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
import java.util.List;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.enquery.encryptedquery.inputformat.hadoop.IntPairWritable;
import org.enquery.encryptedquery.inputformat.hadoop.IntBytesPairWritable;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;

/**
 * Process hash range mapper
 * <p>
 * Reads in {@code ((row,col),[dataBytes])}, computes the hash group
 * identifier, and emits {@code <hashGroup, (rowIndex, [dataBytes]>}.
 * <p>
 * NOTE: This implementation drops the column number, assuming that
 * all the {@code [dataBytes]} array represent fixed-length data and
 * hence can be freely rearranged.
 */
public class ProcessHashRangesMapper extends Mapper<IntPairWritable,BytesWritable,LongWritable,IntBytesPairWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(ProcessHashRangesMapper.class);

  private LongWritable keyOut = null;
  private IntBytesPairWritable valueOut = null;

  private int hashGroupSize;

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    keyOut = new LongWritable();
    valueOut = new IntBytesPairWritable(new IntWritable(), new BytesWritable());
    hashGroupSize = Integer.valueOf(ctx.getConfiguration().get("hashGroupSize"));
  }

  @Override
  public void map(IntPairWritable rowAndCol, BytesWritable dataBytes, Context ctx) throws IOException, InterruptedException
  {
    Integer rowIndex = rowAndCol.getFirst().get();

    keyOut.set(rowIndex / hashGroupSize);
    valueOut.getFirst().set(rowIndex);
    valueOut.setSecond(dataBytes);
    ctx.write(keyOut, valueOut);
  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
  }
}
