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
import java.util.TreeMap;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.enquery.encryptedquery.inputformat.hadoop.BytesArrayWritable;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combine hash ranges partial results reducer (constructs final
 * Response object(s))
 * <p>
 * The reducer task setup creates an empty {@code Response} object.
 * Each call to {@code reducer()} receives a stream of partial results
 * {@code [encryptedColumns]} at a given column index (divided earlier
 * by a fixed width), combines the partial results, and stores the
 * combined values into the {@code Response} object at the given
 * column index.  At the end the {@code Response} object is written
 * into a file.
 */
public class CombineHashRangeResultsReducer extends Reducer<LongWritable,BytesArrayWritable,LongWritable,Text>
{
  private static final Logger logger = LoggerFactory.getLogger(CombineHashRangeResultsReducer.class);

  private MultipleOutputs<LongWritable,Text> mos = null;

  private Response response = null;
  private String outputFile = null;
  private HadoopFileSystemStore storage = null;
  private TreeMap<Integer, BigInteger> treeMap = null;

  private BigInteger NSquared = null;
  private int numPartsPerElement = 0;

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
    outputFile += "-" + ctx.getTaskAttemptID().toString();

    response = new Response(queryInfo);

    treeMap = new TreeMap<>();

    NSquared = query.getNSquared();
    numPartsPerElement = Integer.valueOf(ctx.getConfiguration().get("numPartsPerElement"));
  }

  @Override
  public void reduce(LongWritable colNum, Iterable<BytesArrayWritable> colGroups, Context ctx) throws IOException, InterruptedException
  {
    ctx.getCounter(MRStats.NUM_COLUMNS).increment(1);

    BigInteger[] columns = null;
    boolean first = true;
    for (BytesArrayWritable group: colGroups)
    {
      if (first)
      {
        int sz = group.size();  // this should equal numPartsPerEntry
        columns = new BigInteger[sz];
        for (int i = 0; i < sz; ++i)
        {
          columns[i] = group.getBigInteger(i);
        }
        first = false;
      }
      else
      {
        int sz = group.size();  // this should equal the number of parts
        for (int i = 0; i < sz && i < columns.length; ++i) // just to be safe
        {
          BigInteger newColumn = group.getBigInteger(i);
          columns[i] = columns[i].multiply(newColumn).mod(NSquared);
        }
      }
    }
    // XXX: ideally we should stream this output to a file rather than
    // an in-memory Java object
    int col = (int)colNum.get() * numPartsPerElement;
    for (int i = 0; i < columns.length; ++i)
    {
      treeMap.put(col+i, columns[i]);
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
