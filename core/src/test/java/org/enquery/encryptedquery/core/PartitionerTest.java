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
package org.enquery.encryptedquery.core;

import static org.junit.Assert.assertEquals;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.QuerySchemaElement;
import org.enquery.encryptedquery.utils.PIRException;
import org.junit.Before;
import org.junit.Test;

public class PartitionerTest {

	// private final Logger log = LoggerFactory.getLogger(PartitionerTest.class);

	private Partitioner partitioner;
	private DataSchema dSchema;
	private DataSchemaElement dseOne;
	private DataSchemaElement dse2;
	private DataSchemaElement dse3;
	private DataSchemaElement dse4;
	private DataSchemaElement dse5;
	private DataSchemaElement dse6;
	private DataSchemaElement dse7;
	private DataSchemaElement dse8;
	private DataSchemaElement dse9;
	private DataSchemaElement dse10;
	private DataSchemaElement dse11;

	private QuerySchema qSchema;
	private QuerySchemaElement qseOne;
	private QuerySchemaElement qse2;
	private QuerySchemaElement qse3;
	private QuerySchemaElement qse4;
	private QuerySchemaElement qse5;
	private QuerySchemaElement qse6;
	private QuerySchemaElement qse7;
	private QuerySchemaElement qse8;
	private QuerySchemaElement qse9;
	private QuerySchemaElement qse10;
	private QuerySchemaElement qse11;

	@Before
	public void prepare() {
		dseOne = new DataSchemaElement();
		dseOne.setName("intField");
		dseOne.setDataType("int");
		dseOne.setIsArray(false);
		dseOne.setPosition(0);

		dse2 = new DataSchemaElement();
		dse2.setName("longField");
		dse2.setDataType("long");
		dse2.setIsArray(false);
		dse2.setPosition(1);

		dse3 = new DataSchemaElement();
		dse3.setName("shortField");
		dse3.setDataType("short");
		dse3.setIsArray(false);
		dse3.setPosition(2);

		dse4 = new DataSchemaElement();
		dse4.setName("fixedStringField");
		dse4.setDataType("string");
		dse4.setIsArray(false);
		dse4.setPosition(3);

		dse5 = new DataSchemaElement();
		dse5.setName("variableStringField");
		dse5.setDataType("string");
		dse5.setIsArray(false);
		dse5.setPosition(4);

		dse6 = new DataSchemaElement();
		dse6.setName("doubleField");
		dse6.setDataType("double");
		dse6.setIsArray(false);
		dse6.setPosition(5);

		dse7 = new DataSchemaElement();
		dse7.setName("variableArrayStringField");
		dse7.setDataType("string");
		dse7.setIsArray(false);
		dse7.setPosition(6);

		dse8 = new DataSchemaElement();
		dse8.setName("largeVariableStringField");
		dse8.setDataType("string");
		dse8.setIsArray(false);
		dse8.setPosition(7);

		dse9 = new DataSchemaElement();
		dse9.setName("floatField");
		dse9.setDataType(FieldTypes.FLOAT);
		dse9.setIsArray(false);
		dse9.setPosition(5);

		dse10 = new DataSchemaElement();
		dse10.setName("ipv4Field");
		dse10.setDataType(FieldTypes.IP4);
		dse10.setIsArray(false);
		dse10.setPosition(10);

		dse11 = new DataSchemaElement();
		dse11.setName("ipv6Field");
		dse11.setDataType(FieldTypes.IP6);
		dse11.setIsArray(false);
		dse11.setPosition(11);

		qseOne = new QuerySchemaElement();
		qseOne.setName("intField");
		qseOne.setLengthType("fixed");
		qseOne.setSize(4);
		qseOne.setMaxArrayElements(1);

		qse2 = new QuerySchemaElement();
		qse2.setName("longField");
		qse2.setLengthType("fixed");
		qse2.setSize(8);
		qse2.setMaxArrayElements(1);

		qse3 = new QuerySchemaElement();
		qse3.setName("shortField");
		qse3.setLengthType("fixed");
		qse3.setSize(2);
		qse3.setMaxArrayElements(1);

		qse4 = new QuerySchemaElement();
		qse4.setName("fixedStringField");
		qse4.setLengthType("fixed");
		qse4.setSize(16);
		qse4.setMaxArrayElements(1);

		qse5 = new QuerySchemaElement();
		qse5.setName("variableStringField");
		qse5.setLengthType("variable");
		qse5.setSize(128);
		qse5.setMaxArrayElements(1);

		qse6 = new QuerySchemaElement();
		qse6.setName("doubleField");
		qse6.setLengthType("fixed");
		qse6.setSize(8);
		qse6.setMaxArrayElements(1);

		qse7 = new QuerySchemaElement();
		qse7.setName("variableArrayStringField");
		qse7.setLengthType("variable");
		qse7.setSize(15);
		qse7.setMaxArrayElements(3);

		qse8 = new QuerySchemaElement();
		qse8.setName("largeVariableStringField");
		qse8.setLengthType("variable");
		qse8.setSize(54000);
		qse8.setMaxArrayElements(1);

		qse9 = new QuerySchemaElement();
		qse9.setName("floatField");
		qse9.setLengthType("fixed");
		qse9.setSize(4);
		qse9.setMaxArrayElements(1);

		qse10 = new QuerySchemaElement();
		qse10.setName("ipv4Field");
		qse10.setLengthType("fixed");
		qse10.setSize(4);
		qse10.setMaxArrayElements(1);

		qse11 = new QuerySchemaElement();
		qse11.setName("ipv6Field");
		qse11.setLengthType("fixed");
		qse11.setSize(8);
		qse11.setMaxArrayElements(1);

		dSchema = new DataSchema();
		dSchema.setName("TestDataSchema");
		dSchema.addElement(dseOne);
		dSchema.addElement(dse2);
		dSchema.addElement(dse3);
		dSchema.addElement(dse4);
		dSchema.addElement(dse5);
		dSchema.addElement(dse6);
		dSchema.addElement(dse7);
		dSchema.addElement(dse8);
		dSchema.addElement(dse9);
		dSchema.addElement(dse10);
		dSchema.addElement(dse11);

		qSchema = new QuerySchema();
		qSchema.setName("TestQuerySchema");
		qSchema.setDataSchema(dSchema);
		qSchema.setSelectorField("intField");
		qSchema.getElementList().add(qseOne);
		qSchema.getElementList().add(qse2);
		qSchema.getElementList().add(qse3);
		qSchema.getElementList().add(qse4);
		qSchema.getElementList().add(qse5);
		qSchema.getElementList().add(qse6);
		qSchema.getElementList().add(qse7);
		qSchema.getElementList().add(qse8);
		qSchema.getElementList().add(qse9);
		qSchema.getElementList().add(qse10);
		qSchema.getElementList().add(qse11);

		// partitioner = new Partitioner(128, dSchema);
		partitioner = new Partitioner();
	}

