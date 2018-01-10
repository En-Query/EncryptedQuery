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

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.enquery.encryptedquery.inputformat.hadoop.BytesArrayWritable;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;

public class ConcatenateRow {

	  private static final Logger logger = LoggerFactory.getLogger(ConcatenateRow.class);
	  private static int dataPartitionBitSize = 8;
	  private static int numPartitionsPerElement = 0;

	  public static List<Tuple2<Long,Byte>> concatenateRow(Iterable<BytesArrayWritable> dataPartitionsIter, Query query, int rowIndex,
		      boolean limitHitsPerSelector, int maxHitsPerSelector, boolean useCache) throws IOException
		  {
		    List<Tuple2<Long,Byte>> returnPairs = new ArrayList<>();

            dataPartitionBitSize = query.getQueryInfo().getDataPartitionBitSize();
            numPartitionsPerElement = query.getQueryInfo().getNumPartitionsPerDataElement();
            int bytesPerPartition = 1;
            if (( dataPartitionBitSize % 8 ) == 0 ) {
            	bytesPerPartition = dataPartitionBitSize / 8 ;
            }
            else {
            	logger.error("dataPartitionBitSize must be a multiple of 8 !! {}", dataPartitionBitSize);
            }
            
        	int totalElementBytesNeeded = bytesPerPartition * numPartitionsPerElement;
        	
            logger.debug(" dataPartitionBitSize {} bytesPerPartition {} numPartitionsPerElement {} totalElementBytesNeeded {}",
            		 dataPartitionBitSize, bytesPerPartition, numPartitionsPerElement, totalElementBytesNeeded);
        	
		    long colCounter = 0;
		    int elementCounter = 0;
		    for (BytesArrayWritable dataPartitions : dataPartitionsIter)
		    {
		      logger.debug("rowIndex = {} elementCounter = {}", rowIndex, elementCounter);

		      if (limitHitsPerSelector)
		      {
		        if (elementCounter >= maxHitsPerSelector)
		        {
		          logger.info("Element counter for rowIndex " + rowIndex + " has surpassed the maxHitsPerSelector value " + maxHitsPerSelector);
		          break;
		        }
		      }
		      
		      logger.debug("dataPartitions.size() = {} rowIndex = {} colCounter = {} elementCounter = {}", dataPartitions.size(), rowIndex, colCounter, elementCounter);

		      int byteCount = 0;
		      for (int i = 0; i < dataPartitions.size(); ++i)
		      {
		        BigInteger part = dataPartitions.getBigInteger(i);
                byte partAsByte = part.byteValue();
   		        returnPairs.add(new Tuple2<>(colCounter++, partAsByte));
   		        logger.debug("Added part {}", String.format("%02x",  partAsByte));
                byteCount++;
		      }
 		      for (int i = byteCount; i < totalElementBytesNeeded; i++ ) {
 	                byte partAsByte = new Byte("0");
 	   		        returnPairs.add(new Tuple2<>(colCounter++, partAsByte));
 	   		        logger.debug("Added part {}", String.format("%02x",  partAsByte));
 		      }
		      ++elementCounter;
		    }
		    logger.info("rowIndex = {} has {} hits", rowIndex, elementCounter);
		    return returnPairs;
		  }
}
