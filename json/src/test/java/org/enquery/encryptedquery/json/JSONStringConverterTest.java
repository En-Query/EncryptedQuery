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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class JSONStringConverterTest {

	private static final Logger log = LoggerFactory.getLogger(JSONStringConverterTest.class);
	private String json = "{"
			+ "\"string\":\"string value\","
			+ "\"boolean\":true,"
			+ "\"null\":null,"
			+ "\"int\": 12345,"
			+ "\"double\": 1547215054.479078000,"
			+ "\"array\":[1, 2, 3, 4, 5],"
			+ "\"object\":{"
			+ "		\"string\":\"string value\","
			+ "		\"boolean\":true,"
			+ "		\"null\":null,"
			+ "		\"int\": 12345,"
			+ "		\"double\": 1547215054.479078000,"
			+ "		\"array\":[1, 2, 3, 4, 5],"
			+ "		\"object\":{"
			+ "			\"string\":\"string value\","
			+ "			\"boolean\":true,"
			+ "			\"null\":null,"
			+ "			\"int\": 12345,"
			+ "			\"double\": 1547215054.479078000,"
			+ "			\"array\":[1, 2, 3, 4, 5]"
			+ "		}"
			+ "}}";


	private List<Integer> expectedArray = Arrays.asList(1, 2, 3, 4, 5);

	@Test
	public void testFlatMap() {

		Map<String, Object> flat = JSONStringConverter.toStringObjectFlatMap(json);
		log.info("Flattened to: {}", flat);

		validate(flat);
	}

	private void validate(Map<String, Object> flat) {
		assertNotNull(flat);

		Object value = flat.get("string");
		assertTrue("Actual type=" + value.getClass(), value instanceof String);
		assertEquals("string value", value);

		value = flat.get("boolean");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Boolean);
		assertEquals(Boolean.TRUE, value);

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

		value = flat.get("array");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof ArrayList);
		assertEquals(expectedArray, value);

		// second level object
		value = flat.get("object|string");
		assertTrue("Actual type=" + value.getClass(), value instanceof String);
		assertEquals("string value", value);

		value = flat.get("object|boolean");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Boolean);
		assertEquals(Boolean.TRUE, value);

		value = flat.get("object|null");
		assertNull(value);

		value = flat.get("object|int");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Integer);
		assertEquals(12345, value);

		value = flat.get("object|double");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Double);
		assertEquals(1547215054.479078000, value);

		value = flat.get("object|array");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof ArrayList);
		assertEquals(expectedArray, value);

		// third level object
		value = flat.get("object|object|string");
		assertTrue("Actual type=" + value.getClass(), value instanceof String);
		assertEquals("string value", value);

		value = flat.get("object|object|boolean");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Boolean);
		assertEquals(Boolean.TRUE, value);

		value = flat.get("object|object|null");
		assertNull(value);

		value = flat.get("object|object|int");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Integer);
		assertEquals(12345, value);

		value = flat.get("object|object|double");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof Double);
		assertEquals(1547215054.479078000, value);

		value = flat.get("object|object|array");
		assertNotNull(value);
		assertTrue("Actual type=" + value.getClass(), value instanceof ArrayList);
		assertEquals(expectedArray, value);
	}
}
