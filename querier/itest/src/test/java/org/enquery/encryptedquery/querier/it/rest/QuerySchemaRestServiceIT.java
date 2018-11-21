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

import javax.inject.Inject;

import org.apache.camel.Message;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaResponse;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class QuerySchemaRestServiceIT extends BaseRestServiceItest {

	@Inject
	@Filter(timeout = 60_000)
	private DataSchemaRepository dataSchemaRepo;

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
	public void listEmpty() throws Exception {
		retrieveDataSchemas()
				.getData()
				.stream()
				.map(ds -> retrieveDataSchema(ds.getSelfUri()))
				.forEach(ds -> assertEquals(0, retrieveQuerySchemas(ds).getData().size()));
	}

	@Test
	public void createInvalid() throws Exception {
		Message msg = createInvalidQuerySchema(bookCatalogDataSchema.getQuerySchemasUri(), "invalid");
		assertEquals("", msg.getBody(String.class));
	}


	@Test
	public void create() throws Exception {

		org.enquery.encryptedquery.querier.data.entity.json.QuerySchema querySchema =
				sampleData.createJSONQuerySchema(bookCatalogDataSchema.getId().toString());

		QuerySchemaResponse created = createQuerySchema(bookCatalogDataSchema.getQuerySchemasUri(), querySchema);

		assertNotNull(created);
		assertEquals(querySchema.getName(), created.getData().getName());
		assertEquals(querySchema.getSelectorField(), created.getData().getSelectorField());
		assertNotNull(created.getIncluded());
		assertEquals(1, created.getIncluded().size());
		assertEquals(bookCatalogDataSchema.getId(), created.getIncluded().iterator().next().get("id"));

		QuerySchemaResponse fetched = retrieveQuerySchema(created.getData().getSelfUri());
		assertNotNull(fetched);
		assertEquals(created.getData().getId(), fetched.getData().getId());
		assertEquals(created.getData().getName(), fetched.getData().getName());
		assertEquals(created.getData().getSelectorField(), fetched.getData().getSelectorField());
		assertNotNull(fetched.getIncluded());
		assertEquals(1, fetched.getIncluded().size());
		assertEquals(bookCatalogDataSchema.getId(), fetched.getIncluded().iterator().next().get("id"));

	}

	@Test
	public void list() throws InterruptedException {

		// empty
		QuerySchemaCollectionResponse querySchemas = retrieveQuerySchemas(bookCatalogDataSchema);
		assertNotNull(querySchemas);
		assertEquals(0, querySchemas.getData().size());
		assertNull(querySchemas.getIncluded());

		// create one query schems
		org.enquery.encryptedquery.querier.data.entity.json.QuerySchema querySchema =
				sampleData.createJSONQuerySchema(bookCatalogDataSchema.getId().toString());

		org.enquery.encryptedquery.querier.data.entity.json.QuerySchema created =
				createQuerySchema(bookCatalogDataSchema.getQuerySchemasUri(), querySchema).getData();
		assertNotNull(created);
		assertNotNull(created.getId());

		querySchemas = retrieveQuerySchemas(bookCatalogDataSchema);
		assertNotNull(querySchemas);
		assertEquals(1, querySchemas.getData().size());
		assertNull(querySchemas.getIncluded());

		org.enquery.encryptedquery.querier.data.entity.json.QuerySchema retrievedQuerySchema = querySchemas.getData().iterator().next();
		assertEquals(created.getName(), retrievedQuerySchema.getName());
		assertEquals(created.getId().toString(), retrievedQuerySchema.getId());
		assertNull(retrievedQuerySchema.getFields());
		assertNull(retrievedQuerySchema.getSelectorField());

		// retrieve individual
		QuerySchemaResponse fetched = retrieveQuerySchema(retrievedQuerySchema.getSelfUri());
		assertNotNull(fetched);
		assertEquals(created.getName(), fetched.getData().getName());
		assertEquals(created.getId().toString(), fetched.getData().getId());
		assertNotNull(fetched.getIncluded());
		assertEquals(1, fetched.getIncluded().size());
		assertEquals(bookCatalogDataSchema.getId(), fetched.getIncluded().iterator().next().get("id"));

		// check the data schema URI
		validateDataSchema(bookCatalogDataSchema, fetched.getData().getDataSchema().getSelfUri());

		// add another query schema (in a different Data Schema)
		QuerySchema querySchema2 = sampleData.createJSONQuerySchema(phoneRecordDataSchema.getId().toString());
		fetched = createQuerySchema(phoneRecordDataSchema.getQuerySchemasUri(), querySchema2);
		assertNotNull(fetched);
		assertNotNull(fetched.getData().getId());
		assertEquals(querySchema2.getName(), fetched.getData().getName());
		assertEquals(querySchema2.getSelectorField(), fetched.getData().getSelectorField());
		assertNotNull(fetched.getIncluded());
		assertEquals(1, fetched.getIncluded().size());
		assertEquals(phoneRecordDataSchema.getId(), fetched.getIncluded().iterator().next().get("id"));

		// only second query schema should be returned
		querySchemas = retrieveQuerySchemas(phoneRecordDataSchema);
		assertNotNull(querySchemas);
		assertEquals(1, querySchemas.getData().size());

		retrievedQuerySchema = querySchemas.getData().iterator().next();
		assertEquals(querySchema2.getName(), retrievedQuerySchema.getName());

		// retrieve just the second query schema directly
		fetched = retrieveQuerySchema(retrievedQuerySchema.getSelfUri());
		assertNotNull(fetched);
		validateDataSchema(phoneRecordDataSchema, fetched.getData().getDataSchema().getSelfUri());
	}


	private void validateDataSchema(org.enquery.encryptedquery.querier.data.entity.json.DataSchema ds, String dataSchemaUri) throws InterruptedException {
		org.enquery.encryptedquery.querier.data.entity.json.DataSchema retrievedDataSchema = retrieveDataSchema(dataSchemaUri).getData();
		assertEquals(ds.getName(), retrievedDataSchema.getName());
		assertEquals(ds.getId(), retrievedDataSchema.getId());
	}
}
