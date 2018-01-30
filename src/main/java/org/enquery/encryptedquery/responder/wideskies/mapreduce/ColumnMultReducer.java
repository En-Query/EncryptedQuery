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
import java.util.concurrent.ExecutionException;

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
    
    if (ctx.getConfiguration().get("pirWL.useLocalCache").equals("true"))
    {
      useLocalCache = true;
    }
    
  }

  @Override
  public void reduce(LongWritable colNum, Iterable<Text> colVals, Context ctx) throws IOException, InterruptedException
  {
    logger.info("Processing reducer for colNum = " + colNum.toString());
    ctx.getCounter(MRStats.NUM_COLUMNS).increment(1);
    long startTime = System.currentTimeMillis();
    BigInteger column = BigInteger.valueOf(1);
    long columnCounter = 0;
    for (Text val : colVals)
    {
    	
        String[] valueInfo = val.toString().split(",");
        int rowIndex = Integer.valueOf(valueInfo[0]);
        String part = valueInfo[1];
      //  logger.debug("Processing rowIndex {}, value {}", rowIndex, part);
        
        byte[] partAsByteArray = QueryUtils.hexStringToByteArray(part);
        BigInteger partAsBI = new BigInteger(1, partAsByteArray);
      //  logger.debug("partAsBi: " + partAsBI.toString(16));
        
        BigInteger rowQuery = query.getQueryElement(rowIndex);
      //  logger.debug("rowQuery: " + rowQuery.toString());

        BigInteger exp = null;   	
        try
        {
          if (useLocalCache)
          {
            exp = expCache.get(new Tuple3<>(rowQuery, partAsBI, query.getNSquared()));
          }
          else
          {
            exp = ModPowAbstraction.modPow(rowQuery, partAsBI, query.getNSquared());
          }
        } catch (ExecutionException e)
        {
          e.printStackTrace();
        }     
        
      //  logger.debug("exp = " + exp.toString());
        column = (column.multiply(exp)).mod(query.getNSquared());
      //  logger.debug("column = " + column.toString());   	
        columnCounter++;
    }
    // logger.debug("final column value = " + column.toString());
    long duration = System.currentTimeMillis() - startTime;
    logger.info("It took {} Milliseconds to process {} parts for column {}", duration, columnCounter, colNum.toString());
    outputValue.set(column.toString());
    mos.write(FileConst.PIR_COLS, colNum, outputValue);
  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
    mos.close();
  }
}
