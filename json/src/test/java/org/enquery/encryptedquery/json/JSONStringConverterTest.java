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
package org.enquery.encryptedquery.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.utils.ISO8601DateParser;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 *
 */
public class JSONStringConverterTest {

	private static final Logger log = LoggerFactory.getLogger(JSONStringConverterTest.class);
	private String json = "{"
			+ "\"string\":\"string value\","
			+ "\"byte\": \"1\","
			+ "\"booleanTrue\": true,"
			+ "\"booleanFalse\": false,"
			+ "\"booleanNull\": null,"
			+ "\"null\":null,"
			+ "\"int\": 12345,"
			+ "\"double\": 1547215054.479078000,"
			+ "\"ISO8601Date\": \"2001-01-01T19:32:11Z\","
			+ "\"array\":[1, 2, 3, 4, 5],"
			+ "\"object1\":{"
			+ "		\"string\":\"string value\","
			+ "		\"byte\":\"1\","
			+ "		\"booleanTrue\": true,"
			+ "		\"null\":null,"
			+ "		\"int\": 12345,"
			+ "		\"double\": 1547215054.479078000,"
			+ "		\"array\":[1, 2, 3, 4, 5],"
			+ "		\"object2\":{"
			+ "			\"string\":\"string value\","
			+ "			\"byte\":\"1\","
			+ "			\"booleanTrue\": true,"
			+ "			\"null\":null,"
			+ "			\"int\": 12345,"
			+ "			\"double\": 1547215054.479078000,"
			+ "			\"array\":[1, 2, 3, 4, 5]"
			+ "		}"
			+ "}}";


	private List<Integer> expectedArray = Arrays.asList(1, 2, 3, 4, 5);

	@Test
	public void testFlatMap() {

		DataSchema ds = makeSchema();

		JSONStringConverter converter = new JSONStringConverter(ds);
		Map<String, Object> flat = converter.toStringObjectFlatMap(json);
		// Map<String, Object> flat = JSONStringConverter.toStringObjectFlatMap(json);
		log.info("Flattened to: {}", flat);

		validate(flat);
	}

	private DataSchema makeSchema() {
		DataSchema ds = new DataSchema();
		DataSchemaElement element;
		int pos = 0;

		element = new DataSchemaElement();
		element.setDataType(FieldType.STRING);
		element.setName("string");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.BYTE);
		element.setName("byte");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.BOOLEAN);
		element.setName("booleanTrue");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.BOOLEAN);
		element.setName("booleanFalse");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.BOOLEAN);
		element.setName("booleanNull");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.STRING);
		element.setName("null");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.INT);
		element.setName("int");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.DOUBLE);
		element.setName("double");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.ISO8601DATE);
		element.setName("ISO8601Date");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.INT_LIST);
		element.setName("array");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.STRING);
		element.setName("object1|string");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.BYTE);
		element.setName("object1|byte");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.INT);
		element.setName("object1|null");
		element.setPosition(pos++);
		ds.addElement(element);


		element = new DataSchemaElement();
		element.setDataType(FieldType.INT);
		element.setName("object1|int");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.DOUBLE);
		element.setName("object1|double");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.INT_LIST);
		element.setName("object1|array");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.STRING);
		element.setName("object1|object2|string");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.BYTE);
		element.setName("object1|object2|byte");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.STRING);
		element.setName("object1|object2|null");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.INT);
		element.setName("object1|object2|int");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.DOUBLE);
		element.setName("object1|object2|double");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.INT_LIST);
		element.setName("object1|object2|array");
		element.setPosition(pos++);
		ds.addElement(element);



		return ds;
	}

	private void validate(Map<String, Object> flat) {
		assertNotNull(flat);

		Object value = flat.get("string");
		assertTrue("Actual type=" + value.getClass(), value instanceof String);
		assertEquals("string value", value);

		value = flat.get("byte");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Byte);
		assertEquals((byte) 1, value);

		value = flat.get("booleanTrue");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Boolean);
		assertEquals(Boolean.TRUE, value);

		value = flat.get("booleanFalse");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Boolean);
		assertEquals(Boolean.FALSE, value);

		value = flat.get("booleanNull");
		assertNull(value);

		value = flat.get("null");
		assertNull(value);

		value = flat.get("int");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Integer);
		assertEquals(12345, value);

		value = flat.get("double");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Double);
		assertEquals(1547215054.479078000, value);

		value = flat.get("ISO8601Date");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Instant);
		assertEquals(ISO8601DateParser.getInstant("2001-01-01T19:32:11Z"), value);

		value = flat.get("array");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof ArrayList);
		assertTrue(expectedArray.equals(value));

		// second level object
		value = flat.get("object1|string");
		assertTrue("Actual type=" + value.getClass(), value instanceof String);
		assertEquals("string value", value);

		value = flat.get("object1|byte");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Byte);
		assertEquals((byte) 1, value);

		value = flat.get("object1|null");
		assertNull(value);

		value = flat.get("object1|int");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Integer);
		assertEquals(12345, value);

		value = flat.get("object1|double");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Double);
		assertEquals(1547215054.479078000, value);

		value = flat.get("object1|array");
		log.info("type={},value={}", value.getClass().getName(), value);

		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof ArrayList);
		assertTrue("expected:" + expectedArray + ", actual:" + value, expectedArray.equals(value));

		// third level object
		value = flat.get("object1|object2|string");
		assertTrue("Actual type=" + value.getClass(), value instanceof String);
		assertEquals("string value", value);

		value = flat.get("object1|object2|byte");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Byte);
		assertEquals((byte) 1, value);

		value = flat.get("object1|object2|null");
		assertNull(value);

		value = flat.get("object1|object2|int");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Integer);
		assertEquals(12345, value);

		value = flat.get("object1|object2|double");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Double);
		assertEquals(1547215054.479078000, value);

		value = flat.get("object1|object2|array");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof ArrayList);
		assertEquals(expectedArray, value);
	}

	@Test
	public void testDataPrunedInReturnedMap() throws JsonParseException, JsonMappingException, IOException {
		DataSchema ds = new DataSchema();
		DataSchemaElement element;
		int pos = 0;

		element = new DataSchemaElement();
		element.setDataType(FieldType.INT);
		element.setName("int");
		element.setPosition(pos++);
		ds.addElement(element);

		JSONStringConverter converter = new JSONStringConverter(ds);


		String json = "{"
				+ "\"string\":\"string value\","
				+ "\"booleanTrue\":\"true\","
				+ "\"int\": \"12345\","
				+ "\"long\": \"123456\"}";

		Map<String, Object> map = converter.toStringObjectFlatMap(json);
		log.info("returned map: {}", map);
		assertEquals(1, map.size());

		Object val = map.get("int");
		log.info("read '{}' of type '{}'.", val, val.getClass().getName());
		assertTrue(val instanceof Integer);
		assertEquals(Integer.valueOf(12345), val);
	}
}
