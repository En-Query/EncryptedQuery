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
import org.enquery.encryptedquery.responder.wideskies.ResponderProps;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;


public class ComputeEncryptedColumnYaoJNI implements ComputeEncryptedColumn
{
  static
  {
    String libraryBaseName = SystemConfiguration.getProperty(ResponderProps.RESPONDERJNILIBBASENAME);
    System.loadLibrary(libraryBaseName);
  }
  private long hContext;

  private static final Logger logger = LoggerFactory.getLogger(ComputeEncryptedColumnYaoJNI.class);
  //private final Map<Integer,BigInteger> queryElements;

  private native long yaoNew(byte[] NSquaredBytes, int maxRowIndex, int b);
  private native void yaoSetQueryElement(long hContext, int rowIndex, byte[] queryElementBytes);
  private native void yaoInsertDataPart(long hContext, int rowIndex, int part);
  private native void yaoInsertDataPart2(long hContext, byte[] queryElementBytes, int part);
  private native byte[] yaoComputeColumnAndClearData(long hContext);
  private native void yaoClearData(long hContext);
  private native void yaoDelete(long hContext);

  public ComputeEncryptedColumnYaoJNI(Map<Integer,BigInteger> queryElements, BigInteger NSquared, int maxRowIndex, int dataPartitionBitSize)
  {
    logger.debug("XXX this = {} constructor", this);
    this.hContext = yaoNew(NSquared.toByteArray(), maxRowIndex, dataPartitionBitSize);  // TODO: when to clean up?
    if (0 == this.hContext)
    {
      throw new NullPointerException("failed to allocate context from native code");
    }
    //this.queryElements = queryElements;
    if (queryElements != null)
    {
      for (int rowIndex = 0; rowIndex < maxRowIndex; rowIndex++)
      {
        yaoSetQueryElement(this.hContext, rowIndex, queryElements.get(rowIndex).toByteArray());
      }
    }
  }

  public void insertDataPart(int rowIndex, BigInteger part)
  {
    //insertDataPart(queryElements.get(rowIndex), part);
    yaoInsertDataPart(hContext, rowIndex, part.intValue());
  }

  public void insertDataPart(BigInteger queryElement, BigInteger part)
  {
    yaoInsertDataPart2(hContext, queryElement.toByteArray(), part.intValue());
    //yaoInsertDataPart2(hContext, null, part.intValue()); // XXX
  }

  public BigInteger computeColumnAndClearData()
  {
    byte[] bytes = yaoComputeColumnAndClearData(hContext);
    return new BigInteger(1, bytes);
  }

  public void clearData()
  {
    logger.debug("XXX this = {} clearData()", this);
    yaoClearData(hContext);
  }

  // TODO: how to have this done automatically on GC?
  public void free()
  {
    logger.debug("XXX this = {} free()", this);
    yaoDelete(hContext);
    hContext = 0;
  }
}
