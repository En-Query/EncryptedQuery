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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;

import javax.inject.Inject;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchemaField;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.enquery.encryptedquery.querier.data.service.DataSourceRepository;
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
public class DataSchemaRestServiceIT extends BaseRestServiceItest {

	@Inject
	@Filter(timeout = 60_000)
	private DataSourceRepository dataSourceRepo;
	@Inject
	@Filter(timeout = 60_000)
	private DataSchemaRepository dataSchemaRepo;



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
	public void updatesAfterUsed() throws Exception {

		// Initial fetch should populate Data Schemas from Responder
		validateDataSchemas(retrieveDataSchemas());

		// Create a data source that references one of the data schemas
		DataSchema phoneDataSchema = dataSchemaRepo.findByName("Phone Record");
		log.info("Original: " + phoneDataSchema);
		int originalFieldCount = phoneDataSchema.getFields().size();

		sampleData.createDataSource(phoneDataSchema);

		// emulate scenario where a field is added to our data schema in Responder
		DataSchemaField subjectField = phoneDataSchema.getFields().remove(0);
		dataSchemaRepo.update(phoneDataSchema);

		// validate that the field was actually deleted
		phoneDataSchema = dataSchemaRepo.findByName("Phone Record");
		log.info("After field deleted: " + phoneDataSchema);

		assertEquals(originalFieldCount - 1, phoneDataSchema.getFields().size());
		DataSchemaField field = phoneDataSchema.getFields().stream().filter(
				ds -> ds.getFieldName()
						.equals(subjectField.getFieldName()))
				.findFirst().orElse(null);
		assertNull(field);


		// A new fetch from Responder should recreate the deleted field
		validateDataSchemas(retrieveDataSchemas());

		// field should be re-added after a fetch from Responder
		phoneDataSchema = dataSchemaRepo.findByName("Phone Record");
		log.info("After field deleted restored: " + phoneDataSchema);
		assertEquals(originalFieldCount, phoneDataSchema.getFields().size());

		field = phoneDataSchema.getFields().stream().filter(
				ds -> ds.getFieldName()
						.equals(subjectField.getFieldName()))
				.findFirst().orElse(null);

		assertNotNull(field);

		// now emulate update to field data type is changed
		String originalFieldType = field.getDataType();
		field.setDataType("bad");
		dataSchemaRepo.update(phoneDataSchema);
		phoneDataSchema = dataSchemaRepo.findByName("Phone Record");
		assertEquals(originalFieldCount, phoneDataSchema.getFields().size());

		log.info("After field type update: " + phoneDataSchema);
		field = phoneDataSchema.getFields().stream().filter(
				ds -> ds.getFieldName()
						.equals(subjectField.getFieldName()))
				.findFirst().orElse(null);

		assertNotNull(field);
		assertEquals("bad", field.getDataType());

		// A new fetch from Responder should recreate the deleted field
		validateDataSchemas(retrieveDataSchemas());

		phoneDataSchema = dataSchemaRepo.findByName("Phone Record");
		log.info("After field type restored: " + phoneDataSchema);
		field = phoneDataSchema.getFields().stream().filter(
				ds -> ds.getFieldName()
						.equals(subjectField.getFieldName()))
				.findFirst().orElse(null);
		assertNotNull(field);
		assertEquals(originalFieldType, field.getDataType());


		// Emulate a field was removed in Responder
		// We add a field in Querier DB, which will be deleted during next fetch
		DataSchemaField tobeRemovedField = new DataSchemaField();
		tobeRemovedField.setDataType("float");
		tobeRemovedField.setFieldName("ToBeRemoved");
		tobeRemovedField.setIsArray(false);
		tobeRemovedField.setPosition(199);
		tobeRemovedField.setDataSchema(phoneDataSchema);
		phoneDataSchema.getFields().add(tobeRemovedField);
		dataSchemaRepo.update(phoneDataSchema);

		phoneDataSchema = dataSchemaRepo.findByName("Phone Record");
		log.info("After field added: " + phoneDataSchema);

		assertEquals(originalFieldCount + 1, phoneDataSchema.getFields().size());

		field = phoneDataSchema.getFields().stream().filter(
				ds -> ds.getFieldName()
						.equals(tobeRemovedField.getFieldName()))
				.findFirst().orElse(null);

		assertNotNull(field);

		// A new fetch from Responder should remove the field added in Querier DB
		validateDataSchemas(retrieveDataSchemas());

		phoneDataSchema = dataSchemaRepo.findByName("Phone Record");
		log.info("After added field added is removed: " + phoneDataSchema);
		assertEquals(originalFieldCount, phoneDataSchema.getFields().size());
		field = phoneDataSchema.getFields().stream().filter(
				ds -> ds.getFieldName()
						.equals(tobeRemovedField.getFieldName()))
				.findFirst().orElse(null);
		assertNull(field);
	}

