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
 */
package org.enquery.encryptedquery.responder.wideskies.common;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.utils.MontgomeryReduction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;


public class ComputeEncryptedColumnYao implements ComputeEncryptedColumn
{
  private final Boolean useMontgomery;

  private static final Logger logger = LoggerFactory.getLogger(ComputeEncryptedColumnYao.class);
  private final Map<Integer,BigInteger> queryElements;
  private final BigInteger NSquared;
  private final int b;
  private final BigInteger[] hs;
  private final MontgomeryReduction mont;

  public ComputeEncryptedColumnYao(Map<Integer,BigInteger> queryElements, BigInteger NSquared, int dataPartitionBitSize, boolean useMontgomery)
  {
    this.queryElements = queryElements;
    this.NSquared = NSquared;
    this.b = dataPartitionBitSize;
    this.useMontgomery = useMontgomery;
    this.hs = new BigInteger[1<<b];
    if (useMontgomery)
    {
      mont = new MontgomeryReduction(NSquared);
    }
    else
    {
      mont = null;
    }
  }

  public void insertDataPart(int rowIndex, BigInteger part)
  {
    // NOTE: assuming part < 2**b
    // TODO: handle Montgomery case
    int partInt = part.intValue();
    if (0 != partInt)
    {
      BigInteger queryElement = queryElements.get(rowIndex);
      if (hs[partInt] != null)
      {
	if (useMontgomery) {
	  hs[partInt] = mont.montMultiply(hs[partInt], queryElement);
	} else {
	  hs[partInt] = hs[partInt].multiply(queryElement).mod(NSquared);
	}
      } else {
	hs[partInt] = queryElement;
      }
    }
  }

  public void insertDataPart(BigInteger queryElement, BigInteger part)
  {
    int partInt = part.intValue();
    if (0 != partInt)
    {
      if (hs[partInt] != null)
      {
	if (useMontgomery) {
	  hs[partInt] = mont.montMultiply(hs[partInt], queryElement);
	} else {
	  hs[partInt] = hs[partInt].multiply(queryElement).mod(NSquared);
	}
      } else {
	hs[partInt] = queryElement;
      }
    }
  }

  public BigInteger computeColumnAndClearData()
  {
    if (useMontgomery) {
      return computeColumnAndClearDataMontgomery();
    }      

    BigInteger out = BigInteger.ONE;
    BigInteger a = BigInteger.ONE;
    for (int x = (1<<b)-1; x > 0; x--)
    {
      if (hs[x] != null) {
	a = a.multiply(hs[x]).mod(NSquared);
      }
      out = out.multiply(a).mod(NSquared);
    }
    Arrays.fill(hs, null);
    return out;
  }

  private BigInteger computeColumnAndClearDataMontgomery()
  {
    // TODO: handle Montgomery case:
    BigInteger out = mont.getMontOne();
    BigInteger a = mont.getMontOne();
    for (int x = (1<<b)-1; x > 0; x--)
    {
      if (hs[x] != null) {
	a = mont.montMultiply(a,hs[x]);
      }
      out = mont.montMultiply(out, a);
    }
    Arrays.fill(hs, null);
    return out;
  }
  
  public void clearData()
  {
	Arrays.fill(hs,  null);
  }
  
  public void free()
  {
  }
}
