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
package org.enquery.encryptedquery.schema.data.partitioner;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.enquery.encryptedquery.utils.SystemConfiguration;

/**
 * Partitioner class for IP addresses
 * <p>
 * Assumes an 8-bit partition size
 */
public class IPDataPartitioner implements DataPartitioner
{
  private static final long serialVersionUID = 1L;

  @Override
  public List<BigInteger> toPartitions(Object object, String type)
  {
    List<BigInteger> parts = new ArrayList<>(4);

    String[] octets = ((String) object).split("\\.");
    for (String oct : octets)
    {
      parts.add(new BigInteger(oct));
    }

    return parts;
  }

  @Override
  public Object fromPartitions(List<BigInteger> parts, int partsIndex, String type)
  {
    String element;

    element = parts.get(partsIndex).toString() + "." + parts.get(partsIndex + 1).toString() + "." + parts.get(partsIndex + 2).toString() + "."
        + parts.get(partsIndex + 3).toString();

    return element;
  }

  @Override
  public int getBits(String type)
  {
    return Integer.SIZE;
  }

  @Override
  public List<BigInteger> getPaddedPartitions(String type)
  {
    return Arrays.asList(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
  }

  /**
   * Create partitions for an array of the same type of elements - used when a data value field is an array and we wish to encode these into the return value
   */
  @Override
  public List<BigInteger> arrayToPartitions(List<?> elementList, String type)
  {
    List<BigInteger> parts = new ArrayList<>();

    int numArrayElementsToReturn = SystemConfiguration.getIntProperty("pir.numReturnArrayElements", 1);
    for (int i = 0; i < numArrayElementsToReturn; ++i)
    {
      if (elementList.size() > i) // we may have an element with a list rep that has fewer than numArrayElementsToReturn elements
      {
        parts.addAll(toPartitions(elementList.get(i), type));
      }
      else
      // pad with encryptions of zero
      {
        parts.addAll(getPaddedPartitions(type));
      }
    }
    return parts;
  }

  @Override
  public int getNumPartitions(String type)
  {
    return 4;
  }

}
