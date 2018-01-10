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

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.annotations.Expose;

import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.serialization.Storable;

/**
 * Class to hold the information necessary for the PIR querier to perform decryption
 */
public class Querier implements Serializable, Storable
{
  private static final long serialVersionUID = 1L;

  public static final long querierSerialVersionUID = 1L;

  @Expose
  public final long querierVersion = querierSerialVersionUID;

  @Expose
  private Query query = null; // contains the query vectors and functionality

  @Expose
  private Paillier paillier = null; // Paillier encryption functionality

  @Expose
  private List<String> selectors = null; // selectors

  // map to check the embedded selectors in the results for false positives;
  // if the selector is a fixed size < 32 bits, it is included as is
  // if the selector is of variable lengths
  @Expose
  private Map<Integer,String> embedSelectorMap = null;

  @Override public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (o == null || getClass() != o.getClass())
    {
      return false;
    }

    Querier querier = (Querier) o;

    if (!query.equals(querier.query))
    {
      return false;
    }
    if (!paillier.equals(querier.paillier))
    {
      return false;
    }
    if (!selectors.equals(querier.selectors))
    {
      return false;
    }
    return embedSelectorMap != null ? embedSelectorMap.equals(querier.embedSelectorMap) : querier.embedSelectorMap == null;
  }

  @Override public int hashCode()
  {
    return Objects.hash(query, paillier, selectors, embedSelectorMap);
  }

  public Querier(List<String> selectorsInput, Paillier paillierInput, Query queryInput, Map<Integer,String> embedSelectorMapInput)
  {
    selectors = selectorsInput;

    paillier = paillierInput;

    query = queryInput;

    embedSelectorMap = embedSelectorMapInput;
  }

  public Query getQuery()
  {
    return query;
  }

  public Paillier getPaillier()
  {
    return paillier;
  }

  public List<String> getSelectors()
  {
    return selectors;
  }

  public Map<Integer,String> getEmbedSelectorMap()
  {
    return embedSelectorMap;
  }
}
