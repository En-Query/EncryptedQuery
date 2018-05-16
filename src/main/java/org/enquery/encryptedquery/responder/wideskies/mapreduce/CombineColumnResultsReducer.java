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
import java.util.TreeMap;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reducer class for the CombineColumnResults job
 *
 * <p> It is assumed that this job is run with a single reducer task.
 * The encrypted values from the previous job are inserted into a new
 * {@code Response}, which is written to a file.
 */
public class CombineColumnResultsReducer extends Reducer<LongWritable,BytesWritable,LongWritable,Text>
{
  private static final Logger logger = LoggerFactory.getLogger(CombineColumnResultsReducer.class);

  private MultipleOutputs<LongWritable,Text> mos = null;

  private Response response = null;
  private String outputFile = null;
  private HadoopFileSystemStore storage = null;
  private TreeMap<Integer, BigInteger> treeMap = null;

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    mos = new MultipleOutputs<>(ctx);

    FileSystem fs = FileSystem.newInstance(ctx.getConfiguration());
    storage = new HadoopFileSystemStore(fs);
    String queryDir = ctx.getConfiguration().get("pirMR.queryInputDir");
    Query query = storage.recall(queryDir, Query.class);
    QueryInfo queryInfo = query.getQueryInfo();

    outputFile = ctx.getConfiguration().get("pirMR.outputFile");
    response = new Response(queryInfo);

    treeMap = new TreeMap<>();
  }

  @Override
  public void reduce(LongWritable colNum, Iterable<BytesWritable> encryptedColumns, Context ctx) throws IOException, InterruptedException
  {
    ctx.getCounter(MRStats.NUM_COLUMNS).increment(1);

    boolean first = true;

    int colIndex = (int)colNum.get();
    for (BytesWritable encryptedColumnW: encryptedColumns)
    {
      if (first)
      {
        treeMap.put(colIndex, new BigInteger(encryptedColumnW.copyBytes()));
        first = false;
      }
      else
      {
        logger.warn("column index {} unexpectedly seen a second time!", colIndex);
      }
    }
  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
    response.addResponseElements(treeMap);
    storage.store(outputFile, response);
    mos.close();
  }
}
