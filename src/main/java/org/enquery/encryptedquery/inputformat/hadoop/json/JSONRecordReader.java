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

import org.apache.hadoop.fs.ChecksumException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.LineRecordReader;
import org.enquery.encryptedquery.inputformat.hadoop.TextArrayWritable;
import org.enquery.encryptedquery.schema.data.DataSchema;
import org.enquery.encryptedquery.schema.data.DataSchemaLoader;
import org.enquery.encryptedquery.schema.data.DataSchemaRegistry;
import org.enquery.encryptedquery.utils.QueryParserUtils;
import org.enquery.encryptedquery.utils.StringUtils;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Record reader to parse files of JSON string representations, one per line
 *
 */
public class JSONRecordReader extends RecordReader<Text,MapWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(JSONRecordReader.class);

  private LineRecordReader lineReader = null;
  private Text key = null;
  private MapWritable value = null;
  private JSONParser jsonParser = null;
  private String queryString = null;
  private DataSchema dataSchema = null;

  @Override
  public void initialize(InputSplit inputSplit, TaskAttemptContext context) throws IOException
  {
    key = new Text();
    value = new MapWritable();
    jsonParser = new JSONParser();

    lineReader = new LineRecordReader();
    lineReader.initialize(inputSplit, context);

    queryString = context.getConfiguration().get("query", "?q=*");

    // Load the data schemas
    FileSystem fs = FileSystem.get(context.getConfiguration());
    try
    {
      SystemConfiguration.setProperty("data.schemas", context.getConfiguration().get("data.schemas"));
      DataSchemaLoader.initialize(true, fs);
    } catch (Exception e)
    {
      e.printStackTrace();
    }
    String dataSchemaName = context.getConfiguration().get("dataSchemaName");
    dataSchema = DataSchemaRegistry.get(dataSchemaName);
  }

  @Override
  public Text getCurrentKey() throws IOException, InterruptedException
  {
    return key;
  }

  @Override
  public MapWritable getCurrentValue() throws IOException, InterruptedException
  {
    return value;
  }

  @Override
  public float getProgress() throws IOException, InterruptedException
  {
    return lineReader.getProgress();
  }

  @Override
  public void close() throws IOException
  {
    lineReader.close();
  }

  @Override
  public boolean nextKeyValue() throws IOException, InterruptedException
  {
    boolean success = true;
    value.clear();

    try
    {
      if (!lineReader.nextKeyValue())
      {
        success = false;
      }
      else
      {
        key.set(lineReader.getCurrentKey().toString());
        boolean satisfiedQuery = false;
        while (!satisfiedQuery)
        {
          satisfiedQuery = decodeLineToJson(lineReader.getCurrentValue());
          if (!satisfiedQuery)
          {
            value.clear();
            if (!lineReader.nextKeyValue())
            {
              success = false;
              break;
            }
          }
          else
          {
            success = true;
          }
        }
      }
    } catch (ChecksumException s)
    {
      logger.warn("Caught checksum exception");
      success = false;
    }
    return success;
  }

  public boolean decodeLineToJson(Text line)
  {
    try
    {
      toMapWritable(line);

      // Check to see if the record satisfies the query
      return QueryParserUtils.checkRecord(queryString, value, dataSchema);

    } catch (ParseException e)
    {
      logger.warn("Could not json-decode string: " + line, e);
      return false;
    } catch (NumberFormatException e)
    {
      logger.warn("Could not parse field into number: " + line, e);
      return false;
    }
  }

  public void toMapWritable(Text line) throws ParseException
  {
    JSONObject jsonObj = (JSONObject) jsonParser.parse(line.toString());
    for (Object key : jsonObj.keySet())
    {
      Text mapKey = new Text(key.toString());
      Text mapValue = new Text();
      if (jsonObj.get(key) != null)
      {
        if (dataSchema.isArrayElement(key.toString()))
        {
          String[] elements = StringUtils.jsonArrayStringToList(jsonObj.get(key).toString());
          TextArrayWritable aw = new TextArrayWritable(elements);
          value.put(mapKey, aw);
        }
        else
        {
          mapValue.set(jsonObj.get(key).toString());
          value.put(mapKey, mapValue);
        }
      }
    }
  }
}
