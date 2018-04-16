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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.utils.MontgomeryReduction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;


class Pair {
  private final Tuple2<Long,BigInteger> p;
  public Pair(Long l, BigInteger b)
  {
    p = new Tuple2(l,b);
  }
  public Long _1() { return p._1(); }
  public BigInteger _2() { return p._2(); }
  public String toString()
  {
    return "(" + _1() + "," + _2() + ")";
  }
}

public class ComputeEncryptedColumnDeRooij implements ComputeEncryptedColumn
{
  private static final Boolean useMontgomery = false;

  private static final Logger logger = LoggerFactory.getLogger(ComputeEncryptedColumnDeRooij.class);
  private final Map<Integer,BigInteger> queryElements;
  private final BigInteger NSquared;
  private final MontgomeryReduction mont;
  public final PriorityQueue<Pair> maxHeap;

  private static Comparator<Pair> pairComparator = new Comparator<Pair>() {
      @Override
      public int compare(Pair pair1, Pair pair2)
      {
	return Long.compare(pair2._1(), pair1._1());
      }
    };

  // TODO: specify initialCapacity?
  public ComputeEncryptedColumnDeRooij(Map<Integer,BigInteger> queryElements, BigInteger NSquared)
  {
    this.queryElements = queryElements;
    this.NSquared = NSquared;
    this.maxHeap = new PriorityQueue<>(1, pairComparator);
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
    long partLong = part.longValue();
    if (0 != partLong)
    {
      Pair pair = new Pair(partLong, queryElements.get(rowIndex));
      maxHeap.add(pair);
    }
  }

  public void insertDataPart(BigInteger queryElement, BigInteger part)
  {
    long partLong = part.longValue();
    if (0 != partLong)
    {
      Pair pair = new Pair(partLong, queryElement);
      maxHeap.add(pair);
    }
  }

  public BigInteger computeColumnAndClearData()
  {
    if (maxHeap.isEmpty())
    {
      if (useMontgomery)
      {
	return mont.getMontOne();
      }
      else
      {
	return BigInteger.ONE;
      }
    }

    Long a;
    BigInteger g;
    Pair pair;
    while (maxHeap.size() > 1)
    {
      Long b;
      BigInteger h;
      pair = maxHeap.poll(); a = pair._1(); g = pair._2();
      pair = maxHeap.poll(); b = pair._1(); h = pair._2();
      long q = Long.divideUnsigned(a.longValue(), b.longValue());
      long r = Long.remainderUnsigned(a.longValue(), b.longValue());
      if (useMontgomery)
      {
	BigInteger power = mont.montExp(g, BigInteger.valueOf(q));
	h = mont.montMultiply(h, power);
      } else {
	BigInteger power = ModPowAbstraction.modPow(g, BigInteger.valueOf(q), NSquared);
	h = h.multiply(power).mod(NSquared);
      }
      maxHeap.add(new Pair(b,h));
      if (r != 0)
      {
	maxHeap.add(new Pair(r,g));
      }
    }

    // maxHeap.size() must be 1
    pair = maxHeap.poll(); a = pair._1(); g = pair._2();
    BigInteger answer;
    if (useMontgomery)
    {
      answer = mont.montExp(g, BigInteger.valueOf(a));
    }
    else
    {
      answer = ModPowAbstraction.modPow(g, BigInteger.valueOf(a), NSquared);
    }
    return answer;
  }

  public void clearData()
  {
    maxHeap.clear();
  }

  public void dumpAndClearHeap()
  {
    System.out.println("dumpAndClearHeap()");
    while (!maxHeap.isEmpty())
    {
      Pair pair = maxHeap.poll();
      System.out.println("pair: " + pair);
    }
    System.out.println("done");
  }

  public void dump()
  {
    System.out.println("dump()");
    Pair[] pairs = maxHeap.toArray(new Pair[0]);
    Arrays.sort(pairs, pairComparator);
    for (Pair pair: pairs)
    {
      System.out.println("pair: " + pair);
    }
    System.out.println("done");
  }

  public static void main(String args[])
  {
    System.out.println("hello, world!");
    HashMap<Integer,BigInteger> queryElements = new HashMap<>();
    ComputeEncryptedColumnDeRooij cec = new ComputeEncryptedColumnDeRooij(queryElements, BigInteger.ONE);
    cec.maxHeap.add(new Pair(Long.valueOf(3), BigInteger.valueOf(0)));
    cec.maxHeap.add(new Pair(Long.valueOf(-2), BigInteger.valueOf(1)));
    cec.maxHeap.add(new Pair(Long.valueOf(4), BigInteger.valueOf(2)));
    System.out.println("cec.size(): " + cec.maxHeap.size());
    cec.dump();
    cec.dump();
    cec.dumpAndClearHeap();
    cec.dump();
    System.out.println("cec.size(): " + cec.maxHeap.size());
  }
}