	@Test
	public void unsupportedVersionRejected() throws Exception {
		invokeDataSchemas(406, "application/vnd.encrypedquery.enclave+json; version=111");
		invokeDataSchema("/querier/api/rest/dataschemas/123", 406, "application/vnd.encrypedquery.enclave+json; version=111");
	}

	@Test
	public void list() throws Exception {

		validateDataSchemas(retrieveDataSchemas());

		Collection<DataSchema> list = dataSchemaRepo.list();
		assertNotNull(list);
		assertTrue(list.size() > 0);


		// emulate Responder not reachable
		// it should return the data schemas from the database
		// responderClt.stop();
		int oldPort = responderPort();
		configuteResponderPort(oldPort + 10);
		try {
			validateDataSchemas(retrieveDataSchemas());
		} finally {
			configuteResponderPort(oldPort);
		}

		// emulate Responder http error (404 in this case)
		// since we are providing our own (querier) port, so the
		// service URI is not found (404)
		// it should return the data schemas from the database
		// responderClt.stop();
		configuteResponderPort(8182);
		try {
			validateDataSchemas(retrieveDataSchemas());
		} finally {
			configuteResponderPort(oldPort);
		}
	}

	@Test
	public void retrieveNotFound() throws Exception {
		invokeDataSchema("/querier/api/rest/dataschemas/101", 404);
	}

	private void validateDataSchemas(DataSchemaCollectionResponse schemas) throws UnsupportedEncodingException, InterruptedException {
		assertTrue(schemas.getData().size() > 0);
		assertNull(schemas.getIncluded());

		for (org.enquery.encryptedquery.querier.data.entity.json.DataSchema schema : schemas.getData()) {
			log.info(schema.toString());

			assertEquals(org.enquery.encryptedquery.querier.data.entity.json.DataSchema.TYPE, schema.getType());
			DataSchema persisted = dataSchemaRepo.findByName(schema.getName());
			assertNotNull(persisted);
			String encodedId = URLEncoder.encode(persisted.getId().toString(), "UTF-8");
			String expectedUri = "/querier/api/rest/dataschemas/" + encodedId;

			assertEquals(org.enquery.encryptedquery.querier.data.entity.json.DataSchema.TYPE, schema.getType());
			assertEquals(persisted.getId().toString(), schema.getId());
			assertEquals(persisted.getName(), schema.getName());

			assertEquals(expectedUri, schema.getSelfUri());

			validateEquals(persisted, retrieveDataSchema(schema.getSelfUri()));
		}
	}

	private void validateEquals(DataSchema expected, DataSchemaResponse actual) {
		assertNotNull(actual);
		assertEquals(expected.getName(), actual.getData().getName());
		// assertEquals(expected.getId(), actual.getId());
		assertEquals(expected.getFields().size(), actual.getData().getFields().size());
		for (org.enquery.encryptedquery.querier.data.entity.json.DataSchemaField actualField : actual.getData().getFields()) {
			DataSchemaField expectedField = findFieldByPos(expected, actualField.getPosition());
			validateEquals(expectedField, actualField);
		}
	}

	private void validateEquals(DataSchemaField expectedField, org.enquery.encryptedquery.querier.data.entity.json.DataSchemaField actualField) {
		assertNotNull(expectedField);
		assertNotNull(actualField);
		assertEquals(expectedField.getFieldName(), actualField.getName());
		assertEquals(expectedField.getDataType(), actualField.getDataType());
		assertEquals(expectedField.getIsArray(), actualField.getIsArray());
		assertEquals(expectedField.getPosition(), actualField.getPosition());
	}

	private DataSchemaField findFieldByPos(DataSchema expected, int position) {
		for (DataSchemaField f : expected.getFields()) {
			if (f.getPosition() == position) return f;
		}
		assertTrue("field not found with position " + position, false);
		return null;
	}

}
