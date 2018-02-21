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

import org.apache.commons.codec.binary.Hex;
import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.querier.wideskies.Querier;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.schema.data.DataSchema;
import org.enquery.encryptedquery.schema.data.DataSchemaRegistry;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Class to perform PIR encryption
 */
public class EncryptQuery
{
  private static final Logger logger = LoggerFactory.getLogger(EncryptQuery.class);

  // Available methods for query generation.
  public static final String DEFAULT = "default";
  public static final String FAST = "fast";
  public static final String FASTWITHJNI = "fastwithjni";
  public static final String[] METHODS = { DEFAULT, FAST, FASTWITHJNI };

  // Method to use for query generation.
  private final String method;

  // Contains basic query information.
  private final QueryInfo queryInfo;

  // Selectors for this query.
  private final List<String> selectors;

  // Paillier encryption functionality.
  private final Paillier paillier;

  /**
   * Constructs a query encryptor using the given query information, selectors, and Paillier cryptosystem.
   *
   * @param queryInfo Fundamental information about the query.
   * @param selectors the list of selectors for this query.
   * @param paillier  the Paillier cryptosystem to use.
   */
  public EncryptQuery(QueryInfo queryInfo, List<String> selectors, Paillier paillier)
  {
    this.method = SystemConfiguration.getProperty("pir.encryptQueryMethod");
    this.queryInfo = queryInfo;
    this.selectors = selectors;
    this.paillier = paillier;
  }

  /**
   * Encrypts the query described by the query information using Paillier encryption.
   * <p>
   * The encryption builds a <code>Querier</code> object, calculating and setting the query vectors.
   * <p>
   * Uses the system configured number of threads to conduct the encryption, or a single thread if the configuration has not been set.
   *
   * @return The querier containing the query, and all information required to perform decryption.
   * @throws InterruptedException If the task was interrupted during encryption.
   * @throws PIRException         If a problem occurs performing the encryption.
   */
  public Querier encrypt() throws InterruptedException, PIRException
  {
    int numThreads = SystemConfiguration.getIntProperty("numThreads", 1);
    return encrypt(numThreads);
  }

  /**
   * Encrypts the query described by the query information using Paillier encryption using the given number of threads.
   * <p>
   * The encryption builds a <code>Querier</code> object, calculating and setting the query vectors.
   * <p>
   * If we have hash collisions over our selector set, we will append integers to the key starting with 0 until we no longer have collisions.
   * <p>
   * For encrypted query vector E = E_0, ..., E_{(2^hashBitSize)-1}:
   * <p>
   * E_i = 2^{j*dataPartitionBitSize} if i = H_k(selector_j) 0 otherwise
   *
   * @param numThreads the number of threads to use when performing the encryption.
   * @return The querier containing the query, and all information required to perform decryption.
   * @throws InterruptedException If the task was interrupted during encryption.
   * @throws PIRException         If a problem occurs performing the encryption.
   */
  public Querier encrypt(int numThreads) throws InterruptedException, PIRException
  {
    // Determine the query vector mappings for the selectors; vecPosition -> selectorNum
    Map<Integer,Integer> selectorQueryVecMapping = computeSelectorQueryVecMap();

    // Form the embedSelectorMap
    // Map to check the embedded selectors in the results for false positives;
    // if the selector is a fixed size < 32 bits, it is included as is
    // if the selector is of variable lengths
    Map<Integer,String> embedSelectorMap = computeEmbeddedSelectorMap();

    SortedMap<Integer,BigInteger> queryElements;
    if (numThreads == 1)
    {
      queryElements = serialEncrypt(selectorQueryVecMapping);
      logger.info("Completed serial creation of encrypted query vectors");
    }
    else
    {
      queryElements = parallelEncrypt(Math.max(2, numThreads), selectorQueryVecMapping);
      logger.info("Completed parallel creation of encrypted query vectors");
    }

    Query query = new Query(queryInfo, paillier.getN(), queryElements);

    // Generate the expTable in Query, if we are using it and if
    // useHDFSExpLookupTable is false -- if we are generating it as standalone and not on the cluster
    if (queryInfo.useExpLookupTable() && !queryInfo.useHDFSExpLookupTable())
    {
      logger.info("Starting expTable generation");
      query.generateExpTable();
    }

    // Return the Querier object.
    return new Querier(selectors, paillier, query, embedSelectorMap);
  }

