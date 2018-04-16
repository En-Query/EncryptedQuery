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
package org.enquery.encryptedquery.utils;

import java.math.BigInteger;

public class MontgomeryReduction
{
  private final BigInteger N;
  private final BigInteger Rmask;
  private final int Rlen;
  private final BigInteger Nprime;
  private final BigInteger montOne;

  public MontgomeryReduction(BigInteger N)
  {
    this.N = N;
    this.Rlen  = N.bitLength();
    BigInteger R = BigInteger.ZERO.setBit(this.Rlen);
    this.Rmask = R.subtract(BigInteger.ONE);
    this.Nprime = N.negate().modInverse(R);
    this.montOne = toMontgomery(BigInteger.ONE);
  }

  private BigInteger REDC(BigInteger x)
  {
    // assume: 0 <= x and x < N
    BigInteger m = x.and(this.Rmask).multiply(Nprime).and(this.Rmask);
    BigInteger t = x.add(m.multiply(N)).shiftRight(Rlen);
    if (t.compareTo(N) >= 0)
    {
      return t.subtract(N);
    }
    return t;
  }

  public BigInteger toMontgomery(BigInteger x)
  {
    if (x.compareTo(BigInteger.ZERO) < 0 || x.compareTo(N) >= 0) 
    {
      throw new IllegalArgumentException("x = " + x + "is not in range");
    }
    return x.shiftLeft(Rlen).mod(N);
  }

  public BigInteger fromMontgomery(BigInteger xm)
  {
    return REDC(xm);
  }

  public BigInteger montMultiply(BigInteger xm, BigInteger ym)
  {
    // assume: 0 <= xm < N and 0 <= ym < N
    return REDC(xm.multiply(ym));
  }

  // slow -- for testing only
  public BigInteger montExp(BigInteger xm, BigInteger e)
  {
    BigInteger prod = montOne;
    int elen = e.bitLength();
    for (int i=0; i<elen; i++)
    {
      if (e.testBit(i))
      {
	prod = montMultiply(prod, xm);
      }
      if (i < elen-1)
      {
	xm = montMultiply(xm, xm);
      }
    }
    return prod;
  }

  public BigInteger getMontOne() {
    return montOne;
  }
}
