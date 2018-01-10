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
package org.enquery.encryptedquery.querier.wideskies;

import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptQuery;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Handles encrypting a query and constructing a {@link Querier} given a {@link EncryptionPropertiesBuilder}.
 *
 */
public class QuerierFactory
{
  private static final Logger logger = LoggerFactory.getLogger(QuerierFactory.class);

  /**
   * Generates a {@link Querier} containing the encrypted query.
   *
   * @param queryIdentifier
   *          A unique identifier for this query.
   * @param selectors
   *          A list of query selectors.
   * @param properties
   *          A list of properties specifying EncryptedQuery configuration options. Use {@link EncryptionPropertiesBuilder} to construct this object.
   * @return The encrypted query.
   * @throws PIRException
   *           If the provided parameters violate one of the constraints of the EncryptedQuery algorithm.
   * @throws InterruptedException
   *           If the encryption process is interrupted.
   */
  public static Querier createQuerier(UUID queryIdentifier, List<String> selectors, Properties properties) throws PIRException, InterruptedException
  {
    if (!QuerierProps.validateQuerierEncryptionProperties(properties))
    {
      throw new PIRException("Invalid encryption properties.");
    }
    int numSelectors = selectors.size();
    int numThreads = Integer.parseInt(properties.getProperty(QuerierProps.NUMTHREADS));
    String queryType = properties.getProperty(QuerierProps.QUERYTYPE);
    int hashBitSize = Integer.parseInt(properties.getProperty(QuerierProps.HASHBITSIZE));
    int bitSet = Integer.parseInt(properties.getProperty(QuerierProps.BITSET));
    int dataPartitionBitSize = Integer.parseInt(properties.getProperty(QuerierProps.DATAPARTITIONSIZE));
    int paillierBitSize = Integer.parseInt(properties.getProperty(QuerierProps.PAILLIERBITSIZE));
    int certainty = Integer.parseInt(properties.getProperty(QuerierProps.CERTAINTY));
    boolean embedSelector = Boolean.valueOf(properties.getProperty(QuerierProps.EMBEDSELECTOR, "false"));
    boolean useMemLookupTable = Boolean.valueOf(properties.getProperty(QuerierProps.USEMEMLOOKUPTABLE, "false"));
    boolean useHDFSLookupTable = Boolean.valueOf(properties.getProperty(QuerierProps.USEHDFSLOOKUPTABLE, "false"));

    // Check to ensure we have a valid queryType
    if (QuerySchemaRegistry.get(queryType) == null)
    {
      String message = "Invalid schema: " + queryType + "; The following schemas are loaded: " + QuerySchemaRegistry.getNames();
      logger.error(message);
      throw new PIRException(message);
    }

    // Enforce dataPartitionBitSize < 32
    // WES updated to allow lager dataPartitionBitSizes.
    // dps size really should not exceed the size of the query data.
    if (dataPartitionBitSize > 3072)
    {
      String message = "dataPartitionBitSize = " + dataPartitionBitSize + "; must be less than 3072";
      logger.error(message);
      throw new PIRException(message);
    }
    
    if (( dataPartitionBitSize % 8 ) != 0 ) {
        String message = "dataPartitionBitSize = " + dataPartitionBitSize + "; must be a multiple of 8";
        logger.error(message);
        throw new PIRException(message);
    }

    // Set the necessary QueryInfo and Paillier objects
    QueryInfo queryInfo = new QueryInfo(queryIdentifier, numSelectors, hashBitSize, dataPartitionBitSize, queryType, useMemLookupTable, embedSelector,
        useHDFSLookupTable);

    if ("true".equals(properties.getProperty(QuerierProps.EMBEDQUERYSCHEMA, "false")))
    {
      queryInfo.addQuerySchema(QuerySchemaRegistry.get(queryType));
    }

    Paillier paillier = new Paillier(paillierBitSize, certainty, bitSet); // throws PIRException if certainty conditions are not satisfied

    // Check the number of selectors to ensure that 2^{numSelector*dataPartitionBitSize} < N
    // For example, if the highest bit is set, the largest value is \floor{paillierBitSize/dataPartitionBitSize}
    int exp = numSelectors * dataPartitionBitSize;
    BigInteger val = (BigInteger.valueOf(2)).pow(exp);
    if (val.compareTo(paillier.getN()) != -1)
    {
      String message = "The number of selectors = " + numSelectors + " must be such that " + "2^{numSelector*dataPartitionBitSize} < N = "
          + paillier.getN().toString(2);
      logger.error(message);
      throw new PIRException(message);

    }

    // Perform the encryption
    EncryptQuery encryptQuery = new EncryptQuery(queryInfo, selectors, paillier);
    return encryptQuery.encrypt(numThreads);
  }
}
