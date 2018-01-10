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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.Random;

import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic test functionality for Paillier library
 * 
 */
public class PaillierTest
{
  private static final Logger logger = LoggerFactory.getLogger(PaillierTest.class);

  private static BigInteger p = null; // large prime
  private static BigInteger q = null; // large prime
  private static BigInteger N = null; // N=pq, RSA modulus
  private static BigInteger NSquared = null; // N^2

  private static int bitLength = 0; // bit length of the modulus N
  private static int certainty = 64; // prob that new BigInteger values represents primes will exceed (1 - (1/2)^certainty)

  private static BigInteger r1p = null; // random number in (Z/pZ)*
  private static BigInteger r2p = null; // random number in (Z/pZ)*

  private static BigInteger r1q = null; // random number in (Z/qZ)*
  private static BigInteger r2q = null; // random number in (Z/qZ)*

  private static BigInteger m1 = null; // message to encrypt
  private static BigInteger m2 = null; // message to encrypt

  @BeforeClass
  public static void setup()
  {
    p = BigInteger.valueOf(7);
    q = BigInteger.valueOf(17);
    N = p.multiply(q);
    NSquared = N.multiply(N);

    r1p = BigInteger.valueOf(3);
    r1q = BigInteger.valueOf(4);
    r2p = BigInteger.valueOf(5);
    r2q = BigInteger.valueOf(6);

    m1 = BigInteger.valueOf(5);
    m2 = BigInteger.valueOf(2);

    bitLength = 201;// bitLength = 384;
    certainty = 128;

    logger.info("p = " + p.intValue() + " q = " + q.intValue() + " N = " + N.intValue()
		+ " bitLength = " + bitLength
		+ " m1 = " + m1.intValue() + " m2 = " + m2.intValue()
		+ " r1p = " + r1p.intValue() + " r1q = " + r1q.intValue()
		+ " r2p = " + r2p.intValue() + " r2q = " + r2q.intValue()
		);
  }

  @Test
  public void testPIRExceptions()
  {
    try
    {
      Paillier paillier = new Paillier(BigInteger.valueOf(2), BigInteger.valueOf(2), 128);
      assertNotNull(paillier);
      fail("Paillier constructor did not throw exception for p,q < 3");
    } catch (IllegalArgumentException ignore)
    {}

    try
    {
      Paillier paillier = new Paillier(BigInteger.valueOf(2), BigInteger.valueOf(3), 128);
      assertNotNull(paillier);
      fail("Paillier constructor did not throw exception for p < 3");
    } catch (IllegalArgumentException ignore)
    {}

    try
    {
      Paillier paillier = new Paillier(BigInteger.valueOf(3), BigInteger.valueOf(2), 128);
      assertNotNull(paillier);
      fail("Paillier constructor did not throw exception for q < 3");
    } catch (IllegalArgumentException ignore)
    {}

    try
    {
      Paillier paillier = new Paillier(BigInteger.valueOf(7), BigInteger.valueOf(7), 128);
      assertNotNull(paillier);
      fail("Paillier constructor did not throw exception for p = q");
    } catch (IllegalArgumentException ignore)
    {}

    try
    {
      Paillier paillier = new Paillier(BigInteger.valueOf(8), BigInteger.valueOf(7), 128);
      assertNotNull(paillier);
      fail("Paillier constructor did not throw exception for p not prime");
    } catch (IllegalArgumentException ignore)
    {}

    try
    {
      Paillier paillier = new Paillier(BigInteger.valueOf(7), BigInteger.valueOf(10), 128);
      assertNotNull(paillier);
      fail("Paillier constructor did not throw exception for q not prime");
    } catch (IllegalArgumentException ignore)
    {}

    try
    {
      int systemPrimeCertainty = SystemConfiguration.getIntProperty("pir.primeCertainty", 128);
      Paillier paillier = new Paillier(3072, systemPrimeCertainty - 10);
      assertNotNull(paillier);
      fail("Paillier constructor did not throw exception for certainty less than system default of " + systemPrimeCertainty);
    } catch (IllegalArgumentException ignore)
    {}

    try
    {
      Paillier pailler = new Paillier(p, q, bitLength);
      BigInteger encM1 = pailler.encrypt(N);
      assertNotNull(encM1);
      fail("Paillier encryption did not throw PIRException for message m = N");
    } catch (PIRException ignore)
    {}

    try
    {
      Paillier pailler = new Paillier(p, q, bitLength);
      BigInteger encM1 = pailler.encrypt(N.add(BigInteger.TEN));
      assertNotNull(encM1);
      fail("Paillier encryption did not throw PIRException for message m > N");
    } catch (PIRException ignore)
    {}

    try
    {
      Paillier pailler = new Paillier(bitLength, 128, bitLength);
      assertNotNull(pailler);
      fail("Paillier constructor did not throw exception for ensureBitSet = bitLength");
    } catch (IllegalArgumentException ignore)
    {}

    try
    {
      Paillier pailler = new Paillier(bitLength, 128, bitLength + 1);
      assertNotNull(pailler);
      fail("Paillier constructor did not throw exception for ensureBitSet > bitLength");
    } catch (IllegalArgumentException ignore)
    {}
  }

