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
package org.enquery.encryptedquery.encryption;

import com.google.gson.annotations.Expose;

import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Implementation of the Paillier cryptosystem.
 * <p>
 * The basic Paillier scheme works as follows.  Let N=pq be a RSA
 * modulus where p,q are large primes of roughly the same bit
 * length. The plaintext space is the additive group Z/NZ and the
 * ciphertext space is the multiplicative group (Z/N^2 Z)*.  The
 * public key is the value of N while the private key is the
 * factorization of N into p and q.  Let lambda(N) = lcm(p-1,q-1) be
 * the Carmichael function of N (the exponent of the multiplicative
 * group of units modulo N):
 * <p>
 * Encryption E(m) for a message m is as follows.  Given N and m,
 * <ul>
 * <li>Select a random value r in (Z/NZ)*
 * <li>E(m) = (1 + mN)r^N mod N^2
 * </ul>
 * <p>
 * Decryption D(c) for a ciphertext c is as follows.  Given N, its
 * factorization N=pq, and ciphertext c
 * <ul>
 * <li>Set w = lambda(N)^-1 mod N
 * <li>Set x = c^(lambda(N))mod N^2
 * <li>Set y = (x-1)/N
 * <li>Set D(c) = yw mod N
 * </ul>
 * <p>
 * Note that encryption is randomized: encrypting the same message
 * twice will produce two different ciphertext values.  However, both
 * ciphertext values will both decrypt to the same original message.
 * <p>
 * Our implementation works faster by performing encryption and
 * decryption modulo p^2 and q^2 separately and then combining the
 * results using the Chinese Remainder Theorem.
 * <p>
 * Reference: Paillier, Pascal. "Public-Key Cryptosystems Based on
 * Composite Degree Residuosity Classes." EUROCRYPT'99.
 */

// Our implementation works as follows:
//
// Encryption E(m) for a message m:
//
//  * Select a random value r1 in (Z/pZ)*
//  * Set e1 = (1 + mN)r1^p mod p^2
//  * Select a random value r2 in (Z/qZ)*
//  * Set e2 = (1 + mN)r2^q mod q^2
//  * Set E(m) to be the unique value e in (Z/N^2Z)* such that e mod
//      p^2 = e1 and e mod q^2 = e2.
//
// Decryption D(c) for a ciphetext c:
//
//  * Set w1 = -q mod p
//  * Set w2 = -p mod q
//  * Set c1 = c mod p^2
//  * Set c2 = c mod q^2
//  * Set x1 = c1^(p-1) mod p^2
//  * Set x2 = c2^(q-1) mod q^2
//  * Set y1 = (x1-1)/p
//  * Set y2 = (x2-1)/q
//  * Set d1 = (y1 * w1) mod p
//  * Set d2 = (y2 * w2) mod p
//  * Set D(c) = the unique value d in Z/NZ such that d mod p = d1 and d
//      mod q = d2.

public final class Paillier implements Serializable
{
  private static final long serialVersionUID = 1L;

  private static final Logger logger = LoggerFactory.getLogger(Paillier.class);

  @Expose
  private BigInteger p; // large prime
  @Expose
  private BigInteger q; // large prime
  @Expose
  private final int bitLength; // bit length of the modulus N
  @Expose
  private BigInteger pBasePoint; // mod p^2 base point for fast query generation
  @Expose
  private BigInteger pMaxExponent; // one plus max exponent for mod p^2 base point
  @Expose
  private BigInteger qBasePoint; // mod q^2 base point for fast query generation
  @Expose
  private BigInteger qMaxExponent; // one plus max exponent for mod q^2 base point
  
  private BigInteger N; // N=pq, RSA modulus
  
  private BigInteger pSquared; // pSquared = p^2
  
  private BigInteger qSquared; // qSquared = q^2

  private BigInteger NSquared; // NSquared = N^2

  private ChineseRemainder crtNSquared; // CRT for moduli p^2 and q^2

  private ChineseRemainder crtN; // CRT for moduli p and q

  private BigInteger pMinusOne; // p-1

  private BigInteger qMinusOne; // q-1

  private BigInteger wp; // ((p-1)*q)^-1 mod p

  private BigInteger wq; // ((q-1)*p)^-1 mod q

