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
package org.enquery.encryptedquery.test.distributed;

import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.enquery.encryptedquery.schema.data.DataSchemaLoader;
import org.enquery.encryptedquery.schema.query.QuerySchemaLoader;
import org.enquery.encryptedquery.schema.query.filter.StopListFilter;
import org.enquery.encryptedquery.test.distributed.testsuite.DistTestSuite;
import org.enquery.encryptedquery.test.utils.Inputs;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Driver class to run the suite of functional tests for MR and Spark PIR
 *
 */
public class DistributedTestDriver
{
  private static final Logger logger = LoggerFactory.getLogger(DistributedTestDriver.class);

  // Input
  public static final String JSON_PIR_INPUT_FILE_PROPERTY = "test.inputJSONFile";
  public static final String PIR_QUERY_INPUT_DIR = "test.queryInputDir";
  public static final String PIR_STOPLIST_FILE = "test.stopListFile";

  // Elastic Search
  public static final String ES_INPUT_NODES_PROPERTY = "es.nodes";
  public static final String ES_INPUT_PORT_PROPERTY = "es.port";
  public static final String ES_INPUT_INDEX_PROPERTY = "test.es.index";
  public static final String ES_INPUT_TYPE_PROPERTY = "test.es.type";
  public static final String ES_INPUT_RESOURCE_PROPERTY = "test.es.resource";

  // Output
  public static final String OUTPUT_DIRECTORY_PROPERTY = "test.outputHDFSFile";

  public static void main(String[] args) throws Exception
  {
    // create a cli object to handle all program inputs
    DistributedTestCLI cli = new DistributedTestCLI(args);

    logger.info("DistributedTest Suite Beginning");
    FileSystem fs = FileSystem.get(new Configuration());

    String jarFile = cli.getOptionValue("j");
    logger.info("jarFile = " + jarFile);
    SystemConfiguration.setProperty("jarFile", jarFile);

    List<JSONObject> dataElements = initialize(fs);

    // Pull off the properties and reset upon completion
    String dataSchemasProp = SystemConfiguration.getProperty("data.schemas", "none");
    String querySchemasProp = SystemConfiguration.getProperty("query.schemas", "none");
    String stopListFileProp = SystemConfiguration.getProperty("pir.stopListFile");

    test(fs, cli, dataElements);

    cleanup(fs, dataSchemasProp, querySchemasProp, stopListFileProp);
    logger.info("Distributed Test Suite Complete");
  }

  /**
   * Create all inputs
   */
  public static List<JSONObject> initialize(FileSystem fs) throws Exception
  {
    List<JSONObject> dataElements = Inputs.createJSONInput(fs);

    String localStopListFile = Inputs.createStopList(fs, true);

    SystemConfiguration.setProperty("pir.stopListFile", localStopListFile);

    Inputs.createSchemaFiles(fs, true, StopListFilter.class.getName());

    return dataElements;
  }

  /**
   * Execute Tests
   */
  public static void test(FileSystem fs, DistributedTestCLI cli, List<JSONObject> pirDataElements) throws Exception
  {
    // MapReduce JSON input
    if (cli.run("1:J"))
    {
      DistTestSuite.testJSONInputMR(fs, pirDataElements);
    }

    // Spark with JSON input
    if (cli.run("1:JS"))
    {
      DistTestSuite.testJSONInputSpark(fs, pirDataElements);
    }

    // Spark Streaming
    if (cli.run("1:SS"))
    {
      DistTestSuite.testSparkStreaming(fs, pirDataElements);
    }
    if (cli.run("1:JSS"))
    {
      DistTestSuite.testJSONInputSparkStreaming(fs, pirDataElements);
    }

    // Elasticsearch input
    if (cli.run("1:E") || cli.run("1:ES") || cli.run("1:ESS"))
    {
      Inputs.createESInput();
      if (cli.run("1:E"))
      {
        DistTestSuite.testESInputMR(fs, pirDataElements);
      }
      if (cli.run("1:ES"))
      {
        DistTestSuite.testESInputSpark(fs, pirDataElements);
      }
      if (cli.run("1:ESS"))
      {
        DistTestSuite.testESInputSparkStreaming(fs, pirDataElements);
      }
    }
  }

  /**
   * Delete all necessary inputs, clean up
   */
  public static void cleanup(FileSystem fs, String dataSchemasProp, String querySchemasProp, String stopListProp) throws Exception
  {
    Inputs.deleteESInput();
    fs.close();

    SystemConfiguration.setProperty("pir.stopListFile", stopListProp);

    // Force the query and data schemas to load their original values
    if (!dataSchemasProp.equals("none"))
    {
      DataSchemaLoader.initialize();
    }

    if (!querySchemasProp.equals("none"))
    {
      QuerySchemaLoader.initialize();
    }
  }
}
