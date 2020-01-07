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

import org.apache.commons.codec.Charsets;
import org.apache.commons.io.IOUtils;
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

	private List<Integer> expectedArray = Arrays.asList(1, 2, 3, 4, 5);

	@Test
	public void testFlatMap() {

		DataSchema ds = makeFlatSchema();
		String json = "{"
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

		JSONStringConverter converter = new JSONStringConverter(ds);
		Map<String, Object> flat = converter.toStringObjectFlatMap(json);
		// Map<String, Object> flat = JSONStringConverter.toStringObjectFlatMap(json);
		log.info("Flattened to: {}", flat);

		validate(flat);
	}

	private DataSchema makeFlatSchema() {
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
		assertNotNull(value);
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

	@SuppressWarnings("static-access")
	@Test
	public void testObjectAsString() throws IOException {

		int pos = 0;
		DataSchema ds = new DataSchema();
		DataSchemaElement element = new DataSchemaElement();
		element.setDataType(FieldType.INT);
		element.setName("device");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.ISO8601DATE);
		element.setName("collection_time");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.STRING_LIST);
		element.setName("specimens");
		element.setPosition(pos++);
		ds.addElement(element);

		element = new DataSchemaElement();
		element.setDataType(FieldType.BYTEARRAY);
		element.setName("weather");
		element.setPosition(pos++);
		ds.addElement(element);

		JSONStringConverter converter = new JSONStringConverter(ds);
		String json = IOUtils.resourceToString("/array-of-objects.json", Charsets.UTF_8);
		Map<String, Object> flat = converter.toStringObjectFlatMap(json);
		log.info("Flattened to: {}", flat);
		assertEquals(4, flat.size());

		Object weather = flat.get("weather");
		assertNotNull(weather);
		assertTrue(weather instanceof byte[]);
		byte[] watherBytes = (byte[]) weather;
		Map<String, Object> weatherObject = converter.toStringObjectMap(new String(watherBytes));
		assertEquals(3, weatherObject.size());
		assertEquals("moderate", weatherObject.get("wind"));
		assertEquals("bright", weatherObject.get("sun"));
		assertEquals(Integer.valueOf(68), weatherObject.get("temp"));

		Object specimens = flat.get("specimens");
		assertNotNull(specimens);
		assertTrue(specimens instanceof List);

		@SuppressWarnings("unchecked")
		List<String> specimmenList = (List<String>) specimens;
		assertEquals(3, specimmenList.size());
		Map<String, Object> specimenZero = converter.toStringObjectMap(specimmenList.get(0));
		assertEquals(2, specimenZero.size());
		assertEquals("Bird", specimenZero.get("name"));
		assertEquals(Integer.valueOf(40), specimenZero.get("val"));

		Map<String, Object> specimenOne = converter.toStringObjectMap(specimmenList.get(1));
		assertEquals(2, specimenOne.size());
		assertEquals("northeast", specimenOne.get("region"));
		assertEquals(Integer.valueOf(43), specimenOne.get("level"));

		Map<String, Object> specimenTwo = converter.toStringObjectMap(specimmenList.get(2));
		assertEquals(2, specimenTwo.size());
		assertEquals("street", specimenTwo.get("span"));
		assertEquals("circular", specimenTwo.get("shape"));
	}


	@Test
	public void testPCap() throws IOException {
		DataSchema ds = new DataSchema();
		ds.setName("pcap");
		DataSchemaElement dse1 = new DataSchemaElement();
		dse1.setName("_index");
		dse1.setDataType(FieldType.STRING);
		dse1.setPosition(0);
		ds.addElement(dse1);

		DataSchemaElement dse2 = new DataSchemaElement();
		dse2.setName("_score");
		dse2.setDataType(FieldType.STRING);
		dse2.setPosition(1);
		ds.addElement(dse2);

		DataSchemaElement dse3 = new DataSchemaElement();
		dse3.setName("_source|layers|eth|eth.dst");
		dse3.setDataType(FieldType.STRING);
		dse3.setPosition(2);
		ds.addElement(dse3);

		DataSchemaElement dse4 = new DataSchemaElement();
		dse4.setName("_source|layers|eth|eth.src");
		dse4.setDataType(FieldType.STRING);
		dse4.setPosition(3);
		ds.addElement(dse4);

		DataSchemaElement dse5 = new DataSchemaElement();
		dse5.setName("_source|layers|eth|eth.type");
		dse5.setDataType(FieldType.STRING);
		dse5.setPosition(4);
		ds.addElement(dse5);

		DataSchemaElement dse6 = new DataSchemaElement();
		dse6.setName("_source|layers|frame|frame.protocols");
		dse6.setDataType(FieldType.STRING);
		dse6.setPosition(5);
		ds.addElement(dse6);

		DataSchemaElement dse7 = new DataSchemaElement();
		dse7.setName("_source|layers|frame|frame.time_epoch");
		dse7.setDataType(FieldType.STRING);
		dse7.setPosition(6);
		ds.addElement(dse7);

		DataSchemaElement dse9 = new DataSchemaElement();
		dse9.setName("_source|layers|ip|ip.dst");
		dse9.setDataType(FieldType.STRING);
		dse9.setPosition(7);
		ds.addElement(dse9);

		DataSchemaElement dse10 = new DataSchemaElement();
		dse10.setName("_source|layers|ip|ip.dst_host");
		dse10.setDataType(FieldType.STRING);
		dse10.setPosition(8);
		ds.addElement(dse10);

		DataSchemaElement dse11 = new DataSchemaElement();
		dse11.setName("_source|layers|ip|ip.src");
		dse11.setDataType(FieldType.STRING);
		dse11.setPosition(9);
		ds.addElement(dse11);

		DataSchemaElement dse12 = new DataSchemaElement();
		dse12.setName("_source|layers|ip|ip.src_host");
		dse12.setDataType(FieldType.STRING);
		dse12.setPosition(10);
		ds.addElement(dse12);

		DataSchemaElement dse13 = new DataSchemaElement();
		dse13.setName("_source|layers|tcp|tcp.flags_tree|tcp.flags.syn_tree|_ws.expert|_ws.expert.message");
		dse13.setDataType(FieldType.STRING);
		dse13.setPosition(11);
		ds.addElement(dse13);

		DataSchemaElement dse14 = new DataSchemaElement();
		dse14.setName("_source|layers|tcp|tcp.payload");
		dse14.setDataType(FieldType.STRING);
		dse14.setPosition(12);
		ds.addElement(dse14);

		DataSchemaElement dse15 = new DataSchemaElement();
		dse15.setName("_source|layers|tcp|tcp.srcport");
		dse15.setDataType(FieldType.STRING);
		dse15.setPosition(13);
		ds.addElement(dse15);

		DataSchemaElement dse16 = new DataSchemaElement();
		dse16.setName("_source|layers|tcp|tcp.dstport");
		dse16.setDataType(FieldType.STRING);
		dse16.setPosition(14);
		ds.addElement(dse16);

		DataSchemaElement dse17 = new DataSchemaElement();
		dse17.setName("_type");
		dse17.setDataType(FieldType.STRING);
		dse17.setPosition(15);
		ds.addElement(dse17);

		JSONStringConverter converter = new JSONStringConverter(ds);
		String json = IOUtils.resourceToString("/pcap.json", Charsets.UTF_8);
		Map<String, Object> flat = converter.toStringObjectFlatMap(json);
		log.info("Flattened to: {}", flat);
		assertEquals(14, flat.size());
		assertNull(flat.get("_score"));
		assertEquals("58590", flat.get("_source|layers|tcp|tcp.srcport"));
	}
}
