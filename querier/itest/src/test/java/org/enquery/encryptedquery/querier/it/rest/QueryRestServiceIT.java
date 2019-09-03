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
package org.enquery.encryptedquery.querier.it.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import org.apache.sshd.common.util.io.IoUtils;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.json.QueryCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QueryResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class QueryRestServiceIT extends BaseRestServiceItest {

	@Override
	@Before
	public void init() throws Exception {
		super.init();

		waitForFileConsumed(new File(responderInboxDir, "phone-book-data-schema.xml"));
		waitForFileConsumed(new File(responderInboxDir, "books-data-schema.xml"));
	}

	@Configuration
	public Option[] configuration() {
		return new Option[] {baseOptions()};
	}

	@Test
	public void listEmpty() throws Exception {
		querySchemaRepo.add(
				sampleData.createJPAQuerySchema(
						dataSchemaRepo.add(sampleData.createDataSchema())));

		retrieveDataSchemas()
				.getData()
				.stream()
				.forEach(ds -> {
					org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse dataSchema =
							retrieveDataSchema(ds.getSelfUri());

					retrieveQuerySchemas(dataSchema)
							.getData()
							.stream()
							.forEach(qs -> {
								QuerySchemaResponse querySchema = retrieveQuerySchema(qs.getSelfUri());
								QueryCollectionResponse queries = retrieveQueries(querySchema);
								assertEquals(0, queries.getData().size());
							});
				});
	}

	@Test
	public void list() throws Exception {
		DataSchema ds = dataSchemaRepo.add(sampleData.createDataSchema());
		QuerySchema qs1 = querySchemaRepo.add(sampleData.createJPAQuerySchema(ds));
		Query stored1 = queryRepo.add(sampleData.createQuery(qs1));

		QueryCollectionResponse queries = retrieveQueriesForQuerySchemaName(ds.getName(), qs1.getName());

		assertNotNull(queries);
		assertNull(queries.getIncluded());
		assertEquals(1, queries.getData().size());
		org.enquery.encryptedquery.querier.data.entity.json.Query listed = queries.getData().iterator().next();
		assertEquals(org.enquery.encryptedquery.querier.data.entity.json.Query.TYPE, listed.getType());
		assertEquals(stored1.getId().toString(), listed.getId());
		assertEquals(stored1.getName(), listed.getName());

		validateUris(listed, qs1, ds);

		QuerySchema qs2 = querySchemaRepo.add(sampleData.createJPAQuerySchema(ds));
		Query stored2 = queryRepo.add(sampleData.createQuery(qs2));
		queries = retrieveQueriesForQuerySchemaName(ds.getName(), qs2.getName());
		assertNull(queries.getIncluded());

		assertEquals(1, queries.getData().size());
		listed = queries.getData().iterator().next();
		assertEquals(org.enquery.encryptedquery.querier.data.entity.json.Query.TYPE, listed.getType());
		assertEquals(stored2.getId().toString(), listed.getId());
		assertEquals(stored2.getName(), listed.getName());
	}


	@Test
	public void encryptWithNativeLib() throws Exception {
		waitForHealthyStatus();
		org.osgi.service.cm.Configuration configuration = configurationAdmin.getConfiguration(//
				"org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme", null);

		Dictionary<String, Object> properties = configuration.getProperties();
		if (properties == null) {
			properties = new Hashtable<>();
		}
		properties.put("paillier.encrypt.query.method", "FastWithJNI");
		configuration.update(properties);
		waitForHealthyStatus();

		QueryResponse queryResponse = createQueryAndWaitForEncryption();
		assertNotNull(queryResponse);
		assertEquals("Encrypted", queryResponse.getData().getStatus().toString());
	}

	protected org.enquery.encryptedquery.querier.data.entity.json.Query createQuery() {
		org.enquery.encryptedquery.querier.data.entity.json.Query q = new org.enquery.encryptedquery.querier.data.entity.json.Query();
		q.setName("Test Query " + ++queryCount);

		q.setParameters(new HashMap<>());
		List<String> selectorValues = new ArrayList<>();
		selectorValues.add("432-567-3945");
		selectorValues.add("534-776-3672");
		q.setSelectorValues(selectorValues);
		q.setFilterExpression("duration > 10");
		return q;
	}

	private QueryResponse createQueryAndWaitForEncryption() throws Exception {
		return createQueryAndWaitForEncryption(createQuery());
	}

	@Test
	public void create() throws Exception {

		QueryResponse created = createQueryAndWaitForEncryption();
		assertNotNull(created);
		validateIncluded(created);

		Integer id = Integer.valueOf(created.getData().getId());
		assertTrue(queryRepo.isGenerated(id));
		Query persisted = queryRepo.find(id);
		assertNotNull(persisted);
		assertNotNull(persisted.getQueryUrl());
		assertNotNull(persisted.getQueryKeyUrl());
		assertEquals(persisted.getFilterExpression(), "duration > 10");


		byte[] bytes = IoUtils.toByteArray(queryRepo.loadQueryBytes(id));
		assertNotNull(bytes);
		assertTrue(bytes.length > 0);

		bytes = IoUtils.toByteArray(queryRepo.loadQueryKeyBytes(id));
		assertNotNull(bytes);
		assertTrue(bytes.length > 0);
	}

	private void validateIncluded(QueryResponse created) {
		assertNotNull(created.getIncluded());
		assertEquals(2, created.getIncluded().size());

		assertEquals(1,
				created.getIncluded()
						.stream()
						.filter(o -> o.get("type").equals(org.enquery.encryptedquery.querier.data.entity.json.QuerySchema.TYPE))
						.count());
		assertEquals(1,
				created.getIncluded()
						.stream()
						.filter(o -> o.get("type").equals(org.enquery.encryptedquery.querier.data.entity.json.DataSchema.TYPE))
						.count());
	}

	private QueryCollectionResponse retrieveQueriesForQuerySchemaName(String dataSchemaName, String querySchemaName) {
		org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse dataSchema = retrieveDataSchemaForName(dataSchemaName);
		return retrieveQueries(retrieveQuerySchemaForName(querySchemaName, dataSchema));
	}

	private void validateUris(org.enquery.encryptedquery.querier.data.entity.json.Query query, QuerySchema qs, DataSchema ds) throws Exception {
		QueryResponse retrieved = retrieveQuery(query.getSelfUri());
		assertEquals(query.getId(), retrieved.getData().getId());
		assertEquals(query.getName(), retrieved.getData().getName());
		validateQuerySchemaUri(retrieved.getData().getQuerySchema().getSelfUri(), qs, ds);
	}

	private void validateQuerySchemaUri(String querySchemaUri, QuerySchema qs, DataSchema ds) throws Exception {
		QuerySchemaResponse retrieved = retrieveQuerySchema(querySchemaUri);
		assertEquals(qs.getName(), retrieved.getData().getName());
		assertEquals(qs.getSelectorField(), retrieved.getData().getSelectorField());
		validateDataSchemaUri(retrieved.getData().getDataSchema().getSelfUri(), ds);
	}

	private void validateDataSchemaUri(String dataSchemaUri, DataSchema ds) throws Exception {
		org.enquery.encryptedquery.querier.data.entity.json.DataSchema retrieved = retrieveDataSchema(dataSchemaUri).getData();
		assertEquals(ds.getName(), retrieved.getName());
	}
}
