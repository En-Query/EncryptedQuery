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
package org.enquery.encryptedquery.response.wideskies;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;
import java.util.TreeMap;

import com.google.gson.annotations.Expose;

import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.serialization.Storable;

/**
 * Class to hold the encrypted response elements for the PIR query
 * <p>
 * Serialized and returned to the querier for decryption
 */
public class Response implements Serializable, Storable
{
  private static final long serialVersionUID = 1L;

  public static final long responseSerialVersionUID = 1L;

  @Expose
  public final long responseVersion = responseSerialVersionUID;

  @Expose
  private QueryInfo queryInfo = null; // holds all query info

  @Expose
  private TreeMap<Integer,BigInteger> responseElements = null; // encrypted response columns, colNum -> column

  public Response(QueryInfo queryInfoInput)
  {
    queryInfo = queryInfoInput;
    responseElements = new TreeMap<>();
  }

  public TreeMap<Integer,BigInteger> getResponseElements()
  {
    return responseElements;
  }

  public void setResponseElements(TreeMap<Integer,BigInteger> elements)
  {
    responseElements = elements;
  }

  public QueryInfo getQueryInfo()
  {
    return queryInfo;
  }

  public void addElement(int position, BigInteger element)
  {
    responseElements.put(position, element);
  }

  @Override public boolean equals(Object o)
  {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    Response response = (Response) o;

    if (!queryInfo.equals(response.queryInfo))
      return false;
    return responseElements.equals(response.responseElements);

  }

  @Override public int hashCode()
  {
    return Objects.hash(queryInfo, responseElements);
  }
}