  /**
   * Use this method to get a securely generated, random string of 2*numBytes length
   *
   * @param numBytes How many bytes of random data to return.
   * @return Random hex string of 2*numBytes length
   */
  private static String getRandByteString(int numBytes)
  {
    byte[] randomData = new byte[numBytes];
    RandomProvider.SECURE_RANDOM.nextBytes(randomData);
    return Hex.encodeHexString(randomData);
  }

  /**
   * Helper class to contain both a newly generated hash key and the selector hash to index mapping
   */
  public static class KeyAndSelectorMapping {
    private final String hashKey;
    private final Map<Integer,Integer> selectorQueryVecMapping;
    public KeyAndSelectorMapping(String hashKey,  Map<Integer,Integer> selectorQueryVecMapping)
    {
      this.hashKey = hashKey;
      this.selectorQueryVecMapping = selectorQueryVecMapping;
    }
    public String getHashKey() { return this.hashKey; }
    public Map<Integer,Integer> getSelectorQueryVecMapping() { return this.selectorQueryVecMapping; }
  }

  private static KeyAndSelectorMapping computeSelectorQueryVecMap(int hashBitSize, List<String> selectors)
  {
    String hashKey = getRandByteString(10);
    int numSelectors = selectors.size();
    Map<Integer,Integer> selectorQueryVecMapping = new HashMap<>(numSelectors);

    int attempts = 0;
    int maxAttempts = 3;
    for (int index = 0; index < numSelectors; index++)
    {
      String selector = selectors.get(index);
      int hash = KeyedHash.hash(hashKey, hashBitSize, selector);

      // All keyed hashes of the selectors must be unique
      if (selectorQueryVecMapping.put(hash, index) == null)
      {
        // The hash is unique
        logger.debug("index = " + index + "selector = " + selector + " hash = " + hash);
      }
      else
      {
        // Hash collision.  Each selectors hash needs to be unique.  If not then try a new key to calculate the hash with
    	// Try this 3 times then start logging and skipping selectors that are causing a collision
        if (attempts < maxAttempts ) {
    	    selectorQueryVecMapping.clear();
            hashKey = getRandByteString(10);
            logger.info("Attempt " + attempts + " resulted in a collision for index = " + index + "selector = " + selector + " hash collision = " + hash + " new key = " + hashKey);
            index = -1;
            attempts++;
        } else {
            logger.info("Max Attempts reached ( " + attempts + " ) skipping selector = " + selector + " hash collision = " + hash + " new key = " + hashKey + " Index = " + index);
        }
      }
    }

    // return hashKey and mapping
    return new KeyAndSelectorMapping(hashKey, selectorQueryVecMapping);
  }

  private Map<Integer,Integer> computeSelectorQueryVecMap()
  {
    KeyAndSelectorMapping km = computeSelectorQueryVecMap(queryInfo.getHashBitSize(), selectors);
    String hashKey = km.getHashKey();
    Map<Integer,Integer> selectorQueryVecMapping = km.getSelectorQueryVecMapping();
    queryInfo.setHashKey(hashKey);
    return selectorQueryVecMapping;
  }

  private Map<Integer,String> computeEmbeddedSelectorMap() throws PIRException
  {
    QuerySchema qSchema = QuerySchemaRegistry.get(queryInfo.getQueryType());
    String selectorName = qSchema.getSelectorName();
    DataSchema dSchema = DataSchemaRegistry.get(qSchema.getDataSchemaName());
    String type = dSchema.getElementType(selectorName);

    Map<Integer,String> embedSelectorMap = new HashMap<>(selectors.size());

    int sNum = 0;
    for (String selector : selectors)
    {
      String embeddedSelector = QueryUtils.getEmbeddedSelector(selector, type, dSchema.getPartitionerForElement(selectorName));
      embedSelectorMap.put(sNum, embeddedSelector);
      sNum += 1;
    }

    return embedSelectorMap;
  }

