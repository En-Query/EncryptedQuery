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
package org.enquery.encryptedquery.inputformat.hadoop.json;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.enquery.encryptedquery.inputformat.hadoop.BaseInputFormat;

/**
 * Custom import format to parse files string representations of JSON, one JSON string per line extending BaseInputFormat
 */
public class JSONInputFormatBase extends BaseInputFormat<Text,MapWritable>
{
  private JSONInputFormat jsonInputFormat = new JSONInputFormat();

  @Override
  public RecordReader<Text,MapWritable> createRecordReader(InputSplit arg0, TaskAttemptContext arg1) throws IOException, InterruptedException
  {
    return new JSONRecordReader();
  }

  @Override
  public List<InputSplit> getSplits(JobContext arg0) throws IOException, InterruptedException
  {
    return jsonInputFormat.getSplits(arg0);
  }
}
