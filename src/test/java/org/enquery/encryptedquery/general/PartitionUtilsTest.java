/*
 * Copyright 2017 EnQuery.
 * This product includes software licensed to EnQuery under 
 * one or more license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file has been modified from its original source.
 */
package org.enquery.encryptedquery.general;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.enquery.encryptedquery.schema.data.partitioner.IPDataPartitioner;
import org.enquery.encryptedquery.schema.data.partitioner.ISO8601DatePartitioner;
import org.enquery.encryptedquery.schema.data.partitioner.PrimitiveTypePartitioner;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to functionally test the bit conversion utils
 */
public class PartitionUtilsTest
{
  private static final Logger logger = LoggerFactory.getLogger(PartitionUtilsTest.class);

  @Test
  public void testMask()
  {
    logger.info("Starting testMask: ");

    assertEquals(0, PrimitiveTypePartitioner.formBitMask(0).intValue());

    assertEquals(0b000000000000001, PrimitiveTypePartitioner.formBitMask(1).intValue());
    assertEquals(0b000000000001111, PrimitiveTypePartitioner.formBitMask(4).intValue());
    assertEquals(0b000000001111111, PrimitiveTypePartitioner.formBitMask(7).intValue());
    assertEquals(0b111111111111111, PrimitiveTypePartitioner.formBitMask(15).intValue());

    assertEquals(new BigInteger("FFFFF", 16), PrimitiveTypePartitioner.formBitMask(20));
    assertEquals(new BigInteger("FFFFFFFF", 16), PrimitiveTypePartitioner.formBitMask(32));
    assertEquals(new BigInteger("3FFFFFFFFFF", 16), PrimitiveTypePartitioner.formBitMask(42));
    assertEquals(new BigInteger("7FFFFFFFFFFFFFFF", 16), PrimitiveTypePartitioner.formBitMask(63));

    logger.info("Successfully completed testMask");
  }


