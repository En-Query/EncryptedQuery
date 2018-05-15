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
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.enquery.encryptedquery.inputformat.hadoop.IntBytesPairWritable;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.responder.wideskies.common.ComputeEncryptedColumn;
import org.enquery.encryptedquery.responder.wideskies.common.ComputeEncryptedColumnFactory;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.enquery.encryptedquery.utils.FileConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process column reducer
 * <p>
 * Each call to {@code reducer()} receives a stream {@code {
 * (rowIndex, [dataBytes] }} corresponding to a unique column index
 * (divided by some fixed width {@code V}).  The function then
 * computes a list of encrypted columns using a column-based
 * algorithm, and then pairs {@code (startCol, encryptedColumn0)},
 * {@code (startCol+1, encryptedColumn1)}, ....
 * <p>
 * NOTE: We assume that the arrays of data bytes are all <= {@code W}
 * parts in length, equal except at the very end of rows.  We also
 * assume that {@code W} is small enough that the reducer can read all
 * the data into memory before processing it.
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
  private static BigInteger NSquared = null;

  private String encryptColumnMethod = null;
  private ComputeEncryptedColumn cec = null;
  private int cnt = 0; // XXX

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    logger.info("XXX setup()");

    outputKey = new LongWritable();
    outputValue = new BytesWritable();
    mos = new MultipleOutputs<>(ctx);

    FileSystem fs = FileSystem.newInstance(ctx.getConfiguration());
    String queryDir = ctx.getConfiguration().get("pirMR.queryInputDir");
    query = new HadoopFileSystemStore(fs).recall(queryDir, Query.class);
    hashBitSize = query.getQueryInfo().getHashBitSize();
    NSquared = query.getNSquared();
    dataPartitionBitSize = Integer.valueOf(ctx.getConfiguration().get("dataPartitionBitSize"));

    encryptColumnMethod = ctx.getConfiguration().get("responder.encryptColumnMethod");
    cec = ComputeEncryptedColumnFactory.getComputeEncryptedColumnMethod(encryptColumnMethod, query.getQueryElements(), NSquared, (1<<hashBitSize), dataPartitionBitSize);
  }

  @Override
  public void reduce(IntWritable colIndex, Iterable<IntBytesPairWritable> rowIndexAndData, Context ctx) throws IOException, InterruptedException
  {
    cnt++; // XXX
    ctx.getCounter(MRStats.NUM_COLUMNS).increment(1);

    // read in all the (row, data) pairs
    for (IntBytesPairWritable val : rowIndexAndData)
    {
      // extract row index
      int rowIndex = val.getFirst().get();

      byte[] partBytes = val.getSecond().copyBytes();
      BigInteger part = new BigInteger(1, partBytes);
      
//      logger.info("XXX row={}, col={}, part={}", rowIndex, colIndex.get(), part);
    		  
      cec.insertDataPart(rowIndex, part);
    }
    BigInteger encryptedColumn = cec.computeColumnAndClearData();

    // write column to file
    outputKey.set((long)colIndex.get());
    byte[] columnBytes = encryptedColumn.toByteArray(); 
    outputValue.set(columnBytes, 0, columnBytes.length);
    mos.write(FileConst.PIR_COLS, outputKey, outputValue);
  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
    logger.info("XXX cleanup(), cnt = {}", cnt);
    mos.close();
    cec.free();
  }
}
