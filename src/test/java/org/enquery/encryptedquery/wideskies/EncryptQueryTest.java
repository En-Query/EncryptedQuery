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
package org.enquery.encryptedquery.wideskies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.Map;

import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.querier.wideskies.Querier;
import org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptQuery.KeyAndSelectorMapping;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Test for query generation
 * 
 */
public class EncryptQueryTest
{
  private static final Logger logger = LoggerFactory.getLogger(EncryptQueryTest.class);

  static final int bitLength = 3072;
  static final int dataPartitionBitSize = 8;
  static final int hashBitSize = 10;
  static final int certainty = 128;

  static final List<String> selectors = Arrays.asList("s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11", "s12", "s13", "s14", "s15", "s16", "s17", "s18", "s19");

  static Method computeSelectorQueryVecMap;
  static Method serialEncrypt;
  static Method parallelEncrypt;
  static Paillier paillier;
  static Map<Integer,Integer> selectorQueryVecMapping;
  static String hashKey;

  @BeforeClass
  public static void setup() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
  {
    computeSelectorQueryVecMap = EncryptQuery.class.getDeclaredMethod("computeSelectorQueryVecMap", int.class, List.class);
    computeSelectorQueryVecMap.setAccessible(true);
    serialEncrypt = EncryptQuery.class.getDeclaredMethod("serialEncrypt", Map.class, int.class, int.class, Paillier.class, String.class);
    serialEncrypt.setAccessible(true);
    parallelEncrypt = EncryptQuery.class.getDeclaredMethod("parallelEncrypt", int.class, Map.class, int.class, int.class, Paillier.class, String.class);
    parallelEncrypt.setAccessible(true);
    KeyAndSelectorMapping km = (KeyAndSelectorMapping) computeSelectorQueryVecMap.invoke(null, hashBitSize, selectors);
    hashKey = km.getHashKey();
    logger.info("new hashKey: " + hashKey);
    selectorQueryVecMapping = km.getSelectorQueryVecMapping();
    paillier = new Paillier(bitLength, certainty);
    logger.info("generated paillier with bitLength=" + bitLength + ", certainty=" + certainty);
  }

  @Test
  public void testEncryptQueryBasic() throws Exception
  {
    testSerial(EncryptQuery.DEFAULT);
    testParallel(1, EncryptQuery.DEFAULT);
    testParallel(2, EncryptQuery.DEFAULT);
    testParallel(3, EncryptQuery.DEFAULT);
  }
    
  @Test
  public void testEncryptQueryFast() throws Exception
  {
    testSerial(EncryptQuery.FAST);
    testParallel(1, EncryptQuery.FAST);
    testParallel(2, EncryptQuery.FAST);
    testParallel(3, EncryptQuery.FAST);
  }
    
  /*
  @Test
  public void testEncryptQueryFastWithJNI()
  {
  }
  */

  void testSerial(String method) throws IllegalAccessException, InvocationTargetException
  {
    logger.info("running serial test with method \"" + method + "\"");
    SortedMap<Integer,BigInteger> queryElements = (SortedMap<Integer,BigInteger>) serialEncrypt.invoke(null, selectorQueryVecMapping, hashBitSize, dataPartitionBitSize, paillier, method);
    for (int i=0; i<(1 << hashBitSize); i++)
    {
      if (selectorQueryVecMapping.containsKey(i))
      {
	assertTrue(selectorQueryVecMapping.get(i) * dataPartitionBitSize < bitLength);
	BigInteger expected = BigInteger.ZERO.setBit(selectorQueryVecMapping.get(i) * dataPartitionBitSize);
	assertTrue(paillier.decrypt(queryElements.get(i)).compareTo(expected) == 0);
      }
      else
      {
	assertTrue(paillier.decrypt(queryElements.get(i)).compareTo(BigInteger.ZERO) == 0);
      }
    }
  }

  void testParallel(int numThreads, String method) throws IllegalAccessException, InvocationTargetException
  {
    logger.info("running parallel test with " + numThreads + " threads and method \"" + method + "\"");
    SortedMap<Integer,BigInteger> queryElements = (SortedMap<Integer,BigInteger>) parallelEncrypt.invoke(null, numThreads, selectorQueryVecMapping, hashBitSize, dataPartitionBitSize, paillier, method);
    for (int i=0; i<(1 << hashBitSize); i++)
    {
      if (selectorQueryVecMapping.containsKey(i))
      {
	assertTrue(selectorQueryVecMapping.get(i) * dataPartitionBitSize < bitLength);
	BigInteger expected = BigInteger.ZERO.setBit(selectorQueryVecMapping.get(i) * dataPartitionBitSize);
	assertTrue(paillier.decrypt(queryElements.get(i)).compareTo(expected) == 0);
      }
      else
      {
	assertTrue(paillier.decrypt(queryElements.get(i)).compareTo(BigInteger.ZERO) == 0);
      }
    }
  }
}

