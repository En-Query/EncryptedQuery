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
import java.util.List;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.enquery.encryptedquery.inputformat.hadoop.BytesArrayWritable;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.wideskies.common.ComputeEncryptedRow;
import org.enquery.encryptedquery.responder.wideskies.common.ConcatenateRow;
import org.enquery.encryptedquery.schema.data.DataSchemaLoader;
import org.enquery.encryptedquery.schema.query.QuerySchemaLoader;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.enquery.encryptedquery.utils.FileConst;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;

/**
 * Reducer class to calculate the encrypted rows of the encrypted query
 * <p>
 * For each row (as indicated by key = hash(selector)), iterates over each dataElement and calculates the column values.
 * <p>
 * Emits {@code <colNum, colVal>}
 *
 */
public class RowCalcReducer extends Reducer<IntWritable,BytesArrayWritable,LongWritable,Text>
{
  private static final Logger logger = LoggerFactory.getLogger(RowCalcReducer.class);

  private LongWritable keyOut = null;
  private Text valueOut = null;

  private MultipleOutputs<LongWritable,Text> mos = null;

  private FileSystem fs = null;
  private Query query = null;
  private QueryInfo queryInfo = null;

  private boolean useLocalCache = false;
  private boolean limitHitsPerSelector = false;
  private int maxHitsPerSelector = 1000;

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    keyOut = new LongWritable();
    valueOut = new Text();
    mos = new MultipleOutputs<>(ctx);

    fs = FileSystem.newInstance(ctx.getConfiguration());
    String queryDir = ctx.getConfiguration().get("pirMR.queryInputDir");
    query = new HadoopFileSystemStore(fs).recall(queryDir, Query.class);
    queryInfo = query.getQueryInfo();

    try
    {
      SystemConfiguration.setProperty("data.schemas", ctx.getConfiguration().get("data.schemas"));
      SystemConfiguration.setProperty("query.schemas", ctx.getConfiguration().get("query.schemas"));
      SystemConfiguration.setProperty("pir.stopListFile", ctx.getConfiguration().get("pirMR.stopListFile"));

      DataSchemaLoader.initialize(true, fs);
      QuerySchemaLoader.initialize(true, fs);

    } catch (Exception e)
    {
      e.printStackTrace();
    }

    if (ctx.getConfiguration().get("pirWL.useLocalCache").equals("true"))
    {
      useLocalCache = true;
    }
    if (ctx.getConfiguration().get("pirWL.limitHitsPerSelector").equals("true"))
    {
      limitHitsPerSelector = true;
    }
    maxHitsPerSelector = Integer.parseInt(ctx.getConfiguration().get("pirWL.maxHitsPerSelector"));

    logger.info("RowCalcReducer -- useLocalCache = " + useLocalCache + " limitHitsPerSelector =  " + limitHitsPerSelector + " maxHitsPerSelector = "
        + maxHitsPerSelector);
  }

  @Override
  public void reduce(IntWritable rowIndex, Iterable<BytesArrayWritable> dataElementPartitions, Context ctx) throws IOException, InterruptedException
  {
    logger.info("Processing reducer for hash = " + rowIndex);
    ctx.getCounter(MRStats.NUM_HASHES_REDUCER).increment(1);

    if (queryInfo.useHDFSExpLookupTable())
    {
      ComputeEncryptedRow.loadCacheFromHDFS(fs, query.getExpFile(rowIndex.get()), query);
    }

    List<Tuple2<Long,Byte>> concatenatedRowValues = ConcatenateRow.concatenateRow(dataElementPartitions, query, rowIndex.get(), limitHitsPerSelector,
            maxHitsPerSelector, useLocalCache);

    byte[] values = new byte[concatenatedRowValues.size()];
    int j = 0;
    for (Tuple2<Long,Byte> encRowVal : concatenatedRowValues)
    {
        Byte val = encRowVal._2;
        values[j++] = val.byteValue();
    }

    String valueB64 = Base64.getEncoder().encodeToString(values);
    keyOut.set(rowIndex.get());
    valueOut.set(valueB64);
    logger.debug("Reduce output rowIndex {} / Value {}", keyOut.get(), valueB64 );
    mos.write(FileConst.PIR, keyOut, valueOut);

  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
    mos.close();
  }
}
