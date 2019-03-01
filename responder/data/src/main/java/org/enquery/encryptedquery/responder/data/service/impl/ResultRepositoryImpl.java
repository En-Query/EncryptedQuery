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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;

import javax.persistence.EntityManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.entity.Result;
import org.enquery.encryptedquery.responder.data.service.BlobLocationRegistry;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;
import org.enquery.encryptedquery.responder.data.transformation.URIUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class ResultRepositoryImpl implements ResultRepository {

	private static final Logger log = LoggerFactory.getLogger(ResultRepositoryImpl.class);

	@Reference
	DataSourceRegistry dsRegistry;

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
	public Result find(int id) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> em.find(Result.class, id));
	}

	@Override
	public Result findForExecution(Execution execution, int id) {
		Validate.notNull(execution);
		return txControl
				.build()
				.readOnly()
				.supports(() -> {
					return em.createQuery("Select r From Result r "
							+ "Where r.id = :id "
							+ "And   r.execution = :execution ",
							Result.class)
							.setParameter("id", id)
							.setParameter("execution", em.find(Execution.class, execution.getId()))
							.getResultList()
							.stream()
							.findFirst()
							.orElse(null);
				});
	}

	@Override
	public Collection<Result> listForExecution(Execution execution) {
		Validate.notNull(execution);
		return txControl
				.build()
				.readOnly()
				.supports(() -> {
					return em.createQuery("Select r From Result r "
							+ "Where r.execution = :execution ",
							Result.class)
							.setParameter("execution", em.find(Execution.class, execution.getId()))
							.getResultList();
				});
	}

	private URL makePayloadFileUrl(Execution ex, Result result) {
		final String uri = makeUrl(ex, result);
		try {
			return URIUtils.concat(uri, makeFileName(result)).toURL();
		} catch (final MalformedURLException e) {
			throw new RuntimeException("Error making result URL.", e);
		}
	}

	private String makeUrl(Execution ex, Result result) {
		final Integer executionId = ex.getId();
		final Integer dataSchemaId = ex.getDataSchema().getId();
		final Integer dataSourceId = dsRegistry.find(ex.getDataSourceName()).getId();
		final String uri = blobLocationRegistry.resultUri(dataSchemaId, dataSourceId, executionId, result.getId());
		return uri;
	}

	private String makeFileName(Result result) {
		return String.format("result-%x.xml", result.getId());
	}

	@Override
	public Result add(Result r) {
		return txControl
				.build()
				.required(() -> {
					em.persist(r);
					return r;
				});
	}

	@Override
	public Result update(Result r) {
		return txControl
				.build()
				.required(() -> em.merge(r));
	}

	@Override
	public void delete(Result r) {
		txControl
				.build()
				.required(() -> {
					Result schedule = find(r.getId());
					if (schedule != null) {
						deletePayload(r);
						em.remove(schedule);
					}
					return 0;
				});
	}

	@Override
	public void deleteAll() {
		txControl
				.build()
				.required(() -> {
					em.createQuery("Select r From Result r", Result.class)
							.getResultList()
							.forEach(r -> em.remove(r));
					return 0;
				});
	}

	private void save(URL url, InputStream source) {
		try {
			switch (url.toURI().getScheme()) {
				case "file": {
					File file = Paths.get(url.toURI()).toFile();
					file.getParentFile().mkdirs();

					try (OutputStream os = new FileOutputStream(file)) {
						IOUtils.copyLarge(source, os);
					}
					break;
				}
				default: {
					URLConnection c = url.openConnection();
					c.setDoOutput(true);
					try (OutputStream os = c.getOutputStream()) {
						IOUtils.copyLarge(source, os);
					}
				}
			}
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException("Error saving data to URL: " + url, e);
		}
	}

	private void deletePayload(Result r) {
		final URI uri = URI.create(makeUrl(r.getExecution(), r));
		switch (uri.getScheme()) {
			case "file": {
				File file = Paths.get(uri).toFile();
				FileUtils.deleteQuietly(file);
				break;
			}
			default: {
				log.warn("Don't know how to delete " + uri);
			}
		}
	}

	@Override
	public InputStream payloadInputStream(int resultId) {
		return txControl
				.build()
				.readOnly()
				.supports(() -> {
					Result result = find(resultId);
					Validate.notNull(result, "Result with id %d was not found.", resultId);
					URI uri = URI.create(result.getPayloadUrl());
					try {
						return uri.toURL().openStream();
					} catch (IOException e) {
						throw new RuntimeException("Error accessing the result payload.", e);
					}
				});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.responder.data.service.ResultRepository#add(org.enquery.
	 * encryptedquery.responder.data.entity.Execution, java.io.InputStream, java.time.Instant,
	 * java.time.Instant)
	 */
	@Override
	public Result add(Execution execution, InputStream inputStream, Instant startTime, Instant endTime) {
		Validate.notNull(execution);
		Validate.notNull(inputStream);
		return txControl
				.build()
				.required(() -> {
					Execution ex = em.find(Execution.class, execution.getId());
					Validate.notNull(ex);

					Result result = new Result();
					result.setCreationTime(new Date());
					if (startTime != null) result.setWindowStartTime(Date.from(startTime));
					if (endTime != null) result.setWindowEndTime(Date.from(endTime));
					result.setExecution(ex);
					em.persist(result);

					final URL url = makePayloadFileUrl(ex, result);
					save(url, inputStream);
					result.setPayloadUrl(url.toString());

					return em.merge(result);
				});
	}

}
