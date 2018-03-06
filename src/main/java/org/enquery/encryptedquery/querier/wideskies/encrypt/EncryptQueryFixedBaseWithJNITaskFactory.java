/*
 * Copyright 2017 EnQuery.
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
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
 */
package org.enquery.encryptedquery.querier.wideskies.encrypt;

import java.math.BigInteger;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runnable class for multithreaded PIR query generation
 */
class EncryptQueryFixedBaseWithJNITask implements EncryptQueryTask
{
  private static final Logger logger = LoggerFactory.getLogger(EncryptQueryFixedBaseWithJNITask.class);

  private final long hContext;
  private final int dataPartitionBitSize;
  private final Map<Integer,Integer> selectorQueryVecMapping;
  private final Paillier paillier;
  private final Random random;
  private final int maxExponentBitLength;
  private final BigInteger pMaxExponent;
  private final BigInteger qMaxExponent;
  private final BigInteger p;
  private final BigInteger q;
  private final int start; // start of computing range for the runnable
  private final int stop; // stop, inclusive, of the computing range for the runnable

  public EncryptQueryFixedBaseWithJNITask(EncryptQueryFixedBaseWithJNITaskFactory factory, Map<Integer,Integer> selectorQueryVecMapping, int start, int stop)
  {
    this.hContext = factory.getContextHandle();
    this.dataPartitionBitSize = factory.getDataPartitionBitSize();
    this.paillier = factory.getPaillier();
    this.random = factory.getRandom();
    this.maxExponentBitLength = factory.getMaxExponentBitLength();
    this.pMaxExponent = factory.getPMaxExponent();
    this.qMaxExponent = factory.getQMaxExponent();
    this.selectorQueryVecMapping = selectorQueryVecMapping;
    this.start = start;
    this.stop = stop;
    this.p = paillier.getP();
    this.q = paillier.getQ();
  }

  @Override
  public SortedMap<Integer,BigInteger> call() throws PIRException
  {
    // holds the ordered encrypted values to pull after thread computation is complete
    SortedMap<Integer,BigInteger> encryptedValues = new TreeMap<>();
    for (int i = start; i <= stop; i++)
    {
      Integer selectorNum = selectorQueryVecMapping.get(i);
      int bitIndex = (selectorNum == null) ? -1 : selectorNum * dataPartitionBitSize;
      BigInteger encVal = encrypt(bitIndex);
      encryptedValues.put(i, encVal);
      logger.debug("selectorNum = " + selectorNum + " bitIndex = " + bitIndex + " encVal = " + encVal);
    }

    return encryptedValues;
  }

  private native byte[] encrypt(long hContext, int bitIndex, byte[] rpBytes, byte[] rqBytes);

  private BigInteger encrypt(int bitIndex)
  {
    BigInteger rp, rq;
    do
    {
      rp = new BigInteger(maxExponentBitLength, random);
    }
    while (rp.compareTo(this.pMaxExponent) >= 0 || rp.equals(BigInteger.ZERO));
    do
    {
        rq = new BigInteger(maxExponentBitLength, random);
    }
    while (rq.compareTo(this.qMaxExponent) >= 0 || rq.equals(BigInteger.ZERO));
    byte[] bytes = encrypt(hContext, bitIndex, rp.toByteArray(), rq.toByteArray());
    return new BigInteger(1, bytes);
  }
}


/**
 * Factory class for creating runnables for multithreaded PIR query
 * generation using the faster fixed-base point method plus JNI
 * acceleration.
 */
public class EncryptQueryFixedBaseWithJNITaskFactory implements EncryptQueryTaskFactory
{
  static
  {
    String libraryBaseName = SystemConfiguration.getProperty("pir.fixedBaseQueryGeneration.JNILibName");
    System.loadLibrary(libraryBaseName);
  }
  
  private static final Logger logger = LoggerFactory.getLogger(EncryptQueryFixedBaseWithJNITaskFactory.class);
  private final BigInteger p;
  private final BigInteger q;
  private final Paillier paillier;
  private final BigInteger genPSquared;
  private final BigInteger genQSquared;
  private final BigInteger pMaxExponent;
  private final BigInteger qMaxExponent;
  private final int maxExponentBitLength;  // TODO: separate ones for p and q?
  private final int windowSize = 8; // TODO: make configurable
  private final int numWindows;
  private final Random random;
  private final int dataPartitionBitSize;
  private final Map<Integer,Integer> selectorQueryVecMapping;
  private long hContext = 0;  // opaque handle to context in native code

  public EncryptQueryFixedBaseWithJNITaskFactory(int dataPartitionBitSize, Paillier paillier, Map<Integer,Integer> selectorQueryVecMapping)
  {
    logger.info("initializing EncryptQueryFixedBaseWithJNITaskFactory instance");
    this.dataPartitionBitSize = dataPartitionBitSize;
    this.selectorQueryVecMapping = selectorQueryVecMapping;
    this.p = paillier.getP();
    this.q = paillier.getQ();
    this.paillier = paillier;
    this.genPSquared = paillier.getPBasePoint();
    this.genQSquared = paillier.getQBasePoint();
    this.pMaxExponent = paillier.getPMaxExponent();
    this.qMaxExponent = paillier.getQMaxExponent();
    this.maxExponentBitLength = pMaxExponent.bitLength();
    if (this.maxExponentBitLength != qMaxExponent.bitLength())
    {
      throw new IllegalArgumentException("pMaxExponent and qMaxExponent have different bit lengths");
    }
    this.numWindows = (maxExponentBitLength + windowSize - 1) / windowSize;
    this.random = RandomProvider.SECURE_RANDOM;
    this.hContext = initializeNativeCode();  // TODO: throw exception?
    if (0 == this.hContext)
    {
      throw new NullPointerException("failed to allocate context from native code");
    }
  }

  private native long newContext(byte[] pBytes, byte[] genPSquaredBytes, byte[] qBytes, byte[] genQSquaredBytes, int primeBitLength, int windowSize, int numWindows);

  private long initializeNativeCode()
  {
    return newContext(p.toByteArray(), genPSquared.toByteArray(), q.toByteArray(), genQSquared.toByteArray(), -1, windowSize, numWindows);
  }

  public EncryptQueryFixedBaseWithJNITask createTask(int start, int stop)
  {
    return new EncryptQueryFixedBaseWithJNITask(this, selectorQueryVecMapping, start, stop);
  }

  public Paillier getPaillier() { return paillier; }

  public int getMaxExponentBitLength() { return maxExponentBitLength; }

  public BigInteger getPMaxExponent() { return pMaxExponent; }

  public BigInteger getQMaxExponent() { return qMaxExponent; }

  public long getHContext() { return hContext; }

  public Random getRandom() { return random; }

  public int getDataPartitionBitSize() { return dataPartitionBitSize; }

  public long getContextHandle() { return hContext; }
}
