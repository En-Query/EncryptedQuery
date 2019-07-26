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
import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.json.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSource;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceResponse;
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
public class DataSourceRestServiceIT extends BaseRestServiceItest {

	private DataSchema bookCatalogDataSchema;
	private DataSchema phoneRecordDataSchema;

	@Override
	@Before
	public void init() throws Exception {
		super.init();

		waitForFileConsumed(new File(responderInboxDir, "phone-book-data-schema.xml"));
		waitForFileConsumed(new File(responderInboxDir, "books-data-schema.xml"));

		DataSchemaCollectionResponse retrievedDataSchemas = retrieveDataSchemas();

		assertNotNull(retrievedDataSchemas);
		assertTrue(retrievedDataSchemas.getData().size() > 0);

		bookCatalogDataSchema = retrieveDataSchema(retrievedDataSchemas
				.getData()
				.stream()
				.filter(r -> "Books".equals(r.getName()))
				.findFirst()
				.get()
				.getSelfUri()).getData();

		assertNotNull(bookCatalogDataSchema);
		phoneRecordDataSchema = retrieveDataSchema(retrievedDataSchemas
				.getData()
				.stream()
				.filter(r -> "Phone Record".equals(r.getName()))
				.findFirst()
				.get()
				.getSelfUri()).getData();

		assertNotNull(phoneRecordDataSchema);
	}

	@Configuration
	public Option[] configuration() {
		return new Option[] {baseOptions()};
	}


	@Test
	public void list() throws Exception {
		// retrieve the book data schemas
		DataSourceCollectionResponse sources = retrieveDataSources(bookCatalogDataSchema);
		validateDataSourceList(sources);

		assertEquals(0, retrieveDataSources(phoneRecordDataSchema).getData().size());

		// emulate Responder not reachable (connection refused)
		// it should return the data sources from the database
		int oldPort = responderPort();
		configureResponderPort(oldPort + 10);
		try {
			validateDataSourceList(retrieveDataSources(bookCatalogDataSchema));
		} finally {
			configureResponderPort(oldPort);
		}

		// emulate Responder http error (404 in this case)
		// since we are providing our own (querier) port, so the
		// service URI is not found (404)
		// it should return the data schemas from the database
		configureResponderPort(8182);
		try {
			validateDataSourceList(retrieveDataSources(bookCatalogDataSchema));
		} finally {
			configureResponderPort(oldPort);
		}
	}

	@Test
	public void unsupportedVersionRejected() throws Exception {
		invokeDataSources(bookCatalogDataSchema, 406, "application/vnd.encrypedquery.enclave+json; version=111");

		String uri = retrieveDataSources(bookCatalogDataSchema).getData().iterator().next().getSelfUri();

		invokeDataSource(uri, 406, "application/vnd.encrypedquery.enclave+json; version=111");
	}


	protected void validateDataSourceList(DataSourceCollectionResponse sources) throws InterruptedException {
		assertEquals(1, sources.getData().size());
		assertNull(sources.getIncluded());

		DataSource dataSource = sources.getData().iterator().next();
		assertEquals("Flink-JDBC-Derby-Books", dataSource.getName());
		assertNull(dataSource.getDescription());
		assertNull(dataSource.getDataSchema());
		assertEquals(DataSource.TYPE, dataSource.getType());


		Collection<org.enquery.encryptedquery.querier.data.entity.jpa.DataSource> list = dataSourceRepo.list();
		assertNotNull(list);
		assertTrue(list.size() > 0);

		DataSourceResponse response = retrieveDataSource(dataSource.getSelfUri());
		DataSource retrieved = response.getData();
		assertNotNull(retrieved);
		assertNotNull(retrieved.getDescription());
		assertNotNull(retrieved.getDataSchema());
		assertNotNull(retrieved.getDataSchema().getId());
		assertNotNull(retrieved.getDataSchema().getSelfUri());
		assertNotNull(response.getIncluded());
		assertEquals(1, response.getIncluded().size());
		assertEquals("DataSchema", response.getIncluded().iterator().next().get("type"));

		assertEquals(dataSource.getName(), retrieved.getName());
		validateDataSchema(bookCatalogDataSchema, retrieved.getDataSchema().getSelfUri());
	}

	protected void validateDataSchema(DataSchema ds, String dataSchemaUri) throws InterruptedException {
		DataSchemaResponse retrievedDataSchema = retrieveDataSchema(dataSchemaUri);
		assertEquals(ds.getId().toString(), retrievedDataSchema.getData().getId());
		assertEquals(ds.getName(), retrievedDataSchema.getData().getName());
		assertEquals(dataSchemaUri, retrievedDataSchema.getData().getSelfUri());
	}
}
