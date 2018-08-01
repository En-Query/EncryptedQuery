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
package org.enquery.encryptedquery.responder.wideskies.mapreduce;

import java.io.IOException;
import java.util.Base64;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.utils.CSVOutputUtils;
import org.enquery.encryptedquery.utils.ConversionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Mapper reads in the data from phase 1 of the map reduce.  Each record is broken down into dps sized chunks
 * to be sent to the reducer.  
 * Input record in the form of:  Hash, Base64 string of partition data
 * Output to Reducer: Key:   Column Number
 *                    Value: hash, partition data in dps sized chunk 
 *
 */
public class ColumnMultMapper extends Mapper<LongWritable,Text,LongWritable,Text>
{
  private static final Logger logger = LoggerFactory.getLogger(ColumnMultMapper.class);

  private LongWritable keyOut = null;
  private Text valueOut = null;
  private int dataPartitionBitSize = 8;
  @SuppressWarnings("unused")
  private int numPartitionsPerElement = 0;

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    keyOut = new LongWritable();
    valueOut = new Text();
    dataPartitionBitSize = Integer.valueOf(ctx.getConfiguration().get("dataPartitionBitSize"));
    numPartitionsPerElement = Integer.valueOf(ctx.getConfiguration().get("numPartitionsPerElement"));
  }
/**
 * Read in the results from the prior phase and break the list of parts into individual parts based on dps size.
 * Output to Reduce the partNum or Colnum as the key and the hash and the individual part as the value
 */
  @Override
  public void map(LongWritable key, Text value, Context ctx) throws IOException, InterruptedException
  {
    // logger.debug("key = " + key.toString() + " value = " + value.toString() );
    int bytesPerPartition = 1;
    if (( dataPartitionBitSize % 8 ) == 0 ) {
    	bytesPerPartition = dataPartitionBitSize / 8 ;
    }
    else {
    	logger.error("dataPartitionBitSize must be a multiple of 8 !! {}", dataPartitionBitSize);
    }
	// logger.debug(" dataPartitionBitSize {} bytesPerPartition {} numPartitionsPerElement {}", dataPartitionBitSize, bytesPerPartition, numPartitionsPerElement);
	// Extract tokens from value.  Token[0] is the Hash or rowIndex, Token[1] is the list of partition data.
    String tokens[] = CSVOutputUtils.extractCSVOutput(value);
    // logger.debug("value = " + value.toString() + " tokens[0] = " + tokens[0] + " tokens[1] = " + tokens[1]);
    
    byte[] decodedString = Base64.getDecoder().decode(tokens[1]);
    if (bytesPerPartition > 1) {
        int partsCounter = 0;
    	byte[] tempByteArray = new byte[bytesPerPartition];
        int j = 0;
    	for (int i = 0; i < decodedString.length; i++) {
           if (j < bytesPerPartition) {
               tempByteArray[j] = decodedString[i];
           } else {
               keyOut.set(partsCounter++); // colNum
               valueOut.set(tokens[0] + "," + ConversionUtils.byteArrayToHexString(tempByteArray)); // rowIndex, colValue
              // logger.debug("columnMultMapper output key = " + keyOut.get() + " value = " + valueOut.toString());
               ctx.write(keyOut, valueOut);
        	   j = 0;
               tempByteArray[j] = decodedString[i];
           }
           j++;
        }
        if (j <= bytesPerPartition ) {
        	while (j < bytesPerPartition) {
    	    	tempByteArray[j] = new Byte("0");
    	    	j++;
        	}
            keyOut.set(partsCounter++); // colNum
            valueOut.set(tokens[0] + "," + ConversionUtils.byteArrayToHexString(tempByteArray)); // rowIndex, colValue
            // logger.debug("columnMultMapper output key = " + keyOut.get() + " value = " + valueOut.toString());
            ctx.write(keyOut, valueOut);
        }
    } else {
        int i = 0;
        for (byte b : decodedString)
        {
        	keyOut.set(i++); // colNum
            valueOut.set(tokens[0] + "," + String.format("%02x", b)); // rowIndex, colValue
          //  logger.debug("single columnMultMapper output key = " + keyOut.get() + " value = " + valueOut.toString());
            ctx.write(keyOut, valueOut);
        }
    }
    
  }
}
