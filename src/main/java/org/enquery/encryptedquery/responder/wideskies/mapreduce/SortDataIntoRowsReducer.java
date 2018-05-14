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
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.inputformat.hadoop.IntPairWritable;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.responder.wideskies.common.HashSelectorAndPartitionData;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.enquery.encryptedquery.utils.FileConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import scala.Tuple2;

/**
 * Reducer class for the SortDataIntoRows job
 *
 * <p> Each call to {@code reducer()} receives the data parts
 * corresponding to the data elements belonging to a single row.  The
 * stream of parts are grouped and re-emited in fixed-size windows,
 * which are assigned successively increasing column numbers.  The
 * reducer emits key-value pairs {@code ((row,col), window)}.
 */
public class SortDataIntoRowsReducer extends Reducer<IntWritable,BytesWritable,IntPairWritable,BytesWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(SortDataIntoRowsReducer.class);

  private int dataPartitionBitSize;
  private int bytesPerPart;
  private int windowMaxByteSize;
  private int partsPerWindow;
  private int bytesPerWindow;
  private byte[] buffer;
  private int partsInBuffer;
  
  private IntWritable _rowW = null;
  private IntWritable _colW = null;
  private IntPairWritable outputKey = null;
  private BytesWritable outputValue = null;
  private MultipleOutputs<IntPairWritable,BytesWritable> mos = null;
  
  private boolean limitHitsPerSelector = false;
  private int maxHitsPerSelector = -1;

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    dataPartitionBitSize = Integer.valueOf(ctx.getConfiguration().get("dataPartitionBitSize"));
    bytesPerPart = QueryUtils.getBytesPerPartition(dataPartitionBitSize);
    windowMaxByteSize = Integer.valueOf(ctx.getConfiguration().get("pirMR.windowMaxByteSize"));
    partsPerWindow = windowMaxByteSize / bytesPerPart;
    bytesPerWindow = partsPerWindow * bytesPerPart;
    buffer = new byte[bytesPerWindow];
    partsInBuffer = 0;

    _rowW = new IntWritable();
    _colW = new IntWritable();
    outputKey = new IntPairWritable(_rowW, _colW);
    outputValue = new BytesWritable();
    mos = new MultipleOutputs<>(ctx);

    if (ctx.getConfiguration().get("pirWL.limitHitsPerSelector").equals("true"))
    {
      limitHitsPerSelector = true;
    }
    maxHitsPerSelector = Integer.parseInt(ctx.getConfiguration().get("pirWL.maxHitsPerSelector"));
  }

  @Override
  public void reduce(IntWritable rowIndexW, Iterable<BytesWritable> dataElements, Context ctx) throws IOException, InterruptedException
  {
	int hitCount = 0;
    int rowIndex = rowIndexW.get();
    int windowCol = 0;

    outputKey.getFirst().set(rowIndex);
    for (BytesWritable dataElement : dataElements)
    {
      if (limitHitsPerSelector && hitCount >= maxHitsPerSelector)
      {
        logger.info("maxHitsPerSelector limit ({}) reached for rowIndex = {}", maxHitsPerSelector, rowIndex);
        break;
      }
      
      /* Extract data element bytes.
       * We assume this has already been padded to be a multiple of bytesPerPart bytes
       */
      byte[] dataElementBytes = dataElement.copyBytes();
      int current = 0;
      int partsRemaining = dataElementBytes.length / bytesPerPart;

      while (partsRemaining > 0)
      {
        /* If copy data as much additional data into buffer as possible */
    	    int partsToCopy = partsPerWindow - partsInBuffer;
    	    if (partsToCopy > partsRemaining)
    	    {
    	    	  partsToCopy = partsRemaining;
    	    }
    	    System.arraycopy(dataElementBytes,  current*bytesPerPart, buffer, partsInBuffer*bytesPerPart, partsToCopy*bytesPerPart);
    	    partsInBuffer += partsToCopy;
    	    current += partsToCopy;
    	    partsRemaining -= partsToCopy;
    	    
        /* if buffer is full, write it out */
        if (partsInBuffer == partsPerWindow)
        {
          outputKey.getSecond().set(windowCol);
          outputValue.set(buffer, 0, bytesPerWindow);
          mos.write(FileConst.PIR, outputKey, outputValue);
          windowCol += 1;
          partsInBuffer = 0;
        	}
      }

      hitCount += 1;
    }
    
    /* write out any remaining data */
    if (partsInBuffer > 0)
    {
      outputKey.getSecond().set(windowCol);
      outputValue.set(buffer, 0, partsInBuffer * bytesPerPart);
      mos.write(FileConst.PIR, outputKey, outputValue);
      windowCol += 1;
      partsInBuffer = 0;
    }
  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
	mos.close();
  }
}