  /**
   * Creates a Paillier algorithm with all parameters specified.
   *
   * @param p
   *          First large prime.
   * @param q
   *          Second large prime.
   * @param bitLength
   *          Bit length of the modulus {@code N}.
   * @throws IllegalArgumentException
   *           If {@code p} or {@code q} do not satisfy primality constraints.
   */
  public Paillier(BigInteger p, BigInteger q, int bitLength)
  {
    this.bitLength = bitLength;

    // Verify the prime conditions are satisfied
    int primeCertainty = SystemConfiguration.getIntProperty("pir.primeCertainty", 128);
    BigInteger three = BigInteger.valueOf(3);
    if ((p.compareTo(three) < 0) || (q.compareTo(three) < 0) || p.equals(q) || !p.isProbablePrime(primeCertainty) || !q.isProbablePrime(primeCertainty))
    {
      throw new IllegalArgumentException("p = " + p + " q = " + q + " do not satisfy primality constraints");
    }

    this.p = p;
    this.q = q;

    this.N = p.multiply(q);

    setGenericBasePointsAndExponents();

    setDerivativeElements();

    /*
    // setting base point to (rand)^p mod p^2
    // this depends only on rand mod p so we can pick rand < p
    do {
      this.pBasePoint = new BigInteger(p.bitLength(), RandomProvider.SECURE_RANDOM);
    }
    while (this.pBasePoint.compareTo(BigInteger.ONE) <= 0 || this.pBasePoint.compareTo(p) >= 0);
    this.pBasePoint = ModPowAbstraction.modPow(this.pBasePoint, p, pSquared);
    this.pMaxExponent = p.subtract(BigInteger.ONE);
    // doing the same for q
    do {
      this.qBasePoint = new BigInteger(q.bitLength(), RandomProvider.SECURE_RANDOM);
    }
    while (this.qBasePoint.compareTo(BigInteger.ONE) <= 0 || this.qBasePoint.compareTo(q) >= 0);
    this.qBasePoint = ModPowAbstraction.modPow(this.qBasePoint, q, qSquared);
    this.qMaxExponent = q.subtract(BigInteger.ONE);
    */

    logger.info("Parameters = " + parametersToString());
  }

  /**
   * Constructs a Paillier algorithm with generated keys.
   * <p>
   * The generated keys {@code p} and {@code q} will have half the given modulus bit length, and the given prime certainty.
   * <p>
   * The probability that the generated keys represent primes will exceed (1 - (1/2)<sup>{@code certainty}</sup>). The execution time of this constructor is
   * proportional to the value of this parameter.
   *
   * @param bitLength
   *          The bit length of the resulting modulus {@code N}.
   * @param certainty
   *          The probability that the new {@code p} and {@code q} represent prime numbers.
   * @throws IllegalArgumentException
   *           If the {@code certainty} is less than the system allowed lower bound.
   */
  public Paillier(int bitLength, int certainty)
  {
    this(bitLength, certainty, -1);
  }

  /**
   * Constructs a Paillier algorithm with generated keys and optionally ensures a certain bit is set in the modulus.
   * <p>
   * The generated keys {@code p} and {@code q} will have half the given modulus bit length, and the given prime certainty.
   * <p>
   * The probability that the generated keys represent primes will exceed (1 - (1/2)<sup>{@code certainty}</sup>). The execution time of this constructor is
   * proportional to the value of this parameter.
   * <p>
   * When ensureBitSet > -1 the value of bit "{@code ensureBitSet}" in modulus {@code N} will be set.
   *
   * @param bitLength
   *          The bit length of the resulting modulus {@code N}.
   * @param certainty
   *          The probability that the new {@code p} and {@code q} represent prime numbers.
   * @param ensureBitSet
   *          index of bit in {@code N} to ensure is set.
   * @throws IllegalArgumentException
   *           If the {@code certainty} is less than the system allowed lower bound, or the index of {@code ensureBitSet} is greater than the {@code bitLength}.
   */
  public Paillier(int bitLength, int certainty, int ensureBitSet)
  {
    int systemPrimeCertainty = SystemConfiguration.getIntProperty("pir.primeCertainty", 128);
    if (certainty < systemPrimeCertainty)
    {
      throw new IllegalArgumentException("Input certainty = " + certainty + " is less than allowed system lower bound = " + systemPrimeCertainty);
    }
    if (ensureBitSet >= bitLength)
    {
      throw new IllegalArgumentException("ensureBitSet = " + ensureBitSet + " must be less than bitLengthInput = " + bitLength);
    }
    this.bitLength = bitLength;
    generateKeys(bitLength, certainty, ensureBitSet);
    setDerivativeElements();

    logger.info("Parameters = " + parametersToString());
  }

