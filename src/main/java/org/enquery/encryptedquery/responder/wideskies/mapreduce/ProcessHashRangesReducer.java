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
import org.enquery.encryptedquery.inputformat.hadoop.IntBytesPairWritable;
import org.enquery.encryptedquery.inputformat.hadoop.BytesArrayWritable;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.enquery.encryptedquery.utils.FileConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import scala.Tuple2;

/**
 * Process hash range reducer
 * <p>
 * Each call to {@code reducer()} receives a stream {@code {
 * (rowIndex, [dataBytes] }} belonging to a unique hash range,
 * computes products of {@code (queryElement)^dataByte}, and emits a
 * stream of {@code (startCol/numPartsPerElement, [encryptedColumn])}.
 * <p>
 * NOTE: assumes that each array of databytes corresponds to a unique
 * data element and has a fixed length.  Each output array of
 * encrypted Columns will also have a fixed length of {@code
 * numPartsPerElement}.
 */
public class ProcessHashRangesReducer extends Reducer<LongWritable,IntBytesPairWritable,LongWritable,BytesArrayWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(ProcessHashRangesReducer.class);

  private LongWritable outputKey = null;
  private MultipleOutputs<LongWritable,BytesArrayWritable> mos = null;
  private boolean useLocalCache = false;
  
  private Query query = null;
  private int dataPartitionBitSize = 8;
  private int numPartsPerElement = 0;
  private int bytesPerPart = 0;
  private static BigInteger NSquared = null;

  // Input: base, exponent
  // <<base,exponent,NSquared>, base^exponent mod N^2>
  private static LoadingCache<Tuple2<BigInteger,BigInteger>,BigInteger> expCache = CacheBuilder.newBuilder().maximumSize(10000)
      .build(new CacheLoader<Tuple2<BigInteger,BigInteger>,BigInteger>()
      {
        @Override
        public BigInteger load(Tuple2<BigInteger,BigInteger> info) throws Exception
        {
          return ModPowAbstraction.modPow(info._1(), info._2(), NSquared);
        }
      });
  
  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    outputKey = new LongWritable();
    mos = new MultipleOutputs<>(ctx);

    FileSystem fs = FileSystem.newInstance(ctx.getConfiguration());
    String queryDir = ctx.getConfiguration().get("pirMR.queryInputDir");
    query = new HadoopFileSystemStore(fs).recall(queryDir, Query.class);
    NSquared = query.getNSquared();
    dataPartitionBitSize = Integer.valueOf(ctx.getConfiguration().get("dataPartitionBitSize"));
    numPartsPerElement = Integer.valueOf(ctx.getConfiguration().get("numPartsPerElement"));
    if ((dataPartitionBitSize % 8 ) != 0)
    {
      logger.error("dataPartitionBitSize must be a multiple of 8 !! {}", dataPartitionBitSize);
      throw new RuntimeException("dataPartitionBitSize (" + dataPartitionBitSize + ") must be a multiple of 8");
    }
    bytesPerPart = dataPartitionBitSize / 8 ;
    
    if (ctx.getConfiguration().get("pirWL.useLocalCache").equals("true"))
    {
      useLocalCache = true;
    }
    
  }

  @Override
  public void reduce(LongWritable truncRowIndex, Iterable<IntBytesPairWritable> colVals, Context ctx) throws IOException, InterruptedException
  {
    //logger.info("Processing reducer for truncRowIndex = " + truncRowIndex.toString());
    ctx.getCounter(MRStats.NUM_COLUMNS).increment(1);
    long startReducer = System.nanoTime();
    long totalReducer = 0;
    long totalMath = 0;

    // XXX use circular buffer?
    // XXX set initial capacity?
    ArrayList<BigInteger> columns = new ArrayList<>();

    // XXX if we are working with a range of rows, we only need counters
    //     for row indices within the range
    // initialized to zeros
    int rowCounters[] = new int[1 << query.getQueryInfo().getHashBitSize()];

    long entryCounter = 0;
    for (IntBytesPairWritable val : colVals)
    {
      entryCounter++;

      int rowIndex = val.getFirst().get();
      byte[] entry = val.getSecond().copyBytes();
        
      // prepare parts
      // XXX do this in the mapper?
      // XXX refactor this
      BigInteger[] parts = new BigInteger[numPartsPerElement];
      for (int i = 0; i < numPartsPerElement; i++)
      {
	// XXX range check?
	// XXX refactor?
	parts[i] = new BigInteger(1, Arrays.copyOfRange(entry, i * bytesPerPart, (i+1) * bytesPerPart));
      }

      //int rowCounter = rowCounters.get(rowIndex);
      int rowCounter = rowCounters[rowIndex];
      BigInteger rowQuery = query.getQueryElement(rowIndex);

      // update column values
      for (int i = 0; i < parts.length; i++)
      {
	if (rowCounter + i >= columns.size())
	{
	  // XXX assert rowCounter + i == columns.size()
	  columns.add(BigInteger.ONE);
	}

	BigInteger column = columns.get(rowCounter + i);
	
        long startMath = System.nanoTime();

	//BigInteger exp = ModPowAbstraction.modPow(rowQuery, parts[i], NSquared);

	BigInteger exp = null;
        try
        {
          if (useLocalCache)
          {
            exp = expCache.get(new Tuple2<>(rowQuery, parts[i]));
          }
          else
          {
            exp = ModPowAbstraction.modPow(rowQuery, parts[i], query.getNSquared());
          }
        } catch (ExecutionException e)
        {
          e.printStackTrace();
        }     
        
        column = (column.multiply(exp)).mod(NSquared);
        totalMath += System.nanoTime() - startMath;
	columns.set(rowCounter + i, column);

      }
      rowCounters[rowIndex] = rowCounter + parts.length;
    }

    totalReducer = System.nanoTime() - startReducer;
    logger.info("Reducer took {} nanoseconds to process {} entries for truncRowIndex {}", totalReducer, entryCounter, truncRowIndex.toString());
    logger.info("  math: {} nanoseconds", totalMath);

    // write columns to file
    for (int i = 0; i < columns.size(); i += numPartsPerElement)
    {
      List<BigInteger> cols = columns.subList(i, i + numPartsPerElement);
      BytesArrayWritable colGroup = new BytesArrayWritable(cols);  // XXX reuse storage?
      outputKey.set((long)i / numPartsPerElement);
      mos.write(FileConst.PIR_COLS, outputKey, colGroup);
    }
  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
    mos.close();
  }
}
