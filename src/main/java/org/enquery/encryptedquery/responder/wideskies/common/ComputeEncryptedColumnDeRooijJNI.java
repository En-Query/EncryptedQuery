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
import java.util.Map;

import org.enquery.encryptedquery.responder.wideskies.ResponderProps;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComputeEncryptedColumnDeRooijJNI implements ComputeEncryptedColumn
{
  static
  {
    String libraryBaseName = SystemConfiguration.getProperty(ResponderProps.RESPONDERJNILIBBASENAME);
    System.loadLibrary(libraryBaseName);
  }
  private long hContext;

  private static final Logger logger = LoggerFactory.getLogger(ComputeEncryptedColumnDeRooijJNI.class);
  //private final Map<Integer,BigInteger> queryElements;

  private native long derooijNew(byte[] NSquaredBytes, int maxRowIndex);
  private native void derooijSetQueryElement(long hContext, int rowIndex, byte[] queryElementBytes);
  private native void derooijInsertDataPart(long hContext, int rowIndex, int part);
  private native void derooijInsertDataPart2(long hContext, byte[] queryElementBytes, int part);
  private native byte[] derooijComputeColumnAndClearData(long hContext);
  private native void derooijClearData(long hContext);
  private native void derooijDelete(long hContext);

  public ComputeEncryptedColumnDeRooijJNI(Map<Integer,BigInteger> queryElements, BigInteger NSquared, int maxRowIndex)
  {
    logger.info("XXX this = {} constructor", this);
    this.hContext = derooijNew(NSquared.toByteArray(), maxRowIndex);
    if (0 == this.hContext)
    {
      throw new NullPointerException("failed to allocate context from native code");
    }
    //this.queryElements = queryElements;
    if (queryElements != null)
    {
      for (int rowIndex = 0; rowIndex < maxRowIndex; rowIndex++)
      {
        derooijSetQueryElement(this.hContext, rowIndex, queryElements.get(rowIndex).toByteArray());
      }
    }
  }

  public void insertDataPart(int rowIndex, BigInteger part)
  {
    //insertDataPart(queryElements.get(rowIndex), part);
    derooijInsertDataPart(hContext, rowIndex, part.intValue());
  }

  public void insertDataPart(BigInteger queryElement, BigInteger part)
  {
    derooijInsertDataPart2(hContext, queryElement.toByteArray(), part.intValue());
  }

  public BigInteger computeColumnAndClearData()
  {
    byte[] bytes = derooijComputeColumnAndClearData(hContext);
    return new BigInteger(1, bytes);
  }

  public void clearData()
  {
    logger.info("XXX this = {} clearData()", this);
    derooijClearData(hContext);
  }

  // TODO: how to have this done automatically on GC?
  public void free()
  {
    logger.info("XXX this = {} free()", this);
    derooijDelete(hContext);
    hContext = 0;
  }
}