  /*
   * Functionality for single-threaded query encryption.  Made static for easier testing.
   */
  private static SortedMap<Integer,BigInteger> serialEncrypt(Map<Integer,Integer> selectorQueryVecMapping, int hashBitSize, int dataPartitionBitSize, Paillier paillier, String method) throws PIRException
  {
    int numElements = 1 << hashBitSize; // 2^hashBitSize

    EncryptQueryTaskFactory factory;
    EncryptQueryTask task;

    if (method.equals(FASTWITHJNI))
    {
      factory = new EncryptQueryFixedBaseWithJNITaskFactory(dataPartitionBitSize, paillier, selectorQueryVecMapping);
    }
    else if (method.equals(FAST))
    {
      factory = new EncryptQueryFixedBaseTaskFactory(dataPartitionBitSize, paillier, selectorQueryVecMapping);
    }
    else
    {
      factory = new EncryptQueryBasicTaskFactory(dataPartitionBitSize, paillier, selectorQueryVecMapping);
    }

    task = factory.createTask(0, numElements - 1);

    return task.call();
  }

  /*
   * Perform the encryption using a single thread, avoiding the overhead of thread management.
   */
  private SortedMap<Integer,BigInteger> serialEncrypt(Map<Integer,Integer> selectorQueryVecMapping) throws PIRException
  {
    return serialEncrypt(selectorQueryVecMapping, queryInfo.getHashBitSize(), queryInfo.getDataPartitionBitSize(), paillier, method);
  }

  /*
   * Functionality for multi-threaded query encryption.  Made static for easier testing.
   */
  private static SortedMap<Integer,BigInteger> parallelEncrypt(int numThreads, Map<Integer,Integer> selectorQueryVecMapping, int hashBitSize, int dataPartitionBitSize, Paillier paillier, String method) throws InterruptedException, PIRException
  {
    // Encrypt and form the query vector
    ExecutorService es = Executors.newCachedThreadPool();
    List<Future<SortedMap<Integer,BigInteger>>> futures = new ArrayList<>(numThreads);
    int numElements = 1 << hashBitSize; // 2^hashBitSize

    // Create factory object to produce tasks for all the threads, and to pre-compute data used by all the threads if necessary
    EncryptQueryTaskFactory factory;
    EncryptQueryTask task;
    if (method.equals(FASTWITHJNI))
    {
      factory = new EncryptQueryFixedBaseWithJNITaskFactory(dataPartitionBitSize, paillier, selectorQueryVecMapping);
    }
    else if (method.equals(FAST))
    {
      factory = new EncryptQueryFixedBaseTaskFactory(dataPartitionBitSize, paillier, selectorQueryVecMapping);
    }
    else
    {
      factory = new EncryptQueryBasicTaskFactory(dataPartitionBitSize, paillier, selectorQueryVecMapping);
    }

    // Split the work across the requested number of threads
    int elementsPerThread = numElements / numThreads;
    for (int i = 0; i < numThreads; ++i)
    {
      // Grab the range for this thread
      int start = i * elementsPerThread;
      int stop = start + elementsPerThread - 1;
      if (i == numThreads - 1)
      {
        stop = numElements - 1;
      }

      // Create the runnable and execute
      task = factory.createTask(start, stop);
      futures.add(es.submit(task));
    }

    // Pull all encrypted elements and add to resultMap
    SortedMap<Integer,BigInteger> queryElements = new TreeMap<>();
    try
    {
      for (Future<SortedMap<Integer,BigInteger>> future : futures)
      {
        queryElements.putAll(future.get(1, TimeUnit.DAYS));
      }
    } catch (TimeoutException | ExecutionException e)
    {
      throw new PIRException("Exception in encryption threads.", e);
    }

    es.shutdown();

    return queryElements;
  }

  /*
   * Performs the encryption with numThreads.
   */
  private SortedMap<Integer,BigInteger> parallelEncrypt(int numThreads, Map<Integer,Integer> selectorQueryVecMapping) throws InterruptedException, PIRException
  {
    return parallelEncrypt(numThreads, selectorQueryVecMapping, queryInfo.getHashBitSize(), queryInfo.getDataPartitionBitSize(), paillier, method);
  }
}