	private static void listPartitions(List<byte[]> partitions) {
		System.out.println("Record in Parts:");
		int counter = 0;
		for (byte[] ba : partitions) {
			System.out.print("Partition (" + counter++ + ") Value (");
			for (Byte b : ba) {
				System.out.print(String.format("%02X", b.byteValue()));
			}
			System.out.println(")");
		}
	}

	@Test
	public void ipv4ToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		System.out.println("Test ipv4 to Bytes");
		List<Byte> parts = new ArrayList<>();
		String testValue = "192.169.23.128";
		System.out.println("Original IP4 Value " + testValue);
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse10.getName()).getDataType(), qse10);
		// partitioner.toPartitionsByte(parts, testValue, qseOne);
		System.out.println("Byte size " + parts.size());
		assertEquals("byte array size should be 4", 4, parts.size());
		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "ip4", qse10);
		System.out.println("Return Part " + returnPart.toString());
		assertEquals(testValue, returnPart);
	}

	@Test
	public void ipv6ToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		System.out.println("Test ipv6 to Bytes");
		List<Byte> parts = new ArrayList<>();
		String testValue = "2001:0000:3238:DFE1:63:0000:0000:FEFB";
		InetAddress endValue = null;
		InetAddress startValue = null;
		try {
			startValue = InetAddress.getByName(testValue);
			System.out.println("Original IP6 Value " + startValue.getHostAddress());
			parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse11.getName()).getDataType(), qse11);
			// partitioner.toPartitionsByte(parts, testValue, qseOne);
			System.out.println("Byte size " + parts.size());
			assertEquals("byte array size should be 16", 16, parts.size());
			Object returnPart = null;
			returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "ip6", qse11);
			System.out.println("Return Part " + returnPart.toString());
			endValue = InetAddress.getByName(returnPart.toString());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		assertEquals(startValue, endValue);
	}

	@Test
	public void intToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		System.out.println("Test Integer to Bytes");
		List<Byte> parts = new ArrayList<>();
		int testValue = 123456;
		System.out.println("Original Integer Value " + testValue);
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qseOne.getName()).getDataType(), qseOne);
		// partitioner.toPartitionsByte(parts, testValue, qseOne);
		System.out.println("Byte size " + parts.size());
		// byte[] testParts = partitioner.asByteArray(testValue, "int");
		assertEquals("byte array size should be 4", 4, parts.size());
		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "int", qseOne);
		System.out.println("Return Part " + returnPart.toString());
		assertEquals(testValue, returnPart);
	}

	@Test
	public void doubleToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		System.out.println("Test Double to Bytes");
		List<Byte> parts = new ArrayList<>();
		double testValue = 44.44;
		System.out.println("Original double Value " + testValue);
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse6.getName()).getDataType(), qse6);
		// partitioner.toPartitionsByte(parts, testValue, qseOne);
		System.out.println("Byte size " + parts.size());
		// byte[] testParts = partitioner.asByteArray(testValue, "double");
		assertEquals("byte array size should be 8", 8, parts.size());
		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "double", qse6);
		System.out.println("Return Part " + returnPart.toString());
		assertEquals(testValue, returnPart);
	}

	@Test
	public void floutToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		System.out.println("Test Float to Bytes");
		List<Byte> parts = new ArrayList<>();
		float testValue = 44.44f;
		System.out.println("Original float Value " + testValue);
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse9.getName()).getDataType(), qse9);
		// partitioner.toPartitionsByte(parts, testValue, qseOne);
		System.out.println("Byte size " + parts.size());
		// byte[] testParts = partitioner.asByteArray(testValue, FieldTypes.FLOAT);
		assertEquals("byte array size should be 4", 4, parts.size());
		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, FieldTypes.FLOAT, qse9);
		System.out.println("Return Part " + returnPart.toString());
		assertEquals(testValue, returnPart);
	}

	@Test
	public void longToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		System.out.println("Test Long to Bytes");
		List<Byte> parts = new ArrayList<>();
		long testValue = 657456788;
		System.out.println("Original Long Value " + testValue);
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse2.getName()).getDataType(), qse2);
		System.out.println("Byte size " + parts.size());
		// byte[] testParts = partitioner.asByteArray(testValue, "long");
		assertEquals("byte array size should be 8", 8, parts.size());
		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "long", qse2);
		System.out.println("Return Part " + returnPart.toString());
		assertEquals(testValue, returnPart);
	}

	@Test
	public void shortToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		System.out.println("Test short to Bytes");
		List<Byte> parts = new ArrayList<>();
		short testValue = -31000;
		System.out.println("Original short Value " + testValue);
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse3.getName()).getDataType(), qse3);
		System.out.println("Byte size " + parts.size());
		// byte[] testParts = partitioner.asByteArray(testValue, "short");
		assertEquals("byte array size should be 2", 2, parts.size());
		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "short", qse3);
		System.out.println("Return Part " + returnPart.toString());
		assertEquals(testValue, returnPart);
	}

	@Test
	public void fixedStringToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		System.out.println("Test Fixed Length (" + qse4.getSize() + ") String which is longer");
		List<Byte> parts = new ArrayList<>();
		String testValue = "This is a conversion test for a fixed string length.";
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse4.getName()).getDataType(), qse4);
		System.out.println("parts size " + parts.size());
		byte[] testParts = partitioner.asByteArray(testValue, "string");
		System.out.println("asByteArray Size " + testParts.length);
		assertEquals("byte array size should be fixed at (" + qse4.getSize() + ")", qse4.getSize(), parts.size());
		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "string", qse4);
		System.out.println("Original String Value (" + testValue + ")");
		System.out.println("Return Part (" + returnPart.toString().trim() + ")");
		assertEquals(testValue.substring(0, 16).trim(), returnPart);
	}

	@Test
	public void fixedShortStringToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		System.out.println("Test Fixed Length (" + qse4.getSize() + ") String which is shorter");
		List<Byte> parts = new ArrayList<>();
		String testValue = "A Cup of Java";
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse4.getName()).getDataType(), qse4);
		System.out.println("Byte size " + parts.size());
		byte[] testParts = partitioner.asByteArray(testValue, "string");
		System.out.println("asByteArray Size " + testParts.length);
		assertEquals("byte array size should be fixed at (" + qse4.getSize() + ")", qse4.getSize(), parts.size());
		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "string", qse4);
		System.out.println("Original String Value (" + testValue.trim() + ")");
		System.out.println("Return Part (" + returnPart.toString() + ")");
		assertEquals(testValue.trim(), returnPart.toString().trim());
	}

	@Test
	public void variableStringToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		List<Byte> parts = new ArrayList<>();
		System.out.println("Test Variable Length (" + qse5.getSize() + ") String which is longer");
		String testValue = "A Cup of Java";
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse5.getName()).getDataType(), qse5);
		System.out.println("Byte size " + parts.size());
		assertEquals(14, parts.size());
		byte[] testParts = partitioner.asByteArray(testValue, "string");
		System.out.println("asByteArray Size " + testParts.length);
		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "string", qse5);
		System.out.println("Original String Value (" + testValue + ")");
		System.out.println("Return Part (" + returnPart.toString().trim() + ")");
		assertEquals(testValue, returnPart);
	}

	@Test
	public void variableLargeStringToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		List<Byte> parts = new ArrayList<>();
		System.out.println("Test Variable Length (" + qse8.getSize() + ") String");
		System.out.println("  with createPartitons test within");
		String testValue = "This is a conversion test for a variable string length.";
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse8.getName()).getDataType(), qse8);
		System.out.println("Byte size " + parts.size());
		assertEquals(testValue.length() + 2, parts.size());
		byte[] testParts = partitioner.asByteArray(testValue, "string");
		System.out.println("asByteArray Size " + testParts.length);
		List<byte[]> partitionedBytes = partitioner.createPartitions(parts, 8);
		System.out.println("Number of Partitions at dataPartitionSize(8) " + partitionedBytes.size());
		listPartitions(partitionedBytes);
		partitionedBytes = partitioner.createPartitions(parts, 16);
		System.out.println("Number of Partitions at dataPartitionSize(16) " + partitionedBytes.size());
		listPartitions(partitionedBytes);
		partitionedBytes = partitioner.createPartitions(parts, 24);
		System.out.println("Number of Partitions at dataPartitionSize(24) " + partitionedBytes.size());
		listPartitions(partitionedBytes);
		partitionedBytes = partitioner.createPartitions(parts, 32);
		System.out.println("Number of Partitions at dataPartitionSize(32) " + partitionedBytes.size());
		listPartitions(partitionedBytes);

		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "string", qse8);
		System.out.println("Original String Value (" + testValue + ")");
		System.out.println("Return Part (" + returnPart.toString() + ")");
		assertEquals(testValue, returnPart);
	}

	@Test
	public void variableShortStringToBytes() throws PIRException {
		System.out.println("++++++++++++++++++++++++++++");
		List<Byte> parts = new ArrayList<>();
		System.out.println("Test Variable Length (" + qse5.getSize() + ") String which is shorter");
		String testValue = "Test String";
		parts = partitioner.fieldToBytes(testValue, dSchema.elementByName(qse5.getName()).getDataType(), qse5);
		System.out.println("Byte size " + parts.size());
		assertEquals("Size sould be " + testValue.length() + 1, testValue.length() + 1, parts.size());
		byte[] testParts = partitioner.asByteArray(testValue, "string");
		System.out.println("asByteArray Size " + testParts.length);
		Object returnPart = null;
		returnPart = partitioner.fieldDataFromPartitionedBytes(parts, 0, "string", qse5);
		System.out.println("Original String Value (" + testValue + ")");
		System.out.println("Return Part (" + returnPart.toString() + ")");
		assertEquals(testValue.trim(), returnPart.toString());
	}

}
