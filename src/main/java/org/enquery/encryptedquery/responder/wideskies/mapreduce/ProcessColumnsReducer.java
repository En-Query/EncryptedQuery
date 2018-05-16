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

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.enquery.encryptedquery.inputformat.hadoop.IntBytesPairWritable;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.responder.wideskies.common.ComputeEncryptedColumn;
import org.enquery.encryptedquery.responder.wideskies.common.ComputeEncryptedColumnFactory;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.enquery.encryptedquery.utils.FileConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reducer class for the ProcessColumn job

 * <p> Each call to {@code reducer()} receives a stream of values
 * {@code (row,window)} at a given column position.  These values are
 * all read in (there will be a limited number of them) and used to
 * compute the encrypted column value for each successive column
 * within the window, using one of the classes implementing the {@code
 * ComputeEncryptedColumn} interface.  As each encrypted column value
 * is computed, a key-value pair {@code (col, encvalue)} is emitted.
 */
public class ProcessColumnsReducer extends Reducer<IntWritable,IntBytesPairWritable,LongWritable,BytesWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(ProcessColumnsReducer.class);

  private LongWritable outputKey = null;
  private BytesWritable outputValue;

  private MultipleOutputs<LongWritable,BytesWritable> mos = null;
  
  private Query query = null;
  private int hashBitSize = 0;
  private int dataPartitionBitSize = 8;
  private int bytesPerPart = 0;
  private int windowMaxByteSize;
  private int partsPerWindow;
  private int bytesPerWindow;
  private byte[][] dataWindows;
  private int rowIndices[];
  private int numParts[];
  private static BigInteger NSquared = null;

  private String encryptColumnMethod = null;
  private ComputeEncryptedColumn cec = null;

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    outputKey = new LongWritable();
    outputValue = new BytesWritable();
    mos = new MultipleOutputs<>(ctx);

    FileSystem fs = FileSystem.newInstance(ctx.getConfiguration());
    String queryDir = ctx.getConfiguration().get("pirMR.queryInputDir");
    query = new HadoopFileSystemStore(fs).recall(queryDir, Query.class);
    hashBitSize = query.getQueryInfo().getHashBitSize();
    NSquared = query.getNSquared();
    dataPartitionBitSize = Integer.valueOf(ctx.getConfiguration().get("dataPartitionBitSize"));
    bytesPerPart = QueryUtils.getBytesPerPartition(dataPartitionBitSize);
    windowMaxByteSize = Integer.valueOf(ctx.getConfiguration().get("pirMR.windowMaxByteSize"));
    partsPerWindow = windowMaxByteSize / bytesPerPart;
    bytesPerWindow = partsPerWindow * bytesPerPart;
    dataWindows = new byte[1 << hashBitSize][];
    rowIndices = new int[1 << hashBitSize];
    numParts = new int[1 << hashBitSize];

    encryptColumnMethod = ctx.getConfiguration().get("responder.encryptColumnMethod");
    cec = ComputeEncryptedColumnFactory.getComputeEncryptedColumnMethod(encryptColumnMethod, query.getQueryElements(), NSquared, (1<<hashBitSize), dataPartitionBitSize);
  }

  @Override
  public void reduce(IntWritable colIndexW, Iterable<IntBytesPairWritable> rowIndexAndData, Context ctx) throws IOException, InterruptedException
  {
    ctx.getCounter(MRStats.NUM_COLUMNS).increment(1);

    int numWindows = 0;
    int colIndex = colIndexW.get();

    // read in all the (row, data) pairs
    for (IntBytesPairWritable val : rowIndexAndData)
    {
      // extract row index
      int rowIndex = val.getFirst().get();
      byte[] dataWindow = val.getSecond().copyBytes();

      rowIndices[numWindows] = rowIndex;
      dataWindows[numWindows] = dataWindow;
      numParts[numWindows] = dataWindow.length / bytesPerPart;
      numWindows++;
    }
    
    /* process each column of the buffered data */
    for (int col = 0; col < partsPerWindow; col++)
    {
      boolean emptyColumn = true;
    	  for (int i = 0; i < numWindows; i++)
    	  {
    		if (numParts[i] <= col) continue;
        emptyColumn = false;
        int rowIndex = rowIndices[i];        
        byte[] partBytes = Arrays.copyOfRange(dataWindows[i], col*bytesPerPart, (col+1)*bytesPerPart);
        BigInteger part = new BigInteger(1, partBytes);
        cec.insertDataPart(rowIndex, part);
    	  }
    	  if (emptyColumn)
    	  {
        /* once we have encountered an empty column, we are done */
        break;
    	  }

    	  BigInteger encryptedColumn = cec.computeColumnAndClearData();

    	  /* write encrypted column to file */
      long partColIndex = (long) colIndex * partsPerWindow + col;
      outputKey.set(partColIndex);
      byte[] columnBytes = encryptedColumn.toByteArray(); 
      outputValue.set(columnBytes, 0, columnBytes.length);
      mos.write(FileConst.PIR_COLS, outputKey, outputValue);
    }
}

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
    mos.close();
    cec.free();
  }
}
