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

package org.enquery.encryptedquery.querier.wideskies.decrypt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.querier.wideskies.Querier;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.schema.response.QueryResponseJSON;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to perform PIR decryption
 */
public class DecryptResponse
{
  private static final Logger logger = LoggerFactory.getLogger(DecryptResponse.class);

  private static final BigInteger TWO_BI = BigInteger.valueOf(2);

  private final Response response;

  private final Querier querier;

  public DecryptResponse(Response responseInput, Querier querierInput)
  {
    response = responseInput;
    querier = querierInput;
  }

  public Map<String,List<QueryResponseJSON>> decrypt() throws InterruptedException, PIRException
  {
    int numThreads = SystemConfiguration.getIntProperty("numThreads", 1);
    return decrypt(numThreads);
  }

  /**
   * Method to decrypt the response elements and reconstructs the data elements
   * <p>
   * Each element of response.getResponseElements() is an encrypted column vector E(Y_i)
   * <p>
   * To decrypt and recover data elements:
   * <p>
   * (1) Decrypt E(Y_i) to yield
   * <p>
   * Y_i, where Y_i = \sum_{j = 0}^{numSelectors} 2^{j*dataPartitionBitSize} D_j
   * <p>
   * such that D_j is dataPartitionBitSize-many bits of data corresponding to selector_k for j = H_k(selector_k), for some 0 <= k < numSelectors
   * <p>
   * (2) Reassemble data elements across columns where, hit r for selector_k, D^k_r, is such that
   * <p>
   * D^k_r = D^k_r,0 || D^k_r,1 || ... || D^k_r,(numPartitionsPerDataElement - 1)
   * <p>
   * where D^k_r,l = Y_{r*numPartitionsPerDataElement + l} & (2^{r*numPartitionsPerDataElement} * (2^numBitsPerDataElement - 1))
   *
   */
  public Map<String,List<QueryResponseJSON>> decrypt(int numThreads) throws InterruptedException, PIRException
  {
    Map<String,List<QueryResponseJSON>> resultMap = new HashMap<>(); // selector -> ArrayList of hits

    QueryInfo queryInfo = response.getQueryInfo();

    Paillier paillier = querier.getPaillier();
    List<String> selectors = querier.getSelectors();
    Map<Integer,String> embedSelectorMap = querier.getEmbedSelectorMap();

    // Perform decryption on the encrypted columns
    List<BigInteger> rElements = decryptElements(response.getResponseElements(), paillier);
    logger.debug("rElements.size() = " + rElements.size());

    // Pull the necessary parameters
    int dataPartitionBitSize = queryInfo.getDataPartitionBitSize();

    // Initialize the result map and masks-- removes initialization checks from code below
    Map<String,BigInteger> selectorMaskMap = new HashMap<>();
    int selectorNum = 0;
    for (String selector : selectors)
    {
      resultMap.put(selector, new ArrayList<>());

      // 2^{selectorNum*dataPartitionBitSize}(2^{dataPartitionBitSize} - 1)
      BigInteger mask = TWO_BI.pow(selectorNum * dataPartitionBitSize).multiply((TWO_BI.pow(dataPartitionBitSize).subtract(BigInteger.ONE)));
      logger.debug("selector = " + selector + " mask = " + mask.toString(2));
      selectorMaskMap.put(selector, mask);

      ++selectorNum;
    }

    // Decrypt via Runnables
    ExecutorService es = Executors.newCachedThreadPool();
    if (selectors.size() < numThreads)
    {
      numThreads = selectors.size();
    }
    int elementsPerThread = selectors.size() / numThreads; // Integral division.

    List<Future<Map<String,List<QueryResponseJSON>>>> futures = new ArrayList<>();
    for (int i = 0; i < numThreads; ++i)
    {
      // Grab the range of the thread and create the corresponding partition of selectors
      int start = i * elementsPerThread;
      int stop = start + elementsPerThread - 1;
      if (i == (numThreads - 1))
      {
        stop = selectors.size() - 1;
      }
      TreeMap<Integer,String> selectorsPartition = new TreeMap<>();
      for (int j = start; j <= stop; ++j)
      {
        selectorsPartition.put(j, selectors.get(j));
      }

      // Create the runnable and execute
      DecryptResponseTask<Map<String,List<QueryResponseJSON>>> runDec = new DecryptResponseTask<>(rElements, selectorsPartition, selectorMaskMap,
          queryInfo.clone(), embedSelectorMap);
      futures.add(es.submit(runDec));
    }

    // Pull all decrypted elements and add to resultMap
    try
    {
      for (Future<Map<String,List<QueryResponseJSON>>> future : futures)
      {
        resultMap.putAll(future.get(1, TimeUnit.DAYS));
      }
    } catch (TimeoutException | ExecutionException e)
    {
      throw new PIRException("Exception in decryption threads.", e);
    }

    es.shutdown();

    return resultMap;
  }

  // Method to perform basic decryption of each raw response element - does not
  // extract and reconstruct the data elements
  private List<BigInteger> decryptElements(TreeMap<Integer,BigInteger> elements, Paillier paillier)
  {
    List<BigInteger> decryptedElements = new ArrayList<>();

    for (BigInteger encElement : elements.values())
    {
      decryptedElements.add(paillier.decrypt(encElement));
    }

    return decryptedElements;
  }
}
