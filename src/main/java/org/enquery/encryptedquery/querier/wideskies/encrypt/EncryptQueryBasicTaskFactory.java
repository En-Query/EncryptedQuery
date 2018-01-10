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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runnable class for multithreaded PIR query generation
 */
class EncryptQueryBasicTask implements EncryptQueryTask
{
  private static final Logger logger = LoggerFactory.getLogger(EncryptQueryBasicTask.class);

  private final int dataPartitionBitSize;
  private final int start; // start of computing range for the runnable
  private final int stop; // stop, inclusive, of the computing range for the runnable

  private final Paillier paillier;
  private final Map<Integer,Integer> selectorQueryVecMapping;

  public EncryptQueryBasicTask(int dataPartitionBitSize, Paillier paillier, Map<Integer,Integer> selectorQueryVecMapping, int start, int stop)
  {
    this.dataPartitionBitSize = dataPartitionBitSize;
    this.paillier = paillier;
    this.selectorQueryVecMapping = selectorQueryVecMapping;
    this.start = start;
    this.stop = stop;
    logger.debug("created task with start = " + start + " stop = " + stop);
  }

  @Override
  public SortedMap<Integer,BigInteger> call() throws PIRException
  {
    logger.debug("running task with start = " + start + " stop = " + stop);

    // holds the ordered encrypted values to pull after thread computation is complete
    SortedMap<Integer,BigInteger> encryptedValues = new TreeMap<>();
    for (int i = start; i <= stop; i++)
    {
      Integer selectorNum = selectorQueryVecMapping.get(i);
      BigInteger valToEnc = (selectorNum == null) ? BigInteger.ZERO : (BigInteger.valueOf(2)).pow(selectorNum * dataPartitionBitSize);
      BigInteger encVal = paillier.encrypt(valToEnc);
      encryptedValues.put(i, encVal);
      logger.debug("selectorNum = " + selectorNum + " valToEnc = " + valToEnc + " encVal = " + encVal);
    }

    return encryptedValues;
  }
}

/**
 * Factory class for creating runnables for multithreaded PIR query
 * generation using the basic method.
 */
public class EncryptQueryBasicTaskFactory implements EncryptQueryTaskFactory
{
  private static final Logger logger = LoggerFactory.getLogger(EncryptQueryBasicTaskFactory.class);
  private final int dataPartitionBitSize;
  private final Paillier paillier;
  private final Map<Integer,Integer> selectorQueryVecMapping;

  public EncryptQueryBasicTaskFactory(int dataPartitionBitSize, Paillier paillier, Map<Integer,Integer> selectorQueryVecMapping)
  {
    logger.info("initializing EncryptQueryBasicTaskFactory instance");
    this.dataPartitionBitSize = dataPartitionBitSize;
    this.paillier = paillier;
    this.selectorQueryVecMapping = selectorQueryVecMapping;
  }

  public EncryptQueryTask createTask(int start, int stop)
  {
    return new EncryptQueryBasicTask(dataPartitionBitSize, paillier, selectorQueryVecMapping, start, stop);
  }
}