  @Test
  public void testPaillierGivenAllParameters() throws Exception
  {
    logger.info("Starting testPaillierGivenAllParameters: ");

    Paillier pailler = new Paillier(p, q, bitLength);

    assertEquals(pailler.getN(), N);

    // Check encryption
    BigInteger encM1 = pailler.encrypt(m1, r1p, r1q);
    BigInteger encM2 = pailler.encrypt(m2, r2p, r2q);
    logger.info("encM1 = " + encM1.intValue() + " encM2 = " + encM2.intValue());

    assertEquals(encM1, BigInteger.valueOf(395));
    assertEquals(encM2, BigInteger.valueOf(13606));

    // Check decryption
    BigInteger decM1 = pailler.decrypt(encM1);
    BigInteger decM2 = pailler.decrypt(encM2);
    logger.info("decM1 = " + decM1.intValue() + " decM2 = " + decM2.intValue());

    assertEquals(decM1, m1);
    assertEquals(decM2, m2);

    // Check homomorphic property: E_r1(m1)*E_r2(m2) mod N^2 = E_r1r2((m1+m2) mod N) mod N^2
    BigInteger encM1_times_encM2 = (encM1.multiply(encM2)).mod(NSquared);
    BigInteger encM1plusM2 = pailler.encrypt((m1.add(m2)).mod(N), r1p.multiply(r2p), r1q.multiply(r2q));
    logger.info("encM1_times_encM2 = " + encM1_times_encM2.intValue() + " encM1plusM2 = " + encM1plusM2.intValue());

    assertEquals(encM1_times_encM2, BigInteger.valueOf(7351));
    assertEquals(encM1plusM2, BigInteger.valueOf(7351));

    logger.info("Successfully completed testPaillierGivenAllParameters: ");
  }

  @Test
  public void testPaillierWithKeyGeneration() throws Exception
  {
    logger.info("Starting testPaillierWithKeyGeneration: ");

    // Test with and without gmp optimization for modPow
    SystemConfiguration.setProperty("paillier.useGMPForModPow", "true");
    SystemConfiguration.setProperty("paillier.GMPConstantTimeMode", "true");
    testPaillerWithKeyGenerationGeneral();

    SystemConfiguration.setProperty("paillier.useGMPForModPow", "true");
    SystemConfiguration.setProperty("paillier.GMPConstantTimeMode", "false");
    testPaillerWithKeyGenerationGeneral();

    SystemConfiguration.setProperty("paillier.useGMPForModPow", "false");
    SystemConfiguration.setProperty("paillier.GMPConstantTimeMode", "false");
    testPaillerWithKeyGenerationGeneral();

    // Reset the properties
    SystemConfiguration.initialize();

    logger.info("Ending testPaillierWithKeyGeneration: ");
  }

  public void testPaillerWithKeyGenerationGeneral() throws Exception
  {
    // Test without requiring highest bit to be set
    logger.info("Starting testPaillierWithKeyGenerationBitSetOption with ensureHighBitSet = false");
    testPaillierWithKeyGenerationBitSetOption(-1);

    // Test requiring highest bit to be set
    logger.info("Starting testPaillierWithKeyGenerationBitSetOption with ensureHighBitSet = true");
    testPaillierWithKeyGenerationBitSetOption(5);
  }

  public void testPaillierWithKeyGenerationBitSetOption(int ensureBitSet) throws Exception
  {
    Random r = new Random();
    int lowBitLength = 3073; // inclusive
    int highBitLength = 4000; // exclusive

    int loopVal = 1; // int loopVal = 1000; //change this and re-test for high loop testing
    for (int i = 0; i < loopVal; ++i)
    {
      logger.info("i = " + i);

      basicTestPaillierWithKeyGeneration(bitLength, certainty, ensureBitSet);
      basicTestPaillierWithKeyGeneration(3072, certainty, ensureBitSet);

      // Test with random bit length between 3073 and 4000
      int randomLargeBitLength = r.nextInt(highBitLength - lowBitLength) + lowBitLength;
      basicTestPaillierWithKeyGeneration(randomLargeBitLength, certainty, ensureBitSet);
    }
  }

  private void basicTestPaillierWithKeyGeneration(int bitLengthInput, int certaintyInput, int ensureBitSet) throws Exception
  {
    Paillier pailler = new Paillier(bitLengthInput, certaintyInput, ensureBitSet);
    BigInteger generatedN = pailler.getN();
    BigInteger geneartedNsquared = generatedN.multiply(generatedN);

    // Check the decrypting the encryption yields the message
    BigInteger encM1 = pailler.encrypt(m1);
    BigInteger encM2 = pailler.encrypt(m2);
    logger.info("encM1 = " + encM1.intValue() + " encM2 = " + encM2.intValue());

    BigInteger decM1 = pailler.decrypt(encM1);
    BigInteger decM2 = pailler.decrypt(encM2);
    logger.info("decM1 = " + decM1.intValue() + " decM2 = " + decM2.intValue());

    assertEquals(decM1, m1);
    assertEquals(decM2, m2);

    // Check homomorphic property: E_r1(m1)*E_r2(m2) mod N^2 = E_r1r2((m1+m2) mod N) mod N^2
    BigInteger encM1_times_encM2 = (encM1.multiply(encM2)).mod(geneartedNsquared);
    BigInteger multDecrypt = pailler.decrypt(encM1_times_encM2);
    BigInteger m1_plus_m2 = (m1.add(m2)).mod(N);

    logger.info("encM1_times_encM2 = " + encM1_times_encM2.intValue() + " multDecrypt = " + multDecrypt.intValue() + " m1_plus_m2 = " + m1_plus_m2.intValue());

    assertEquals(multDecrypt, m1_plus_m2);

  }
}
