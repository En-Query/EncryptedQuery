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
package org.enquery.encryptedquery.querier.wideskies.encrypt;

import java.math.BigInteger;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import org.enquery.encryptedquery.encryption.ChineseRemainder;
import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.utils.MontgomeryReduction;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runnable class for multithreaded PIR query generation
 */
class EncryptQueryFixedBaseTask implements EncryptQueryTask
{
  private static final Logger logger = LoggerFactory.getLogger(EncryptQueryFixedBaseTask.class);

  private final int dataPartitionBitSize;
  private final Paillier paillier;
  private final Random random;
  private final int maxExponentBitLength;
  private final BigInteger pMaxExponent;
  private final BigInteger qMaxExponent;
  private final BigInteger p;
  private final BigInteger q;
  private final BigInteger N;
  private final BigInteger NSquared;
  private final ChineseRemainder crtNSquared;
  private final MontgomeryReduction montPSquared;
  private final MontgomeryReduction montQSquared;
  private final BigInteger[][] pLUT;
  private final BigInteger[][] qLUT;
  private final int windowSize;

  private final Map<Integer,Integer> selectorQueryVecMapping;
  private final int start; // start of computing range for the runnable
  private final int stop; // stop, inclusive, of the computing range for the runnable

  public EncryptQueryFixedBaseTask(EncryptQueryFixedBaseTaskFactory factory, Map<Integer,Integer> selectorQueryVecMapping, int start, int stop)
  {
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
    this.N = paillier.getN();
    this.NSquared = factory.getNSquared();
    this.crtNSquared = factory.getCrtNSquared();
    this.montPSquared = factory.getMontPSquared();
    this.montQSquared = factory.getMontQSquared();
    this.pLUT = factory.getPLookupTable();
    this.qLUT = factory.getQLookupTable();
    this.windowSize = factory.getWindowSize();
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
    return encryptWithLookupTables(bitIndex, rp, rq);
  }

  private BigInteger encryptWithLookupTables(int bitIndex, BigInteger rp, BigInteger rq)
  {
    BigInteger pAnswer = encryptWithLookupTable(bitIndex, rp, pLUT, montPSquared);
    BigInteger qAnswer = encryptWithLookupTable(bitIndex, rq, qLUT, montQSquared);
    pAnswer = montPSquared.fromMontgomery(pAnswer);
    qAnswer = montQSquared.fromMontgomery(qAnswer);
    BigInteger answer = this.crtNSquared.combine(pAnswer, qAnswer, NSquared);

    if (bitIndex >= 0)
    {
      BigInteger tmp = N.shiftLeft(bitIndex).add(BigInteger.ONE).mod(NSquared);
      answer = answer.multiply(tmp).mod(NSquared);
    }

    return answer;
  }

  private BigInteger encryptWithLookupTable(int bitIndex, BigInteger r, BigInteger[][] lut, MontgomeryReduction mont)
  {
    byte[] rbytes = r.toByteArray();
    int windowsPerByte = 8 / windowSize;
    int winMask = (1 << windowSize) - 1;
    int numWindows = rbytes.length * windowsPerByte;
    if (numWindows > lut.length)
    {
      numWindows = lut.length;
    }
    BigInteger ans = mont.getMontOne();
    boolean done = false;
    int win = 0;
    for (int j=0; j<rbytes.length; j++)
    {
      int byteIndex = rbytes.length - 1 - j;
      int byteValue = (int)rbytes[byteIndex] & 0xff;
      for (int i=0; i<windowsPerByte; i++)
      {
        int winValue = byteValue & winMask;
        byteValue >>= windowSize;
        if (winValue != 0)
        {
          ans = mont.montMultiply(ans, lut[win][winValue-1]);
        }
        win += 1;
        if (win >= numWindows)
        {
          done = true;
          break;
        }
      }
      if (done) { break; }
    }
    
    return ans;
  }
}


/**
 * Factory class for creating runnables for multithreaded PIR query
 * generation using the faster fixed-base point method.
 */