  /**
   * Returns the value of the large prime {@code p}.
   *
   * @return p.
   */
  public BigInteger getP()
  {
    return p;
  }

  /**
   * Returns the value of the large prime {@code q}.
   *
   * @return q.
   */
  public BigInteger getQ()
  {
    return q;
  }

  /**
   * Returns the RSA modulus value {@code N}.
   *
   * @return N, the product of {@code p} and {@code q}.
   */
  public BigInteger getN()
  {
    return N;
  }

  /**
   * Returns the generator for the mod {@code p}<sup>2</sup> subgroup used for fast query generation.
   *
   * @return the generator for the mod {@code p}<sup>2</sup> subgroup.
   */
  public BigInteger getPBasePoint()
  {
    return pBasePoint;
  }

  /**
   * Returns one plus the maximum exponent used for the mod {@code p}<sup>2</sup> base point used for fast query generation.
   *
   * @return one plus the maximum exponent used for the mod {@code p}<sup>2</sup> base point.
   */
  public BigInteger getPMaxExponent()
  {
    return pMaxExponent;
  }

  /**
   * Returns the generator for the mod {@code q}<sup>2</sup> subgroup used for fast query generation.
   *
   * @return the generator for the mod {@code q}<sup>2</sup> subgroup.
   */
  public BigInteger getQBasePoint()
  {
    return qBasePoint;
  }

  /**
   * Returns one plus the maximum exponent used for the mod {@code q}<sup>2</sup> base point used for fast query generation.
   *
   * @return one plus the maximum exponent used for the mod {@code q}<sup>2</sup> base point.
   */
  public BigInteger getQMaxExponent()
  {
    return qMaxExponent;
  }

  /**
   * Returns the value of {@code N}<sup>2</sup>.
   *
   * @return N squared.
   */
  public BigInteger getNSquared()
  {
    return NSquared;
  }

  /**
   * Returns the bit length of the modulus {@code N}.
   *
   * @return the bit length, as an integer.
   */
  public int getBitLength()
  {
    return bitLength;
  }

  private void generateKeys(int bitLength, int certainty, final int ensureBitSet)
  {
    getKeys(bitLength, certainty);

    if (ensureBitSet > -1)
    {
      while (!N.testBit(ensureBitSet))
      {
        logger.info("testBit false\n N = " + N.toString(2));
        getKeys(bitLength, certainty);
      }
      logger.info("testBit true\n N = " + N.toString(2));
    }
  }

  private void getKeys(int bitLength, int certainty)
  {
    if (bitLength >= 1024)
    {
      // prime generation is not expected to fail, but if it does we keep trying until it succeeds
      BigInteger[] pq;
      while (true)
      {
	try
	{
	  pq = PrimeGenerator.getPrimePairWithAuxiliaryPrimes(bitLength, certainty, RandomProvider.SECURE_RANDOM);
	  p = pq[0];
	  BigInteger p1 = pq[1];
	  q = pq[2];
	  BigInteger q1 = pq[3];
	  BigInteger[] pBasePointAndMaxExponent = calcBasePointAndExponentWithAuxiliaryPrime(p, p1);
	  pBasePoint = pBasePointAndMaxExponent[0];
	  pMaxExponent = pBasePointAndMaxExponent[1];
	  BigInteger[] qBasePointAndMaxExponent = calcBasePointAndExponentWithAuxiliaryPrime(q, q1);
	  qBasePoint = qBasePointAndMaxExponent[0];
	  qMaxExponent = qBasePointAndMaxExponent[1];
	  break;
	}
	catch (PIRException e) { }
      }
    }
    else
    {
      // Generate the primes
      BigInteger[] pq = PrimeGenerator.getPrimePair(bitLength, certainty, RandomProvider.SECURE_RANDOM);
      p = pq[0];
      q = pq[1];
      setGenericBasePointsAndExponents();
    }
    N = p.multiply(q);
  }

