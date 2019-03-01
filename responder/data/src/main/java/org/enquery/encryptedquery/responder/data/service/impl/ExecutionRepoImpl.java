/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.responder.data.service.impl;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.Collection;

import javax.persistence.EntityManager;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.BlobLocationRegistry;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.responder.data.transformation.URIUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProvider;

@Component
public class ExecutionRepoImpl implements ExecutionRepository {

	private static final String QUERY_FILE_NAME = "query.xml";
	private static final String STD_OUPUT_FILE_NAME = "stdout.log";

	@Reference
	private DataSourceRegistry dataSourceRegistry;

	@Reference
	private BlobLocationRegistry blobLocationRegistry;

	@Reference(target = "(osgi.unit.name=responderPersistenUnit)")
	private JPAEntityManagerProvider provider;
	@Reference
	private TransactionControl txControl;
	private EntityManager em;

	@Activate
	void init() {
		em = provider.getResource(txControl);
	}


	@Override
	public Collection<Execution> list() {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery(
						"Select ex From Execution ex",
						Execution.class).getResultList());
	}

	@Override
	public Execution find(int id) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.find(Execution.class, id));
	}

	@Override
	public Execution add(Execution ex) {
		Validate.notNull(ex);
		return txControl
				.build()
				.required(() -> {
					em.persist(ex);
					return ex;
				});
	}

	@Override
	public Execution update(Execution ex) {
		return txControl
				.build()
				.required(() -> em.merge(ex));
	}

	@Override
	public void delete(int id) {
		txControl
				.build()
				.required(() -> {
					Execution ex = find(id);
					if (ex != null) {
						em.remove(ex);
					}
					return 0;
				});
	}

	@Override
	public Collection<Execution> list(DataSchema dataSchema, DataSource dataSource) {
		Validate.notNull(dataSchema);
		Validate.notNull(dataSource);

		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery("Select ex From Execution ex "
						+ "Where ex.dataSourceName = :dataSourceName "
						+ "And   ex.dataSchema = :dataSchema ",
						Execution.class)
						.setParameter("dataSourceName", dataSource.getName())
						.setParameter("dataSchema", findOrRefresh(dataSchema, em))
						.getResultList());
	}


	private DataSchema findOrRefresh(DataSchema dataSchema, EntityManager em) {
		Validate.notNull(dataSchema);
		DataSchema result = null;
		if (em.contains(dataSchema)) {
			em.refresh(dataSchema);
			result = dataSchema;
		} else {
			result = em.find(DataSchema.class, dataSchema.getId());
			Validate.notNull(result, "Data Schema %s not found.", dataSchema.toString());
		}
		return result;
	}

	@Override
	public Execution findForDataSource(DataSource dataSource, int id) {
		Validate.notNull(dataSource);

		return txControl
				.build()
				.readOnly()
				.supports(() -> {
					DataSource ds = dataSourceRegistry.find(dataSource.getDataSchema(), dataSource.getId());
					Validate.notNull(ds, "Data Source not found:" + dataSource);

					return em.createQuery("Select ex From Execution ex "
							+ "Where ex.id = :id "
							+ "And   ex.dataSourceName = :dataSourceName "
							+ "And   ex.dataSchema = :dataSchema ",
							Execution.class)
							.setParameter("id", id)
							.setParameter("dataSourceName", ds.getName())
							.setParameter("dataSchema", ds.getDataSchema())
							.getResultList()
							.stream()
							.findFirst()
							.orElse(null);
				});
	}

	@Override
	public Execution updateQueryBytes(int executionId, byte[] bytes) throws IOException {
		Validate.notNull(bytes);
		Validate.isTrue(bytes.length > 0L, "Query byte[] is empty.");
		return txControl
				.build()
				.required(() -> {
					Execution execution = find(executionId);
					Validate.notNull(execution);

					URL url = makeUrl(execution, QUERY_FILE_NAME);
					save(bytes, url);

					execution.setQueryLocation(url.toString());
					return update(execution);
				});
	}

	private void save(byte[] bytes, URL url) {
		try {
			try (OutputStream os = openURLOutputStream(url)) {
				IOUtils.write(bytes, os);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error saving Query to URL: " + url, e);
		}
	}

	private void save(URL url, InputStream is) {
		try {
			try (OutputStream os = openURLOutputStream(url)) {
				long bytes = IOUtils.copyLarge(is, os);
				Validate.isTrue(bytes > 0L, "Query stream is empty.");
			}
		} catch (IOException e) {
			throw new RuntimeException("Error saving Query to URL: " + url, e);
		}
	}

	private OutputStream openURLOutputStream(URL url) throws IOException {
		try {
			OutputStream result = null;
			switch (url.toURI().getScheme()) {
				case "file": {
					File file = Paths.get(url.toURI()).toFile();
					file.getParentFile().mkdirs();
					result = new FileOutputStream(file);
					break;
				}
				default: {
					URLConnection c = url.openConnection();
					c.setDoOutput(true);
					result = c.getOutputStream();
				}
			}
			return result;
		} catch (URISyntaxException e) {
			throw new RuntimeException("Error building URI from URL: " + url, e);
		}
	}

	private URL makeUrl(Execution execution, String fileName) {
		Validate.notNull(execution);
		Validate.notNull(execution.getDataSchema());

		Integer dataSchemaId = execution.getDataSchema().getId();

		DataSource dataSource = dataSourceRegistry.find(execution.getDataSourceName());

		try {
			String url = URIUtils.concat(
					blobLocationRegistry.executionUri(dataSchemaId,
							dataSource.getId(),
							execution.getId()),
					fileName).toString();

			return new URL(url);
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error making URL for query: " + execution, e);
		}
	}

	@Override
	public InputStream queryBytes(int executionId) throws IOException {
		Execution execution = find(executionId);
		Validate.notNull(execution, "Execution with id %d not found.", executionId);

		String url = execution.getQueryLocation();
		if (url == null) return null;

		return new URL(url).openStream();
	}

	@Override
	public OutputStream executionOutputOutputStream(int executionId) throws IOException {
		URL url = txControl
				.build()
				.readOnly()
				.supports(() -> {
					Execution execution = find(executionId);
					Validate.notNull(execution, "Execution id %d not found", executionId);
					return makeUrl(execution, STD_OUPUT_FILE_NAME);
				});

		return openURLOutputStream(url);
	}

	@Override
	public InputStream executionOutputInputStream(int executionId) throws IOException {
		URL url = txControl
				.build()
				.readOnly()
				.supports(() -> {
					Execution execution = find(executionId);
					Validate.notNull(execution, "Execution id %d not found", executionId);
					return makeUrl(execution, STD_OUPUT_FILE_NAME);
				});

		return url.openStream();
	}

	@Override
	public Execution add(Execution ex, InputStream inputStream) throws IOException {
		Validate.notNull(ex);
		Validate.notNull(inputStream);

		return txControl
				.build()
				.required(() -> {
					Execution execution = add(ex);
					Validate.notNull(execution);

					URL url = makeUrl(execution, QUERY_FILE_NAME);
					save(url, inputStream);

					execution.setQueryLocation(url.toString());
					return update(execution);
				});

	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.responder.data.service.ExecutionRepository#listIncomplete()
	 */
	@Override
	public Collection<Execution> listIncomplete() {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.createQuery(
						"Select ex From Execution ex Where ex.endTime is null",
						Execution.class).getResultList());

	}
}
