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

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.enquery.encryptedquery.inputformat.hadoop.IntPairWritable;
import org.enquery.encryptedquery.inputformat.hadoop.IntBytesPairWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapper class for the ProcessColumn job
 *
 * <p> Each input key-value pairs {@code ((row,col),chunk)} is
 * re-emitted as {@code (col, (row,chunk)}.
 */
public class ProcessColumnsMapper extends Mapper<IntPairWritable,BytesWritable,IntWritable,IntBytesPairWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(ProcessColumnsMapper.class);

  private IntWritable keyOut = null;
  private IntBytesPairWritable valueOut = null;

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);
    keyOut = new IntWritable();
    valueOut = new IntBytesPairWritable(new IntWritable(), new BytesWritable());
  }

  @Override
  public void map(IntPairWritable rowAndCol, BytesWritable dataBytes, Context ctx) throws IOException, InterruptedException
  {
    Integer rowIndex = rowAndCol.getFirst().get();
    Integer colIndex = rowAndCol.getSecond().get();

    byte[] chunkBytes = dataBytes.copyBytes();
    keyOut.set(colIndex);
    valueOut.getSecond().set(chunkBytes, 0, chunkBytes.length);
    valueOut.getFirst().set(rowIndex);
    ctx.write(keyOut, valueOut);
  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
  }
}