public class EncryptQueryFixedBaseTaskFactory implements EncryptQueryTaskFactory
{
  private static final Logger logger = LoggerFactory.getLogger(EncryptQueryFixedBaseTaskFactory.class);
  private final BigInteger p;
  private final BigInteger q;
  private final BigInteger pSquared;
  private final BigInteger qSquared;
  private final BigInteger NSquared;
  private final MontgomeryReduction montPSquared;
  private final MontgomeryReduction montQSquared;
  private final ChineseRemainder crtNSquared;
  private final Paillier paillier;
  private final BigInteger genPSquared;
  private final BigInteger genQSquared;
  private final BigInteger pMaxExponent;
  private final BigInteger qMaxExponent;
  private final int maxExponentBitLength;  // TODO: separate ones for p and q?
  private final int windowSize = 8; // TODO: make configurable
  private final int numWindows;
  private final Random random;
  private final Map<Integer,Integer> selectorQueryVecMapping;
  private int dataPartitionBitSize;
  private final BigInteger[][] pLUT;
  private final BigInteger[][] qLUT;

  public EncryptQueryFixedBaseTaskFactory(int dataPartitionBitSize, Paillier paillier, Map<Integer,Integer> selectorQueryVecMapping)
  {
    logger.info("initializing EncryptQueryFixedBaseTaskFactory instance");
    this.dataPartitionBitSize = dataPartitionBitSize;
    this.selectorQueryVecMapping = selectorQueryVecMapping;
    this.p = paillier.getP();
    this.q = paillier.getQ();
    this.pSquared = p.multiply(p);
    this.qSquared = q.multiply(q);
    this.NSquared = pSquared.multiply(qSquared);
    this.montPSquared = new MontgomeryReduction(this.pSquared);
    this.montQSquared = new MontgomeryReduction(this.qSquared);
    this.crtNSquared = new ChineseRemainder(pSquared, qSquared);
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
    pLUT = computeLookupTable(windowSize, numWindows, montPSquared, genPSquared);
    qLUT = computeLookupTable(windowSize, numWindows, montQSquared, genQSquared);
  }

  private BigInteger[][] computeLookupTable(int windowSize, int numWindows, MontgomeryReduction montPSquared, BigInteger basePoint)
  {
    int W = (1 << windowSize) - 1;
    BigInteger[][] lut = new BigInteger[numWindows][];
    BigInteger tmp = montPSquared.toMontgomery(basePoint);
    for (int win=0; win<numWindows; win++)
    {
      lut[win] = new BigInteger[W];
      BigInteger g = tmp;
      for (int i=0; i<W; i++)
      {
        lut[win][i] = tmp;
        tmp = montPSquared.montMultiply(tmp, g);
      }
    }
    return lut;
  }

  public EncryptQueryFixedBaseTask createTask(int start, int stop)
  {
    return new EncryptQueryFixedBaseTask(this, selectorQueryVecMapping, start, stop);
  }

  public Paillier getPaillier() { return paillier; }

  public int getMaxExponentBitLength() { return maxExponentBitLength; }

  public BigInteger getPMaxExponent() { return pMaxExponent; }

  public BigInteger getQMaxExponent() { return qMaxExponent; }

  public Random getRandom() { return random; }

  public int getDataPartitionBitSize() { return dataPartitionBitSize; }

  public BigInteger[][] getPLookupTable() { return pLUT; }
  
  public BigInteger[][] getQLookupTable() { return qLUT; }

  public int getWindowSize() { return windowSize; }

  public BigInteger getPSquared() { return pSquared; }

  public BigInteger getQSquared() { return qSquared; }

  public BigInteger getNSquared() { return NSquared; }

  public MontgomeryReduction getMontPSquared() { return montPSquared; }

  public MontgomeryReduction getMontQSquared() { return montQSquared; }

  public ChineseRemainder getCrtNSquared() { return crtNSquared; }
}
