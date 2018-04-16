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
 */
package org.enquery.encryptedquery.responder.wideskies.mapreduce;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.enquery.encryptedquery.inputformat.hadoop.IntPairWritable;
import org.enquery.encryptedquery.inputformat.hadoop.IntBytesPairWritable;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;

/**
 * Process column mapper
 * <p>
 * Reads in {@code ((row,col),[dataBytes])}, computes the hash group
 * identifier, and emits {@code <col/V, (row,[dataBytes])>}.
 * <p>
 * NOTE: Currently assumes each array {@code [dataBytes]} has a fixed
 * number of bytes {@code V}.
 */
public class ProcessColumnsMapper extends Mapper<IntPairWritable,BytesWritable,IntWritable,IntBytesPairWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(ProcessColumnsMapper.class);

//  private int numPartsPerElement = 0;
  private int dataPartitionBitSize = 0;
//  private int numBytesPerElement = 0;
  private int bytesPerPart = 0;
  
  private IntWritable keyOut = null;
  private IntBytesPairWritable valueOut = null;

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

//    numPartsPerElement = Integer.valueOf(ctx.getConfiguration().get("numPartsPerElement"));
    dataPartitionBitSize = Integer.valueOf(ctx.getConfiguration().get("dataPartitionBitSize"));
//    numBytesPerElement = numPartsPerElement * dataPartitionBitSize / 8;
    bytesPerPart = dataPartitionBitSize / 8;  // XXX assume divisible by 8

    keyOut = new IntWritable();
    valueOut = new IntBytesPairWritable(new IntWritable(), new BytesWritable());
  }

  @Override
  public void map(IntPairWritable rowAndCol, BytesWritable dataBytes, Context ctx) throws IOException, InterruptedException
  {
    Integer rowIndex = rowAndCol.getFirst().get();
    Integer colIndex = rowAndCol.getSecond().get();

    // combine adjacent bytes into parts
    byte[] entry = dataBytes.copyBytes();
    int numParts = entry.length / bytesPerPart;  // XXX assume divisible
    for (int i = 0; i < numParts; i++)
    {	
//    keyOut.set(colIndex / numBytesPerElement);
  	  keyOut.set(colIndex / bytesPerPart + i);
  	  byte[] part = Arrays.copyOfRange(entry, i * bytesPerPart, (i+1) * bytesPerPart);
//  	  logger.info("YYY row={}, col={}, part.length={}, part[0]={}, part[1]={}", rowIndex, colIndex, part.length, part[0], part[1]);
  	  valueOut.getSecond().set(part, 0, part.length);
  	  valueOut.getFirst().set(rowIndex);
  	  ctx.write(keyOut, valueOut);
    }    
  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
  }
}
