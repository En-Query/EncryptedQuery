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
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Mapper class for the CombineColumnResults job
 *
 * <p> This is a pass-through mapper that reads in pairs {@code
 * (startCol, encvalue)} and emits the same key-value pair unchanged.
 */
public class CombineColumnResultsMapper extends Mapper<LongWritable,BytesWritable,LongWritable,BytesWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(CombineColumnResultsMapper.class);

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);
  }

  @Override
  public void map(LongWritable colIndex, BytesWritable encryptedColumns, Context ctx) throws IOException, InterruptedException
  {
    ctx.write(colIndex, encryptedColumns);
  }
}