  @Test
  public void testPartitions() throws Exception
  {
    logger.info("Starting testToPartitions:");

    PrimitiveTypePartitioner primitivePartitioner = new PrimitiveTypePartitioner();
    IPDataPartitioner ipPartitioner = new IPDataPartitioner();
    ISO8601DatePartitioner datePartitioner = new ISO8601DatePartitioner();

    // Test IP
    String ipTest = "127.0.0.1";
    List<Byte> partsIP = ipPartitioner.toPartitions(ipTest, PrimitiveTypePartitioner.STRING);
    assertEquals(4, partsIP.size());
    assertEquals(ipTest, ipPartitioner.fromPartitions(partsIP, 0, PrimitiveTypePartitioner.STRING));
//
//    // Test Date
    String dateTest = "2016-02-20T23:29:05.000Z";
    List<Byte> partsDate = datePartitioner.toPartitions(dateTest, null);
    assertEquals(8, partsDate.size());
    assertEquals(dateTest, datePartitioner.fromPartitions(partsDate, 0, null));
//
//    // Test byte
    byte bTest = Byte.parseByte("10");
    List<Byte> partsByte = primitivePartitioner.toPartitions(bTest, PrimitiveTypePartitioner.BYTE);
    assertEquals(1, partsByte.size());
    assertEquals(bTest, primitivePartitioner.fromPartitions(partsByte, 0, PrimitiveTypePartitioner.BYTE));
//
    partsByte = primitivePartitioner.toPartitions("12", PrimitiveTypePartitioner.BYTE);
    assertEquals(1, partsByte.size());
    assertEquals((byte) 12, primitivePartitioner.fromPartitions(partsByte, 0, PrimitiveTypePartitioner.BYTE));
//
    List<Byte> partsByteMax = primitivePartitioner.toPartitions(Byte.MAX_VALUE, PrimitiveTypePartitioner.BYTE);
    assertEquals(1, partsByteMax.size());
    assertEquals(Byte.MAX_VALUE, primitivePartitioner.fromPartitions(partsByteMax, 0, PrimitiveTypePartitioner.BYTE));
//
//    // Test string
    String stringBits = SystemConfiguration.getProperty("pir.stringBits");
    SystemConfiguration.setProperty("pir.stringBits", "64");
    testString("testString"); // over the allowed bit size
    testString("t"); // under the allowed bit size
    SystemConfiguration.setProperty("pir.stringBits", stringBits);
//
//    // Test short
    short shortTest = Short.valueOf("2456");
    List<Byte> partsShort = primitivePartitioner.toPartitions(shortTest, PrimitiveTypePartitioner.SHORT);
    assertEquals(2, partsShort.size());
    assertEquals(shortTest, primitivePartitioner.fromPartitions(partsShort, 0, PrimitiveTypePartitioner.SHORT));
//
    partsShort = primitivePartitioner.toPartitions("32767", PrimitiveTypePartitioner.SHORT);
    assertEquals(2, partsShort.size());
    assertEquals((short) 32767, primitivePartitioner.fromPartitions(partsShort, 0, PrimitiveTypePartitioner.SHORT));
//
    partsShort = primitivePartitioner.toPartitions((short) -42, PrimitiveTypePartitioner.SHORT);
    assertEquals(2, partsShort.size());
    assertEquals((short) -42, primitivePartitioner.fromPartitions(partsShort, 0, PrimitiveTypePartitioner.SHORT));
//
    List<Byte> partsShortMax = primitivePartitioner.toPartitions(Short.MAX_VALUE, PrimitiveTypePartitioner.SHORT);
    assertEquals(2, partsShortMax.size());
    assertEquals(Short.MAX_VALUE, primitivePartitioner.fromPartitions(partsShortMax, 0, PrimitiveTypePartitioner.SHORT));
//
//    // Test int
    int intTest = Integer.parseInt("-5789");
    List<Byte> partsInt = primitivePartitioner.toPartitions(intTest, PrimitiveTypePartitioner.INT);
    assertEquals(4, partsInt.size());
    assertEquals(intTest, primitivePartitioner.fromPartitions(partsInt, 0, PrimitiveTypePartitioner.INT));
//
    partsInt = primitivePartitioner.toPartitions("2016", PrimitiveTypePartitioner.INT);
    assertEquals(4, partsInt.size());
    assertEquals(2016, primitivePartitioner.fromPartitions(partsInt, 0, PrimitiveTypePartitioner.INT));
//
    partsInt = primitivePartitioner.toPartitions(1386681237, PrimitiveTypePartitioner.INT);
    assertEquals(4, partsInt.size());
    assertEquals(1386681237, primitivePartitioner.fromPartitions(partsInt, 0, PrimitiveTypePartitioner.INT));
//
    List<Byte> partsIntMax = primitivePartitioner.toPartitions(Integer.MAX_VALUE, PrimitiveTypePartitioner.INT);
    assertEquals(4, partsIntMax.size());
    assertEquals(Integer.MAX_VALUE, primitivePartitioner.fromPartitions(partsIntMax, 0, PrimitiveTypePartitioner.INT));
//
//    // Test long
    long longTest = Long.parseLong("56789");
    List<Byte> partsLong = primitivePartitioner.toPartitions(longTest, PrimitiveTypePartitioner.LONG);
    assertEquals(8, partsLong.size());
    assertEquals(longTest, primitivePartitioner.fromPartitions(partsLong, 0, PrimitiveTypePartitioner.LONG));
//
    List<Byte> partsLongMax = primitivePartitioner.toPartitions(Long.MAX_VALUE, PrimitiveTypePartitioner.LONG);
    assertEquals(8, partsLongMax.size());
    assertEquals(Long.MAX_VALUE, primitivePartitioner.fromPartitions(partsLongMax, 0, PrimitiveTypePartitioner.LONG));
//
//    // Test float
    float floatTest = Float.parseFloat("567.77");
    List<Byte> partsFloat = primitivePartitioner.toPartitions(floatTest, PrimitiveTypePartitioner.FLOAT);
    assertEquals(4, partsFloat.size());
    assertEquals(floatTest, primitivePartitioner.fromPartitions(partsFloat, 0, PrimitiveTypePartitioner.FLOAT));
//
    partsFloat = primitivePartitioner.toPartitions(-99.99f, PrimitiveTypePartitioner.FLOAT);
    assertEquals(4, partsFloat.size());
    assertEquals(-99.99f, primitivePartitioner.fromPartitions(partsFloat, 0, PrimitiveTypePartitioner.FLOAT));
//
    List<Byte> partsFloatMax = primitivePartitioner.toPartitions(Float.MAX_VALUE, PrimitiveTypePartitioner.FLOAT);
    assertEquals(4, partsFloatMax.size());
    assertEquals(Float.MAX_VALUE, primitivePartitioner.fromPartitions(partsFloatMax, 0, PrimitiveTypePartitioner.FLOAT));
//
//    // Test double
    double doubleTest = Double.parseDouble("567.77");
    List<Byte> partsDouble = primitivePartitioner.toPartitions(doubleTest, PrimitiveTypePartitioner.DOUBLE);
    assertEquals(8, partsDouble.size());
    assertEquals(doubleTest, primitivePartitioner.fromPartitions(partsDouble, 0, PrimitiveTypePartitioner.DOUBLE));
//
    List<Byte> partsDoubleMax = primitivePartitioner.toPartitions(Double.MAX_VALUE, PrimitiveTypePartitioner.DOUBLE);
    assertEquals(8, partsDoubleMax.size());
    assertEquals(Double.MAX_VALUE, primitivePartitioner.fromPartitions(partsDoubleMax, 0, PrimitiveTypePartitioner.DOUBLE));
//
//    // Test char
    char charTest = 'b';
    List<Byte> partsChar = primitivePartitioner.toPartitions(charTest, PrimitiveTypePartitioner.CHAR);
    assertEquals(2, partsChar.size());
    assertEquals(charTest, primitivePartitioner.fromPartitions(partsChar, 0, PrimitiveTypePartitioner.CHAR));
//
//    // Ensure Endianness preserved
    charTest = '\uFFFE';
    partsChar = primitivePartitioner.toPartitions(charTest, PrimitiveTypePartitioner.CHAR);
    assertEquals(2, partsChar.size());
    assertEquals(charTest, primitivePartitioner.fromPartitions(partsChar, 0, PrimitiveTypePartitioner.CHAR));
//
//    charTest = '\uFEFF';
    partsChar = primitivePartitioner.toPartitions(charTest, PrimitiveTypePartitioner.CHAR);
    assertEquals(2, partsChar.size());
    assertEquals(charTest, primitivePartitioner.fromPartitions(partsChar, 0, PrimitiveTypePartitioner.CHAR));
//
    List<Byte> partsCharMax = primitivePartitioner.toPartitions(Character.MAX_VALUE, PrimitiveTypePartitioner.CHAR);
    assertEquals(2, partsCharMax.size());
    assertEquals(Character.MAX_VALUE, primitivePartitioner.fromPartitions(partsCharMax, 0, PrimitiveTypePartitioner.CHAR));

    logger.info("Sucessfully completed testToPartitions:");
  }

