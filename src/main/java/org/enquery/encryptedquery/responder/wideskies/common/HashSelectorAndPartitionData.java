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
package org.enquery.encryptedquery.responder.wideskies.common;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.io.MapWritable;
import org.enquery.encryptedquery.inputformat.hadoop.BytesArrayWritable;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.schema.data.DataSchema;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;

/**
 * Given a MapWritable or JSON formatted dataElement, this class gives the common functionality to extract the selector by queryType from each dataElement,
 * perform a keyed hash of the selector, extract the partitions of the dataElement, and outputs {@code <hash(selector), dataPartitions>}
 */
public class HashSelectorAndPartitionData
{
  private static final Logger logger = LoggerFactory.getLogger(HashSelectorAndPartitionData.class);

//  public static Tuple2<Integer,List<BigInteger>> hashSelectorAndFormPartitionsBigInteger(MapWritable dataElement, QuerySchema qSchema, DataSchema dSchema,
//      QueryInfo queryInfo) throws Exception
//  {
//    // Pull the selector based on the query type
//    String selector = QueryUtils.getSelectorByQueryType(dataElement, qSchema, dSchema);
//    int hash = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selector);
//    logger.debug("selector = " + selector + " hash = " + hash);
//
//    // Extract the data bits based on the query type
//    // Partition by the given partitionSize
//    List<BigInteger> hitValPartitions = QueryUtils.partitionDataElement(dataElement, qSchema, dSchema, queryInfo.getEmbedSelector());
//
//    return new Tuple2<>(hash, hitValPartitions);
//  }

//  public static Tuple2<Integer,BytesArrayWritable> hashSelectorAndFormPartitions(MapWritable dataElement, QuerySchema qSchema, DataSchema dSchema,
//      QueryInfo queryInfo) throws Exception
//  {
//    // Pull the selector based on the query type
//    String selector = QueryUtils.getSelectorByQueryType(dataElement, qSchema, dSchema);
//    int hash = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selector);
//    logger.debug("selector = " + selector + " hash = " + hash);
//
//    // Extract the data bits based on the query type
//    // Partition by the given partitionSize
//    List<BigInteger> hitValPartitions = QueryUtils.partitionDataElement(dataElement, qSchema, dSchema, queryInfo.getEmbedSelector());
//    BytesArrayWritable bAW = new BytesArrayWritable(hitValPartitions);
//
//    return new Tuple2<>(hash, bAW);
//  }

  public static Tuple2<Integer,Byte[]> hashSelectorAndFormPartitions2(MapWritable dataElement, QuerySchema qSchema, DataSchema dSchema,
	      QueryInfo queryInfo) throws Exception
	  {
	    // Pull the selector based on the query type
	    String selector = QueryUtils.getSelectorByQueryType(dataElement, qSchema, dSchema);
	    int hash = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selector);
	    logger.debug("selector = " + selector + " hash = " + hash);

	    int bytesPerPartition = QueryUtils.getBytesPerPartition(queryInfo.getDataPartitionBitSize());
    
	    // Extract the data bits based on the query type
	    // Partition by the given partitionSize
	    Byte[] packedBytes = QueryUtils.partitionDataElementAsBytes(dataElement, qSchema, dSchema, queryInfo.getEmbedSelector(), bytesPerPartition );
	    return new Tuple2<>(hash, packedBytes);
	  }

//  public static Tuple2<Integer,List<BigInteger>> hashSelectorAndFormPartitions(JSONObject json, QueryInfo queryInfo, QuerySchema qSchema) throws Exception
//  {
//    // Pull the selector based on the query type
//    String selector = QueryUtils.getSelectorByQueryTypeJSON(qSchema, json);
//    int hash = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selector);
//    logger.debug("selector = " + selector + " hash = " + hash);
//
//    // Extract the data bits based on the query type
//    // Partition by the given partitionSize
//    List<BigInteger> hitValPartitions = QueryUtils.partitionDataElement(qSchema, json, queryInfo.getEmbedSelector());
//
//    return new Tuple2<>(hash, hitValPartitions);
//  }

  public static int numPartsInPackedBytes(byte[] packedBytes, int bytesPerPart) {
	  return (packedBytes.length + bytesPerPart - 1) / bytesPerPart;
  }

  public static byte[] packedBytesToPartAsBytes(byte[] packedBytes, int bytesPerPart, int i) {
	  byte[] partAsBytes = Arrays.copyOfRange(packedBytes, i*bytesPerPart, (i+1)*bytesPerPart);
	  return partAsBytes;
  }
}
