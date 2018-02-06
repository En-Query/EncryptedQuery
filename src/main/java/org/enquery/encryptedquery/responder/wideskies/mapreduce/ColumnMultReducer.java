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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.enquery.encryptedquery.utils.FileConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import scala.Tuple3;

/**
 * Reducer to perform encrypted column multiplication
 * 
 */
public class ColumnMultReducer extends Reducer<LongWritable,Text,LongWritable,Text>
{
  private static final Logger logger = LoggerFactory.getLogger(ColumnMultReducer.class);

  private Text outputValue = null;
  private MultipleOutputs<LongWritable,Text> mos = null;
  private boolean useLocalCache = false;
  private int threadPoolSize = 10;
  private int partitionsPerThread = 1000;
  
  private Query query = null;

  // Input: base, exponent, NSquared
  // <<base,exponent,NSquared>, base^exponent mod N^2>
  private static LoadingCache<Tuple3<BigInteger,BigInteger,BigInteger>,BigInteger> expCache = CacheBuilder.newBuilder().maximumSize(10000)
      .build(new CacheLoader<Tuple3<BigInteger,BigInteger,BigInteger>,BigInteger>()
      {
        @Override
        public BigInteger load(Tuple3<BigInteger,BigInteger,BigInteger> info) throws Exception
        {
          logger.debug("cache miss");
          return ModPowAbstraction.modPow(info._1(), info._2(), info._3());
        }
      });
  
  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    outputValue = new Text();
    mos = new MultipleOutputs<>(ctx);

    FileSystem fs = FileSystem.newInstance(ctx.getConfiguration());
    String queryDir = ctx.getConfiguration().get("pirMR.queryInputDir");
    query = new HadoopFileSystemStore(fs).recall(queryDir, Query.class);
    threadPoolSize = ctx.getConfiguration().getInt("computeThreadPoolSize", 10);
    partitionsPerThread = ctx.getConfiguration().getInt("computePartitionsPerThread", 1000);

    if (ctx.getConfiguration().get("pirWL.useLocalCache").equals("true"))
    {
      useLocalCache = true;
    }
    
  }

  @Override
  public void reduce(LongWritable colNum, Iterable<Text> colVals, Context ctx) throws IOException, InterruptedException
  {
    logger.info("Processing reducer for colNum {} threadPoolSize {} recordsPerThread {}", colNum.toString(), threadPoolSize, partitionsPerThread);
    ctx.getCounter(MRStats.NUM_HASHES_REDUCER).increment(1);
    
    long startTime = System.currentTimeMillis();
    ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
    
    List<String> valuesToProcess = new ArrayList<String>();
    
    List<Callable<ColumnValue>> lst = new ArrayList<Callable<ColumnValue>>();
    long columnCounter = 0;
    
    for (Text val : colVals) {
        valuesToProcess.add(val.toString());
        columnCounter++;
        if ( columnCounter % partitionsPerThread == 0) {
        	List<String> valuesToSend = Optional.ofNullable(valuesToProcess)
                    .map(List::stream)
                    .orElseGet(Stream::empty)
                    .collect(Collectors.toList());
            lst.add(new ComputeColumnValue(colNum.toString(), valuesToSend, query));
            valuesToProcess.clear();
        }
    }
    if (valuesToProcess.size() > 0) {
        lst.add(new ComputeColumnValue(colNum.toString(), valuesToProcess, query));
    }

//    logger.info("Submitted {} Records in {} tasks", columnCounter, lst.size());
    
    // returns a list of Futures holding their status and results when all complete
    List<Future<ColumnValue>> tasks = executorService.invokeAll(lst);
     
//    logger.info("{} Tasks Completed", tasks.size());
    BigInteger column = BigInteger.valueOf(1);
     
    for(Future<ColumnValue> task : tasks)
    {
        try {
            column = (column.multiply(task.get().getValue())).mod(query.getNSquared());
		} catch (ExecutionException e) {
            logger.error("Error Exception getting value from task thread: \n" + e.getMessage());
		}
    }
     
    outputValue.set(column.toString());
    mos.write(FileConst.PIR_COLS, colNum, outputValue);

    /* shutdown your thread pool, else your application will keep running */
    executorService.shutdown();
    long duration = System.currentTimeMillis() - startTime;
    logger.info("It took {} Milliseconds to process {} parts for column {} in {} threads", duration, columnCounter, colNum.toString(), tasks.size());

  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
    mos.close();
  }
}