  private void setDerivativeElements()
  {
    NSquared = N.multiply(N);
    pSquared = p.multiply(p);
    qSquared = q.multiply(q);
    crtNSquared = new ChineseRemainder(pSquared, qSquared);
    crtN = new ChineseRemainder(p, q);
    pMinusOne = p.subtract(BigInteger.ONE);
    qMinusOne = q.subtract(BigInteger.ONE);
    wp = p.subtract(q).modInverse(p);
    wq = q.subtract(p).modInverse(q);
  }

  /**
   * Called to initialize the Paillier object's base points and
   * maximum exponents for the order p-1 subgroup of (Z/p^2Z)* and the
   * order q-1 subgroup of (Z/p^2Z)*.  This function is called during
   * Paillier object construction in the case when auxiliary primes
   * are not being used.
   *
   * Each of the two base points is generated by raising a random
   * value to the p-th or q-th power and does not necessarily generate
   * the entire respective subgroup.  The maximum exponents are set to
   * p-1 and q-1.
   */
  private void setGenericBasePointsAndExponents()
  {
    BigInteger pSquared = this.p.multiply(this.p);
    BigInteger qSquared = this.q.multiply(this.q);

    // setting base point to (rand)^p mod p^2
    // this depends only on rand mod p so we can pick rand < p
    do {
      this.pBasePoint = new BigInteger(this.p.bitLength(), RandomProvider.SECURE_RANDOM);
    }
    while (this.pBasePoint.compareTo(BigInteger.ONE) <= 0 || this.pBasePoint.compareTo(this.p) >= 0);
    this.pBasePoint = ModPowAbstraction.modPow(this.pBasePoint, this.p, pSquared);
    this.pMaxExponent = this.p.subtract(BigInteger.ONE);
    // doing the same for q
    do {
      this.qBasePoint = new BigInteger(this.q.bitLength(), RandomProvider.SECURE_RANDOM);
    }
    while (this.qBasePoint.compareTo(BigInteger.ONE) <= 0 || this.qBasePoint.compareTo(this.q) >= 0);
    this.qBasePoint = ModPowAbstraction.modPow(this.qBasePoint, this.q, qSquared);
    this.qMaxExponent = this.q.subtract(BigInteger.ONE);
  }

  /**
   * Returns an ordered pair consisting of a choice of basepoint and
   * maximum exponent for either the order p1 subgroup of (Z/p^2Z)* or
   * the order q1 subgroup of (Z/p^2Z)*.  This function is called
   * during Paillier object construction in the case when auxiliary
   * primes are also generated.
   *
   * The base point is generated by raising a random value to the p-th
   * or q-th power; it is then checked that the base point has order
   * divisible by p1 or p2.  The base points do not necessarily
   * generate the entire respective subgroup.  The maximum exponent is
   * set to the value of the auxiliary prime p1.
   *
   * @return array whose first element is the base point and the
   * second element is the maximum exponent
   */
  private static BigInteger[] calcBasePointAndExponentWithAuxiliaryPrime(BigInteger p, BigInteger p1)
  {
    // checking p1 divides p - 1 and saving (p - 1)/p1 for later
    BigInteger[] divmod = p.subtract(BigInteger.ONE).divideAndRemainder(p1);
    BigInteger div = divmod[0];
    BigInteger rem = divmod[1];
    if (rem.compareTo(BigInteger.ZERO) != 0)
    {
      throw new IllegalArgumentException("auxiliary prime fails to divide prime minus one");
    }
    BigInteger pSquared = p.multiply(p);
    // exponent and random base point for p^2
    BigInteger maxExponent = p1;
    while (true)
    {
      BigInteger basePoint = new BigInteger(p.bitLength(), RandomProvider.SECURE_RANDOM);
      if (basePoint.compareTo(BigInteger.ONE) <= 0 || basePoint.compareTo(p) >= 0)
      {
	continue;
      }
      basePoint = ModPowAbstraction.modPow(basePoint, p, pSquared);
      BigInteger tmp = ModPowAbstraction.modPow(basePoint, div, pSquared);  // base^((p-1)/p1)
      if (tmp.compareTo(BigInteger.ONE) == 0)
      {
	continue;
      }
      return new BigInteger[] {basePoint, maxExponent};
    }
  }