  @Test
  public void testPaddedPartitions() throws PIRException
  {
    PrimitiveTypePartitioner primitivePartitioner = new PrimitiveTypePartitioner();

    List<String> primitiveTypes = Arrays.asList(PrimitiveTypePartitioner.BYTE, PrimitiveTypePartitioner.CHAR, PrimitiveTypePartitioner.SHORT,
        PrimitiveTypePartitioner.INT, PrimitiveTypePartitioner.LONG, PrimitiveTypePartitioner.FLOAT, PrimitiveTypePartitioner.DOUBLE,
        PrimitiveTypePartitioner.STRING);
    for (String type : primitiveTypes)
    {
      assertEquals(primitivePartitioner.getNumPartitions(type), primitivePartitioner.getPaddedPartitions(type).size());
    }
  }

  private void testString(String testString) throws Exception
  {
    PrimitiveTypePartitioner ptp = new PrimitiveTypePartitioner();

    List<Byte> partsString = ptp.toPartitions(testString, PrimitiveTypePartitioner.STRING);
    int numParts = Integer.parseInt(SystemConfiguration.getProperty("pir.stringBits")) / 8;
    assertEquals(numParts, partsString.size());

    logger.info("testString.getBytes().length = " + testString.getBytes().length);
    int offset = numParts;
    if (testString.getBytes().length < numParts)
    {
      offset = testString.getBytes().length;
    }
    String element = new String(testString.getBytes(), 0, offset);
//    assertEquals(element, ptp.fromPartitions(partsString, 0, PrimitiveTypePartitioner.STRING));
  }
}
