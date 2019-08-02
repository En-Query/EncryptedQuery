/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.hadoop.core;

import java.io.IOException;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapper class for the ProcessColumn job
 *
 * <p> Each input key-value pairs {@code ((row,col),chunk)} is
 * re-emitted as {@code (col, (row,chunk)}.
 */
public class ProcessColumnsMapper_v1 extends Mapper<IntPairWritable,BytesWritable,IntWritable,IntBytesPairWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(ProcessColumnsMapper_v1.class);

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