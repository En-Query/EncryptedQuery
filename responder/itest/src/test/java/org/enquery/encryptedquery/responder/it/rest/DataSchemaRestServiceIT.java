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
package org.enquery.encryptedquery.responder.it.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.karaf.shell.api.console.SessionFactory;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSchemaField;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.it.util.KarafController;
import org.enquery.encryptedquery.xml.schema.DataSchema.Field;
import org.enquery.encryptedquery.xml.schema.DataSchemaResource;
import org.enquery.encryptedquery.xml.schema.DataSchemaResources;
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
public class DataSchemaRestServiceIT extends BaseRestServiceItest {

	@Inject
	private DataSchemaService dataSchemaRegistry;
	@Inject
	private SessionFactory sessionFactory;

	@Before
	@Override
	public void init() throws Exception {
		super.init();
	}

	@Configuration
	public Option[] configuration() {
		return super.baseOptions();
	}

	@Test
	public void importCommand() throws IOException, TimeoutException, InterruptedException {

		DataSchemaResources dataSchemas = retrieveDataSchemas("/responder/api/rest/dataschemas");
		assertEquals(0, dataSchemas.getDataSchemaResource().size());

		Path dsfile = Paths.get("data/books-data-schema.xml");
		IOUtils.copy(
				this.getClass().getClassLoader().getResourceAsStream("/schemas/books-data-schema.xml"),
				Files.newOutputStream(dsfile));

		KarafController kc = new KarafController(sessionFactory);
		String output = kc.executeCommand("dataschema:import -i " + dsfile.toAbsolutePath().toString());
		assertTrue(output.contains("'Books' data schema added"));

		dataSchemas = retrieveDataSchemas("/responder/api/rest/dataschemas");
		assertEquals(1, dataSchemas.getDataSchemaResource().size());

		DataSchemaResource resource = dataSchemas.getDataSchemaResource().get(0);
		assertEquals("Books", resource.getDataSchema().getName());
	}

	@Test
	public void list() throws Exception {

		// First run without any data should return empty
		DataSchemaResources dataSchemas = retrieveDataSchemas("/responder/api/rest/dataschemas");
		assertEquals(0, dataSchemas.getDataSchemaResource().size());

		// Now add one data schema and retest
		DataSchema schema1 = new DataSchema();
		schema1.setName(BOOKS_DATA_SCHEMA_NAME);
		DataSchemaField field1 = new DataSchemaField();
		field1.setFieldName("field1");
		field1.setDataType(FieldType.INT_LIST);
		// field1.setIsArray(true);
		field1.setPosition(33);
		field1.setDataSchema(schema1);
		schema1.getFields().add(field1);
		dataSchemaRegistry.add(schema1);

		DataSchema schema2 = new DataSchema();
		schema2.setName("second schema");

		DataSchemaField field2 = new DataSchemaField();
		field2.setFieldName("field2");
		field2.setDataType(FieldType.STRING);
		field2.setPosition(12);
		field2.setDataSchema(schema2);
		schema2.getFields().add(field2);
		dataSchemaRegistry.add(schema2);


		dataSchemas = retrieveDataSchemas("/responder/api/rest/dataschemas");
		assertEquals(2, dataSchemas.getDataSchemaResource().size());

		// get schema1
		org.enquery.encryptedquery.xml.schema.DataSchemaResource schema = dataSchemas
				.getDataSchemaResource()
				.stream()
				.filter(s -> s.getDataSchema().getName()
						.equals(schema1.getName()))
				.collect(Collectors.toList()).get(0);

		assertNotNull(schema);
		assertEquals(schema1.getName(), schema.getDataSchema().getName());
		assertEquals(1, schema.getDataSchema().getField().size());

		Field xField = schema.getDataSchema().getField().get(0);
		assertEquals(field1.getFieldName(), xField.getName());
		assertEquals(field1.getDataType().getExternalName(), xField.getDataType());
		// assertEquals(field1.getIsArray(), xField.isIsArray());
		assertEquals(field1.getPosition(), Integer.valueOf(xField.getPosition()));

		// find schema 2
		schema = dataSchemas
				.getDataSchemaResource()
				.stream()
				.filter(s -> s.getDataSchema().getName().equals(schema2.getName()))
				.collect(Collectors.toList()).get(0);

		assertNotNull(schema);
		assertEquals(schema2.getName(), schema.getDataSchema().getName());
		assertEquals(1, schema.getDataSchema().getField().size());

		xField = schema.getDataSchema().getField().get(0);
		assertEquals(field2.getFieldName(), xField.getName());
		assertEquals(field2.getDataType().getExternalName(), xField.getDataType());
		// assertEquals(field2.getIsArray(), xField.isIsArray());
		assertEquals(field2.getPosition(), Integer.valueOf(xField.getPosition()));

	}
}
