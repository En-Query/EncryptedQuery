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
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.enquery.encryptedquery.utils.FileConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import scala.Tuple2;

/**
 * Initialization reducer
 * <p>
 * Each reducer call receives {@code rowIndex, {dataBytes, ...}} and
 * outputs key-value pairs {@code <(rowIndex,colNumber), dataBytes>}.
 */
public class SortDataIntoRowsReducer extends Reducer<IntWritable,BytesWritable,IntPairWritable,BytesWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(SortDataIntoRowsReducer.class);

  private IntWritable _rowW = null;
  private IntWritable _colW = null;
  private IntPairWritable outputKey = null;
  private MultipleOutputs<IntPairWritable,BytesWritable> mos = null;
  
  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    _rowW = new IntWritable();
    _colW = new IntWritable();
    outputKey = new IntPairWritable(_rowW, _colW);
    mos = new MultipleOutputs<>(ctx);
  }

  @Override
  public void reduce(IntWritable rowIndexW, Iterable<BytesWritable> dataElements, Context ctx) throws IOException, InterruptedException
  {
    int col = 0;
    int rowIndex = rowIndexW.get();

    outputKey.getFirst().set(rowIndex);
    for (BytesWritable dataElement : dataElements)
    {
      outputKey.getSecond().set(col);
      mos.write(FileConst.PIR, outputKey, dataElement);
      col += dataElement.getLength();
    }
  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
    mos.close();
  }
}
