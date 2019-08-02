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
package org.enquery.encryptedquery.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.core.FieldTypeProducerVisitor;
import org.enquery.encryptedquery.utils.PIRException;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class RecordEncodingTest {

	private final Logger log = LoggerFactory.getLogger(RecordEncodingTest.class);

	private DataSchema dSchema;
	private QuerySchema qSchema;
	private QueryInfo queryInfo;
	private RecordEncoding encoder;

	@Before
	public void prepare() {
		dSchema = makeDataSchema();
		qSchema = makeQuerySchema(dSchema);
		queryInfo = makeQueryInfo(qSchema);
		encoder = new RecordEncoding(queryInfo);
		log.info("Query Schema: {}", qSchema);
	}

	@Test
	public void testEmpty() throws PIRException {
		Map<String, Object> rawRecord = new HashMap<>();

		ByteBuffer encodedRecord = encoder.encode(rawRecord);
		assertNotNull(encodedRecord);
		assertTrue(encodedRecord.remaining() > 0);

		DataSchema dataSchema = queryInfo.getQuerySchema().getDataSchema();

		Map<String, Object> decoded = encoder.decode(encodedRecord);

		// we are not properly handling null values yet
		for (QuerySchemaElement qse : queryInfo.getQuerySchema().getElementList()) {
			Object value = decoded.get(qse.getName());
			log.info("{}='{}'", qse.getName(), value);
			assertTrue("Field '" + qse.getName() + "' mismatch.", equals(nullValueOf(dataSchema.elementByName(qse.getName())), value));
		}
	}

	/**
	 * @param nullValueOf
	 * @param value
	 * @return
	 */
	private boolean equals(Object value1, Object value2) {
		if (value1 == null && value2 == null) return true;
		if (value1 instanceof byte[]) {
			return Arrays.equals((byte[]) value1, (byte[]) value2);
		}
		return Objects.equals(value1, value2);
	}

	@Test
	public void testIntList() throws PIRException {
		final String fieldName = "intListField";

		Map<String, Object> rawRecord = new HashMap<>();
		List<Integer> values = Arrays.asList(1, 2, 3);
		rawRecord.put(fieldName, values);

		List<Integer> expectedValues = Arrays.asList(1, 2, 3);

		QuerySchemaElement qsIntArrayField = qSchema.getElement(fieldName);
		Validate.notNull(qsIntArrayField);

		ByteBuffer encodedRecord = encoder.encode(rawRecord);
		assertNotNull(encodedRecord);
		assertTrue(encodedRecord.remaining() > 0);

		Map<String, Object> decoded = encoder.decode(encodedRecord);

		assertEquals(expectedValues, decoded.get(fieldName));

		// Now change Query Schema to return all array elements
		qsIntArrayField.setMaxArrayElements(2);
		encodedRecord = encoder.encode(rawRecord);
		decoded = encoder.decode(encodedRecord);

		expectedValues = Arrays.asList(1, 2);
		assertEquals(expectedValues, decoded.get(fieldName));
	}


	@Test
	public void testStringList() throws PIRException {
		String fieldName = "stringListField";

		Map<String, Object> rawRecord = new HashMap<>();
		List<String> values = Arrays.asList("100", "200", "300");
		rawRecord.put(fieldName, values);

		List<String> expectedValues = Arrays.asList("100", "200", "300");

		QuerySchemaElement qsField = qSchema.getElement(fieldName);
		Validate.notNull(qsField);

		ByteBuffer encodedRecord = encoder.encode(rawRecord);
		assertNotNull(encodedRecord);
		assertTrue(encodedRecord.remaining() > 0);

		Map<String, Object> decoded = encoder.decode(encodedRecord);

		assertEquals(expectedValues, decoded.get(fieldName));

		// Now change Query Schema to return all array elements
		qsField.setMaxArrayElements(2);
		encodedRecord = encoder.encode(rawRecord);
		decoded = encoder.decode(encodedRecord);

		expectedValues = Arrays.asList("100", "200");
		assertEquals(expectedValues, decoded.get(fieldName));

		// Now change Query Schema to return only 1 character from each string
		qsField.setSize(1);
		encodedRecord = encoder.encode(rawRecord);
		decoded = encoder.decode(encodedRecord);

		expectedValues = Arrays.asList("1", "2");
		assertEquals(expectedValues, decoded.get(fieldName));
	}

	@Test
	public void testSingleString() throws PIRException {
		String fieldName = "stringField";
		final QuerySchemaElement qsField = qSchema.getElement(fieldName);
		Validate.notNull(qsField);

		// first test a single value
		Map<String, Object> rawRecord = new HashMap<>();
		String scalarValue = "100";
		rawRecord.put(fieldName, scalarValue);

		ByteBuffer encodedRecord = encoder.encode(rawRecord);
		Map<String, Object> decoded = encoder.decode(encodedRecord);

		String actual = (String) decoded.get(fieldName);
		assertEquals(scalarValue, actual);
	}

	@Test
	public void testByteArray() throws PIRException {
		String fieldName = "varArrayField";
		final QuerySchemaElement qsField = qSchema.getElement(fieldName);
		Validate.notNull(qsField);
		qsField.setSize(null);

		Map<String, Object> rawRecord = new HashMap<>();
		final byte[] scalarValue = new byte[] {0x1, 0x2, 0x3};
		rawRecord.put(fieldName, scalarValue);

		ByteBuffer encodedRecord = encoder.encode(rawRecord);
		Map<String, Object> decoded = encoder.decode(encodedRecord);

		byte[] actual = (byte[]) decoded.get(fieldName);
		log.info("actual={}", actual);
		assertArrayEquals(scalarValue, actual);

		qsField.setSize(2);
		byte[] expected = new byte[] {0x1, 0x2};
		encodedRecord = encoder.encode(rawRecord);
		decoded = encoder.decode(encodedRecord);

		actual = (byte[]) decoded.get(fieldName);
		log.info("actual={}", actual);
		assertArrayEquals(expected, actual);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testByteArrayList() throws PIRException {
		String fieldName = "varArrayListField";
		final QuerySchemaElement qsField = qSchema.getElement(fieldName);
		Validate.notNull(qsField);

		List<byte[]> listValue = Arrays.asList(
				new byte[] {0x1, 0x2, 0x3},
				new byte[] {0x4, 0x5, 0x6},
				new byte[] {0x7, 0x8, 0x9});

		List<byte[]> expectedListResult = Arrays.asList(
				new byte[] {0x1, 0x2},
				new byte[] {0x4, 0x5},
				new byte[] {0x7, 0x8});

		Map<String, Object> rawRecord = new HashMap<>();
		rawRecord.put(fieldName, listValue);

		ByteBuffer encodedRecord = encoder.encode(rawRecord);
		Map<String, Object> decoded = encoder.decode(encodedRecord);

		List<byte[]> actualList = (List<byte[]>) decoded.get(fieldName);
		assertEquals(3, actualList.size());
		byte[] actual;
		for (int i = 0; i < actualList.size(); ++i) {
			actual = actualList.get(i);
			log.info("actual={}", actual);
			assertArrayEquals(expectedListResult.get(i), actual);
		}
		qsField.setMaxArrayElements(2);
		encodedRecord = encoder.encode(rawRecord);
		decoded = encoder.decode(encodedRecord);

		actualList = (List<byte[]>) decoded.get(fieldName);
		assertEquals(2, actualList.size());
		for (int i = 0; i < actualList.size(); ++i) {
			actual = actualList.get(i);
			log.info("actual={}", actual);
			assertArrayEquals(expectedListResult.get(i), actual);
		}
	}

	@Test
	public void testSingleBoolean() throws PIRException {
		String fieldName = "booleanField";
		final QuerySchemaElement qsField = qSchema.getElement(fieldName);
		Validate.notNull(qsField);

		Map<String, Object> rawRecord = new HashMap<>();

		// test true
		Boolean scalarValue = true;
		rawRecord.put(fieldName, scalarValue);

		ByteBuffer encodedRecord = encoder.encode(rawRecord);
		Map<String, Object> decoded = encoder.decode(encodedRecord);

		Boolean actual = (Boolean) decoded.get(fieldName);
		assertEquals(scalarValue, actual);

		// test false
		scalarValue = false;
		rawRecord.put(fieldName, scalarValue);

		encodedRecord = encoder.encode(rawRecord);
		decoded = encoder.decode(encodedRecord);

		actual = (Boolean) decoded.get(fieldName);
		assertEquals(scalarValue, actual);

		// test null
		scalarValue = null;
		rawRecord.put(fieldName, scalarValue);

		encodedRecord = encoder.encode(rawRecord);
		decoded = encoder.decode(encodedRecord);

		actual = (Boolean) decoded.get(fieldName);
		assertEquals(scalarValue, actual);
	}

	@Test
	public void testBooleanList() throws PIRException {
		String fieldName = "booleanListField";

		Map<String, Object> rawRecord = new HashMap<>();
		List<Boolean> values = Arrays.asList(true, false, null);
		rawRecord.put(fieldName, values);

		List<Boolean> expectedValues = Arrays.asList(true, false, null);

		QuerySchemaElement qsField = qSchema.getElement(fieldName);
		Validate.notNull(qsField);

		ByteBuffer encodedRecord = encoder.encode(rawRecord);
		assertNotNull(encodedRecord);
		assertTrue(encodedRecord.remaining() > 0);

		Map<String, Object> decoded = encoder.decode(encodedRecord);

		assertEquals(expectedValues, decoded.get(fieldName));

		// Now change Query Schema to return 2 array elements
		qsField.setMaxArrayElements(2);
		encodedRecord = encoder.encode(rawRecord);
		decoded = encoder.decode(encodedRecord);

		expectedValues = Arrays.asList(true, false);
		assertEquals(expectedValues, decoded.get(fieldName));

		// Now change Query Schema to return 1 array elements
		qsField.setMaxArrayElements(1);
		encodedRecord = encoder.encode(rawRecord);
		decoded = encoder.decode(encodedRecord);

		expectedValues = Arrays.asList(true);
		assertEquals(expectedValues, decoded.get(fieldName));
	}


	/**
	 * @param elementByName
	 * @param value
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Object nullValueOf(DataSchemaElement dataElement) {
		// if (dataElement.getIsArray()) {
		// return Collections.emptyList();
		// }

		FieldTypeProducerVisitor visitor = new FieldTypeProducerVisitor() {

			@Override
			public Byte visitByte() {
				return Byte.valueOf((byte) 0);
			}

			@Override
			public List<Byte> visitByteList() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public String visitISO8601Date() {
				return "";
			}

			@Override
			public List<String> visitISO8601DateList() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public String visitIP6() {
				return "0:0:0:0:0:0:0:1";
			}

			@Override
			public List<String> visitIP6List() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public String visitIP4() {
				return "127.0.0.1";
			}

			@Override
			public List<String> visitIP4List() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public byte[] visitByteArray() {
				return new byte[0];
			}

			@Override
			public List<byte[]> visitByteArrayList() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public Character visitChar() {
				return Character.valueOf((char) 0);
			}

			@Override
			public List<Character> visitCharList() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public Double visitDouble() {
				return Double.valueOf(0);
			}

			@Override
			public List<Double> visitDoubleList() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public Float visitFloat() {
				return Float.valueOf(0);
			}

			@Override
			public List<Float> visitFloatList() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public Long visitLong() {
				return Long.valueOf(0);
			}

			@Override
			public List<Long> visitLongList() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public Short visitShort() {
				return Short.valueOf((short) 0);
			}

			@Override
			public List<Short> visitShortList() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public Integer visitInt() {
				return Integer.valueOf(0);
			}

			@Override
			public List<Integer> visitIntList() {
				return Collections.EMPTY_LIST;
			}

			@Override
			public String visitString() {
				return "";
			}

			@Override
			public List<String> visitStringList() {
				return Collections.EMPTY_LIST;
			}
			
			@Override
			public Boolean visitBoolean() {
				return null;
			}

			@Override
			public List<Boolean> visitBooleanList() {
				return Collections.EMPTY_LIST;
			}
		};

		return dataElement.getDataType().convert(visitor);
		/*--switch (dataElement.getDataType()) {
			case BYTE:
				result = Byte.valueOf((byte) 0);
				break;
			case SHORT: {
				result = Short.valueOf((short) 0);
				break;
			}
			case INT: {
				result = Integer.valueOf(0);
				break;
			}
			case LONG: {
				result = Long.valueOf(0);
				break;
			}
			case FLOAT: {
				result = Float.valueOf(0);
				break;
			}
			case DOUBLE: {
				result = Double.valueOf(0);
				break;
			}
			case CHAR: {
				result = Character.valueOf((char) 0);
				break;
			}
			case STRING: {
				result = "";
				break;
			}
			case BYTEARRAY: {
				result = new byte[0];
				break;
			}
			case IP4:
				result = "127.0.0.1";
				break;
			case IP6:
				result = "0:0:0:0:0:0:0:1";
				break;
			case ISO8601DATE:
				result = "";
				break;
			default:
				throw new RuntimeException("dataType = '" + dataElement.getDataType() + "' not recognized!");
		}
		return result;*/
	}

	private QuerySchema makeQuerySchema(DataSchema dSchema) {

		QuerySchema qSchema = new QuerySchema();
		qSchema.setName("TestQuerySchema");
		qSchema.setDataSchema(dSchema);
		List<QuerySchemaElement> fields = qSchema.getElementList();

		QuerySchemaElement field = new QuerySchemaElement();
		field.setName("intField");
		fields.add(field);
		qSchema.setSelectorField(field.getName());

		field = new QuerySchemaElement();
		field.setName("intListField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("byteField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("byteListField");
		fields.add(field);
		
		field = new QuerySchemaElement();
		field.setName("booleanField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("booleanListField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("longField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("longListField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("shortField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("shortListField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("stringField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("stringListField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("doubleField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("doubleListField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("floatField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("floatListField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("ipv4Field");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("ipv4ListField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("ipv6Field");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("ipv6ListField");
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("varArrayField");
		// limit number of bytes
		field.setSize(2);
		fields.add(field);

		field = new QuerySchemaElement();
		field.setName("varArrayListField");
		// limit number of bytes
		field.setSize(2);
		fields.add(field);

		qSchema.validate();
		return qSchema;
	}

	private DataSchema makeDataSchema() {
		int pos = 0;

		List<DataSchemaElement> fields = new ArrayList<>();

		DataSchemaElement field = new DataSchemaElement();
		field.setName("intField");
		field.setDataType(FieldType.INT);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("intListField");
		field.setDataType(FieldType.INT_LIST);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("byteField");
		field.setDataType(FieldType.BYTE);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("byteListField");
		field.setDataType(FieldType.BYTE_LIST);
		field.setPosition(pos++);
		fields.add(field);
		
		field = new DataSchemaElement();
		field.setName("booleanField");
		field.setDataType(FieldType.BOOLEAN);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("booleanListField");
		field.setDataType(FieldType.BOOLEAN_LIST);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("longField");
		field.setDataType(FieldType.LONG);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("longListField");
		field.setDataType(FieldType.LONG_LIST);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("shortField");
		field.setDataType(FieldType.SHORT);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("shortListField");
		field.setDataType(FieldType.SHORT_LIST);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("stringField");
		field.setDataType(FieldType.STRING);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("stringListField");
		field.setDataType(FieldType.STRING_LIST);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("doubleField");
		field.setDataType(FieldType.DOUBLE);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("doubleListField");
		field.setDataType(FieldType.DOUBLE_LIST);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("floatField");
		field.setDataType(FieldType.FLOAT);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("floatListField");
		field.setDataType(FieldType.FLOAT_LIST);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("ipv4Field");
		field.setDataType(FieldType.IP4);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("ipv4ListField");
		field.setDataType(FieldType.IP4);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("ipv6Field");
		field.setDataType(FieldType.IP6);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("ipv6ListField");
		field.setDataType(FieldType.IP6);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("charField");
		field.setDataType(FieldType.CHAR);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("charListField");
		field.setDataType(FieldType.CHAR);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("varArrayField");
		field.setDataType(FieldType.BYTEARRAY);
		field.setPosition(pos++);
		fields.add(field);

		field = new DataSchemaElement();
		field.setName("varArrayListField");
		field.setDataType(FieldType.BYTEARRAY_LIST);
		field.setPosition(pos++);
		fields.add(field);

		DataSchema dSchema = new DataSchema();
		dSchema.setName("TestDataSchema");
		for (DataSchemaElement f : fields) {
			dSchema.addElement(f);
		}

		return dSchema;
	}

	private QueryInfo makeQueryInfo(QuerySchema qSchema) {
		QueryInfo queryInfo = new QueryInfo();
		queryInfo.setQuerySchema(qSchema);
		return queryInfo;
	}
}
