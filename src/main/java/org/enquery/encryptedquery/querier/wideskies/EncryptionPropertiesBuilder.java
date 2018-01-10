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

import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.BITSET;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.CERTAINTY;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.DATAPARTITIONSIZE;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.EMBEDSELECTOR;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.HASHBITSIZE;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.NUMTHREADS;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.PAILLIERBITSIZE;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.QUERYTYPE;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.USEHDFSLOOKUPTABLE;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.USEMEMLOOKUPTABLE;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.setEncryptionDefaults;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.setGeneralDefaults;
import static org.enquery.encryptedquery.querier.wideskies.QuerierProps.validateQuerierEncryptionProperties;

import java.util.Properties;

import org.enquery.encryptedquery.utils.PIRException;



/**
 * Holds the various parameters related to creating a {@link Querier}.
 *
 */
public class EncryptionPropertiesBuilder
{
  private final Properties properties;

  public static EncryptionPropertiesBuilder newBuilder()
  {
    return new EncryptionPropertiesBuilder();
  }

  private EncryptionPropertiesBuilder()
  {
    this.properties = new Properties();

    setGeneralDefaults(properties);
    setEncryptionDefaults(properties);
  }

  public EncryptionPropertiesBuilder numThreads(int numThreads)
  {
    properties.setProperty(NUMTHREADS, String.valueOf(numThreads));
    return this;
  }

  public EncryptionPropertiesBuilder bitSet(int bitSet)
  {
    properties.setProperty(BITSET, String.valueOf(bitSet));
    return this;
  }

  public EncryptionPropertiesBuilder queryType(String queryType)
  {
    properties.setProperty(QUERYTYPE, queryType);
    return this;
  }

  public EncryptionPropertiesBuilder hashBitSize(int hashBitSize)
  {
    properties.setProperty(HASHBITSIZE, String.valueOf(hashBitSize));
    return this;
  }

  public EncryptionPropertiesBuilder dataPartitionBitSize(int dataPartitionBitSize)
  {
    properties.setProperty(DATAPARTITIONSIZE, String.valueOf(dataPartitionBitSize));
    return this;
  }

  public EncryptionPropertiesBuilder paillierBitSize(int paillierBitSize)
  {
    properties.setProperty(PAILLIERBITSIZE, String.valueOf(paillierBitSize));
    return this;
  }

  public EncryptionPropertiesBuilder certainty(int certainty)
  {
    properties.setProperty(CERTAINTY, String.valueOf(certainty));
    return this;
  }

  public EncryptionPropertiesBuilder embedSelector(boolean embedSelector)
  {
    properties.setProperty(EMBEDSELECTOR, String.valueOf(embedSelector));
    return this;
  }

  public EncryptionPropertiesBuilder useMemLookupTable(boolean useMemLookupTable)
  {
    properties.setProperty(USEMEMLOOKUPTABLE, String.valueOf(useMemLookupTable));
    return this;
  }

  public EncryptionPropertiesBuilder useHDFSLookupTable(boolean useHDFSLookupTable)
  {
    properties.setProperty(USEHDFSLOOKUPTABLE, String.valueOf(useHDFSLookupTable));
    return this;
  }

  public Properties build() throws PIRException
  {
    if (!validateQuerierEncryptionProperties(properties))
    {
      throw new PIRException("Encryption properties not valid. See log for details.");
    }
    return properties;
  }

}
