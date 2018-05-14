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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComputeEncryptedColumnFactory
{
  private static final Logger logger = LoggerFactory.getLogger(ComputeEncryptedColumnFactory.class);
  
  public static final String M_BASIC = "basic";
  public static final String M_YAO = "yao";
  public static final String M_YAOJNI = "yaojni";
  public static final String M_DEROOIJ = "derooij";
  public static final String M_DEROOIJJNI = "derooijjni";
  public static final String[] METHODS = { M_BASIC, M_YAO, M_DEROOIJ, M_YAOJNI, M_DEROOIJJNI }; 

  public static ComputeEncryptedColumn getComputeEncryptedColumnMethod(String method, Map<Integer,BigInteger> queryElements, BigInteger NSquared, int maxRowIndex, int dataPartitionBitSize)
  {
	validateParameters(method, maxRowIndex, dataPartitionBitSize);
	if (method.equals(M_BASIC))
	{
	  return new ComputeEncryptedColumnBasic(queryElements, NSquared);
	}
	else if (method.equals(M_YAO))
	{
	  return new ComputeEncryptedColumnYao(queryElements, NSquared, dataPartitionBitSize, false);
	}
	else if (method.equals(M_YAOJNI))
	{
	  return new ComputeEncryptedColumnYaoJNI(queryElements, NSquared, maxRowIndex, dataPartitionBitSize);
	}
	else if (method.equals(M_DEROOIJ))
	{
	  return new ComputeEncryptedColumnDeRooij(queryElements, NSquared);
	}
	else if (method.equals(M_DEROOIJJNI))
	{
	  return new ComputeEncryptedColumnDeRooijJNI(queryElements, NSquared, maxRowIndex);
	}
	else
	{
	  throw new IllegalArgumentException("unrecognized ComputeEncryptedColumn method specified: \"" + method + "\"");
	}
  }

  public static void validateParameters(String method, int maxRowIndex, int dataPartitionBitSize)
  {
    if (-1 == Arrays.asList(METHODS).indexOf(method))
    {
      throw new IllegalArgumentException("unrecognized ComputeEncryptedColumn method specified: \"" + method + "\"");
    }

    if (method.equals(M_YAO)) {
      ComputeEncryptedColumnYao.validateParameters(maxRowIndex, dataPartitionBitSize);
	}
    else if (method.equals(M_YAOJNI)) {
      ComputeEncryptedColumnYaoJNI.validateParameters(maxRowIndex, dataPartitionBitSize);
    }
    else if (method.equals(M_DEROOIJJNI))
    {
      ComputeEncryptedColumnDeRooijJNI.validateParameters(maxRowIndex, dataPartitionBitSize);
    }
  }
}