  /**
   * Returns an encryption of a given message.
   * 
   * @param m
   *          the value to be encrypted.
   * @return the encrypted value
   * @throws PIRException
   *           If {@code m} is not less than @{code N}.
   */
  public BigInteger encrypt(BigInteger m) throws PIRException
  {
    // Generate a random value in (Z/NZ)*.  We generate the mod p and mod q portions separately.
    BigInteger r1 = (new BigInteger(p.bitLength(), RandomProvider.SECURE_RANDOM)).mod(p);
    while (r1.equals(BigInteger.ZERO) || r1.equals(BigInteger.ONE) || r1.mod(p).equals(BigInteger.ZERO) || r1.mod(q).equals(BigInteger.ZERO))
    {
      r1 = (new BigInteger(p.bitLength(), RandomProvider.SECURE_RANDOM)).mod(p);
    }

    BigInteger r2 = (new BigInteger(q.bitLength(), RandomProvider.SECURE_RANDOM)).mod(q);
    while (r2.equals(BigInteger.ZERO) || r2.equals(BigInteger.ONE) || r2.mod(p).equals(BigInteger.ZERO) || r2.mod(q).equals(BigInteger.ZERO))
    {
      r2 = (new BigInteger(q.bitLength(), RandomProvider.SECURE_RANDOM)).mod(q);
    }

    return encrypt(m, r1, r2);
  }

  /**
   * Returns an encryption of a a given message using the given random values.
   *
   * @param m
   *          the value to be encrypted.
   * @param r1
   *          the mod {@code p} part of the random base to use in the Pailler encryption.
   * @param r2
   *          the mod {@code q} part of the random base to use in the Pailler encryption.
   * @return the encrypted value.
   * @throws PIRException
   *           If {@code m} is not less than @{code N}.
   */
  public BigInteger encrypt(BigInteger m, BigInteger r1, BigInteger r2) throws PIRException
  {
    if (m.compareTo(N) >= 0)
    {
      throw new PIRException("m  = " + m.toString(2) + " is greater than or equal to N = " + N.toString(2));
    }

    // E(m) = (1 + mN) * CRT(r1^p mod p^2, r2^q mod q^2)
    BigInteger term1 = (m.multiply(N).add(BigInteger.ONE)).mod(NSquared);
    BigInteger u = ModPowAbstraction.modPow(r1, p, pSquared);
    BigInteger v = ModPowAbstraction.modPow(r2, q, qSquared);
    BigInteger term2 = crtNSquared.combine(u,  v,  NSquared);

    return (term1.multiply(term2)).mod(NSquared);
  }

  /**
   * Returns the plaintext message for a given ciphertext.
   *
   * @param c
   *          an encrypted value.
   * @return the corresponding plaintext value.
   */
  public BigInteger decrypt(BigInteger c)
  {
    // x  = c^(p-1) mod p^2, y  = (x - 1)/p, z  = y  * ((p-1)*q)^-1 mod p
    // x' = c^(q-1) mod q^2, y' = (x'- 1)/q, z' = y' * ((q-1)*p)^-1 mod q
    // d = crt.combine(z, z')
    BigInteger cModPSquared = c.mod(pSquared);
    BigInteger cModQSquared = c.mod(qSquared);
    BigInteger xp = ModPowAbstraction.modPow(cModPSquared, pMinusOne, pSquared);
    BigInteger xq = ModPowAbstraction.modPow(cModQSquared, qMinusOne, qSquared);
    BigInteger yp = (xp.subtract(BigInteger.ONE)).divide(p);
    BigInteger yq = (xq.subtract(BigInteger.ONE)).divide(q);
    BigInteger zp = yp.multiply(wp).mod(p);
    BigInteger zq = yq.multiply(wq).mod(q);
    BigInteger d = crtN.combine(zp,  zq,  N);
    return d;
}

  private String parametersToString()
  {
    return "p = " + p.intValue() + " q = " + q.intValue() + " N = " + N.intValue() + " NSquared = " + NSquared.intValue()
        + " bitLength = " + bitLength;
  }

  @Override public boolean equals(Object o)
  {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    Paillier paillier = (Paillier) o;

    if (bitLength != paillier.bitLength)
      return false;
    if (!p.equals(paillier.p))
      return false;
    if (!q.equals(paillier.q))
      return false;
    if (!N.equals(paillier.N))
      return false;
    if (!NSquared.equals(paillier.NSquared))
      return false;
    return true;

  }

  @Override public int hashCode()
  {
    return Objects.hash(p, q, N, NSquared, bitLength);
  }
}
