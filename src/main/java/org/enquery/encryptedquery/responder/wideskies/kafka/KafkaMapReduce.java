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
package org.enquery.encryptedquery.responder.wideskies.kafka;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.enquery.encryptedquery.inputformat.hadoop.BaseInputFormat;
import org.enquery.encryptedquery.inputformat.hadoop.BytesArrayWritable;
import org.enquery.encryptedquery.inputformat.hadoop.InputFormatConst;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.wideskies.mapreduce.ColumnMultMapper;
import org.enquery.encryptedquery.responder.wideskies.mapreduce.ColumnMultReducer;
import org.enquery.encryptedquery.responder.wideskies.mapreduce.FinalResponseMapper;
import org.enquery.encryptedquery.responder.wideskies.mapreduce.FinalResponseReducer;
import org.enquery.encryptedquery.responder.wideskies.mapreduce.HashSelectorsAndPartitionDataMapper;
import org.enquery.encryptedquery.responder.wideskies.mapreduce.RowCalcReducer;
import org.enquery.encryptedquery.schema.data.DataSchemaLoader;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaLoader;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.enquery.encryptedquery.utils.FileConst;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaMapReduce extends Configured implements Tool {

	  private static final Logger logger = LoggerFactory.getLogger(KafkaMapReduce.class);

	  private String dataInputFormat = null;
	  private String inputFolder = null;
	  private String outputFile = null;
	  private String outputDirExp = null;
	  private String outputDirInit = null;
	  private String outputDirColumnMult = null;
	  private String outputDirFinal = null;
	  private String queryInputDir = null;
	  private String stopListFile = null;
	  private int numReduceTasks = 1;
	  private int iteration = 0;

	  private Configuration conf = null;
	  private FileSystem fs = null;

	  private Query query = null;
	  private QueryInfo queryInfo = null;
	  private QuerySchema qSchema = null;

	private String workingFolder = null;
	
	public KafkaMapReduce(String workingFolder, int iteration) throws IOException, PIRException {
	
		this.workingFolder = workingFolder;
        this.iteration = iteration;	
        setupParameters();

        conf = new Configuration();
        fs = FileSystem.get(conf);

        // Load the schemas
        DataSchemaLoader.initialize(true, fs);
        QuerySchemaLoader.initialize(true, fs);
      
        this.query = new HadoopFileSystemStore(fs).recall(queryInputDir, Query.class);
        queryInfo = query.getQueryInfo();
        qSchema = QuerySchemaRegistry.get(queryInfo.getQueryType());
        
	}
	
	  private void setupParameters()
	  {
	    dataInputFormat = SystemConfiguration.getProperty("pir.dataInputFormat");
	    if (!InputFormatConst.ALLOWED_FORMATS.contains(dataInputFormat))
	    {
	      throw new IllegalArgumentException("inputFormat = " + dataInputFormat + " is of an unknown form");
	    }
	    logger.info("inputFormat = " + dataInputFormat);
	    if (dataInputFormat.equals(InputFormatConst.BASE_FORMAT))
	    {
	      inputFolder = workingFolder + "/data";
	      logger.info("inputFolder = " + inputFolder);
	    }

	    outputFile = workingFolder + "/" + SystemConfiguration.getProperty("pir.outputFile") + "-" + iteration;
	    outputDirInit = outputFile + "_init";
	    outputDirExp = outputFile + "_exp";
	    outputDirColumnMult = outputFile + "_colMult";
	    outputDirFinal = outputFile + "_final";
	    queryInputDir = SystemConfiguration.getProperty("pir.queryInput");
	    stopListFile = SystemConfiguration.getProperty("pir.stopListFile");

	    numReduceTasks = SystemConfiguration.getIntProperty("pir.numReduceTasks", 1);
	  }

	
	  @Override
	  public int run(String[] arg0) throws Exception
	  {
		
		   boolean success = true;

		    Path dataPath = new Path(inputFolder);
		    Path outPathInit = new Path(outputDirInit);
		    Path outPathColumnMult = new Path(outputDirColumnMult);
		    Path outPathFinal = new Path(outputDirFinal);
		    // Read the data, hash selectors, form encrypted rows
		    if (success)
		    {
		      success = readDataEncRows(outPathInit);
		    }

		    // Multiply the column values
		    if (success)
		    {
		      success = multiplyColumns(outPathInit, outPathColumnMult);
		    }

		    // Concatenate the output to one file
		    if (success)
		    {
		      success = computeFinalResponse(outPathFinal);
		    }

		    // Clean up
		    fs.delete(outPathInit, true);
		    fs.delete(outPathColumnMult, true);
		    fs.delete(outPathFinal, true);
		    fs.delete(dataPath, true);

		    return success ? 0 : 1;
	}
	  
	  @SuppressWarnings("unchecked")
	  private boolean readDataEncRows(Path outPathInit) throws Exception
	  {
	    boolean success;

	    logger.info("Configuring for Map/Reduce Phase 1");
	    Job job = Job.getInstance(conf, "pirMR");
	    job.setSpeculativeExecution(false);

	    // Set the data and query schema properties
	    job.getConfiguration().set("dataSchemaName", qSchema.getDataSchemaName());
	    job.getConfiguration().set("data.schemas", SystemConfiguration.getProperty("data.schemas"));
	    job.getConfiguration().set("query.schemas", SystemConfiguration.getProperty("query.schemas"));

	    // Set the memory and heap options
	    job.getConfiguration().set("mapreduce.map.memory.mb", SystemConfiguration.getProperty("mapreduce.map.memory.mb", "2000"));
	    job.getConfiguration().set("mapreduce.reduce.memory.mb", SystemConfiguration.getProperty("mapreduce.reduce.memory.mb", "2000"));
	    job.getConfiguration().set("mapreduce.map.java.opts", SystemConfiguration.getProperty("mapreduce.map.java.opts", "-Xmx1800m"));
	    job.getConfiguration().set("mapreduce.reduce.java.opts", SystemConfiguration.getProperty("mapreduce.reduce.java.opts", "-Xmx1800m"));

	    // Set necessary files for Mapper setup
	    job.getConfiguration().set("pirMR.queryInputDir", SystemConfiguration.getProperty("pir.queryInput"));
	    job.getConfiguration().set("pirMR.stopListFile", SystemConfiguration.getProperty("pir.stopListFile"));

	    job.getConfiguration().set("mapreduce.map.speculative", "false");
	    job.getConfiguration().set("mapreduce.reduce.speculative", "false");

	    job.getConfiguration().set("pirWL.useLocalCache", SystemConfiguration.getProperty("pir.useLocalCache", "true"));
	    job.getConfiguration().set("pirWL.limitHitsPerSelector", SystemConfiguration.getProperty("pir.limitHitsPerSelector", "false"));
	    job.getConfiguration().set("pirWL.maxHitsPerSelector", SystemConfiguration.getProperty("pir.maxHitsPerSelector", "100"));
	    job.getConfiguration().set("dataPartitionBitSize", Integer.toString(queryInfo.getDataPartitionBitSize()));
	    job.getConfiguration().set("hashBitSize", Integer.toString(queryInfo.getHashBitSize()));

	    if (dataInputFormat.equals(InputFormatConst.BASE_FORMAT))
	    {
          String baseQuery = SystemConfiguration.getProperty("pir.baseQuery");
	      String jobName = "EQ-MR-Query_" + qSchema.getSchemaName() + "_" + System.currentTimeMillis();
	      job.setJobName(jobName);

	      job.getConfiguration().set("baseQuery", baseQuery);
	      job.getConfiguration().set("query", baseQuery);
	      job.getConfiguration().set("pir.allowAdHocQuerySchemas", SystemConfiguration.getProperty("pir.allowAdHocQuerySchemas", "false"));

	      job.getConfiguration().setBoolean("mapreduce.input.fileinputformat.input.dir.recursive", true);

	      // Set the inputFormatClass based upon the baseInputFormat property
	      String classString = SystemConfiguration.getProperty("pir.baseInputFormat");
	      Class<BaseInputFormat> inputClass = (Class<BaseInputFormat>) Class.forName(classString);
	      if (!Class.forName("org.enquery.encryptedquery.inputformat.hadoop.BaseInputFormat").isAssignableFrom(inputClass))
	      {
	        throw new Exception("baseInputFormat class = " + classString + " does not extend BaseInputFormat");
	      }
	      job.setInputFormatClass(inputClass);

	      FileInputFormat.setInputPaths(job, inputFolder);
	    }

	    job.setJarByClass(HashSelectorsAndPartitionDataMapper.class);
	    job.setMapperClass(HashSelectorsAndPartitionDataMapper.class);

	    job.setMapOutputKeyClass(IntWritable.class);
	    job.setMapOutputValueClass(BytesArrayWritable.class);

	    // Set the reducer and output params
	    job.setNumReduceTasks(numReduceTasks);
	    job.setReducerClass(RowCalcReducer.class);

	    // Delete the output directory if it exists
	    if (fs.exists(outPathInit))
	    {
	      fs.delete(outPathInit, true);
	    }
	    job.setOutputKeyClass(LongWritable.class);
	    job.setOutputValueClass(Text.class);
	    FileOutputFormat.setOutputPath(job, outPathInit);
	    job.getConfiguration().set("mapreduce.output.textoutputformat.separator", ",");

	    MultipleOutputs.addNamedOutput(job, FileConst.PIR, TextOutputFormat.class, LongWritable.class, Text.class);

	    // Submit job, wait for completion
	    success = job.waitForCompletion(true);

	    return success;
	  }
	  
	  private boolean multiplyColumns(Path outPathInit, Path outPathColumnMult) throws IOException, ClassNotFoundException, InterruptedException
	  {
	    boolean success;

	    Job columnMultJob = Job.getInstance(conf, "pir_columnMult");
	    columnMultJob.setSpeculativeExecution(false);

	    String columnMultJobName = "pir_columnMult";

	    // Set the same job configs as for the first iteration
	    columnMultJob.getConfiguration().set("mapreduce.map.memory.mb", SystemConfiguration.getProperty("mapreduce.map.memory.mb", "2000"));
	    columnMultJob.getConfiguration().set("mapreduce.reduce.memory.mb", SystemConfiguration.getProperty("mapreduce.reduce.memory.mb", "2000"));
	    columnMultJob.getConfiguration().set("mapreduce.map.java.opts", SystemConfiguration.getProperty("mapreduce.map.java.opts", "-Xmx1800m"));
	    columnMultJob.getConfiguration().set("mapreduce.reduce.java.opts", SystemConfiguration.getProperty("mapreduce.reduce.java.opts", "-Xmx1800m"));

	    columnMultJob.getConfiguration().set("mapreduce.map.speculative", "false");
	    columnMultJob.getConfiguration().set("mapreduce.reduce.speculative", "false");
	    columnMultJob.getConfiguration().set("pirMR.queryInputDir", SystemConfiguration.getProperty("pir.queryInput"));
	    columnMultJob.getConfiguration().set("pirWL.useLocalCache", SystemConfiguration.getProperty("pir.useLocalCache", "true"));
	    columnMultJob.getConfiguration().set("dataPartitionBitSize", Integer.toString(queryInfo.getDataPartitionBitSize()));
	    columnMultJob.getConfiguration().set("numPartitionsPerElement", Integer.toString(queryInfo.getNumPartitionsPerDataElement()));
	    columnMultJob.getConfiguration().set("computeThreadPoolSize",  SystemConfiguration.getProperty("mapreduce.reduce.compute.threadPoolSize", "10"));
	    columnMultJob.getConfiguration().set("computePartitionsPerThread",  SystemConfiguration.getProperty("mapreduce.reduce.compute.partitionsPerThread", "1000"));

	    columnMultJob.setJobName(columnMultJobName);
	    columnMultJob.setJarByClass(ColumnMultMapper.class);
	    columnMultJob.setNumReduceTasks(numReduceTasks);

	    // Set the Mapper, InputFormat, and input path
	    columnMultJob.setMapperClass(ColumnMultMapper.class);
	    columnMultJob.setInputFormatClass(TextInputFormat.class);

	    FileStatus[] status = fs.listStatus(outPathInit);
	    for (FileStatus fstat : status)
	    {
	      if (fstat.getPath().getName().startsWith(FileConst.PIR))
	      {
	        logger.info("fstat.getPath() = " + fstat.getPath().toString());
	        FileInputFormat.addInputPath(columnMultJob, fstat.getPath());
	      }
	    }
	    columnMultJob.setMapOutputKeyClass(LongWritable.class);
	    columnMultJob.setMapOutputValueClass(Text.class);

	    // Set the reducer and output options
	    columnMultJob.setReducerClass(ColumnMultReducer.class);
	    columnMultJob.setOutputKeyClass(LongWritable.class);
	    columnMultJob.setOutputValueClass(Text.class);
	    columnMultJob.getConfiguration().set("mapreduce.output.textoutputformat.separator", ",");

	    // Delete the output file, if it exists
	    if (fs.exists(outPathColumnMult))
	    {
	      fs.delete(outPathColumnMult, true);
	    }
	    FileOutputFormat.setOutputPath(columnMultJob, outPathColumnMult);

	    MultipleOutputs.addNamedOutput(columnMultJob, FileConst.PIR_COLS, TextOutputFormat.class, LongWritable.class, Text.class);

	    // Submit job, wait for completion
	    success = columnMultJob.waitForCompletion(true);

	    return success;
	  }
	  
	  private boolean computeFinalResponse(Path outPathFinal) throws ClassNotFoundException, IOException, InterruptedException
	  {
	    boolean success;

	    Job finalResponseJob = Job.getInstance(conf, "pir_finalResponse");
	    finalResponseJob.setSpeculativeExecution(false);

	    String finalResponseJobName = "pir_finalResponse";

	    // Set the same job configs as for the first iteration
	    finalResponseJob.getConfiguration().set("mapreduce.map.memory.mb", SystemConfiguration.getProperty("mapreduce.map.memory.mb", "2000"));
	    finalResponseJob.getConfiguration().set("mapreduce.reduce.memory.mb", SystemConfiguration.getProperty("mapreduce.reduce.memory.mb", "2000"));
	    finalResponseJob.getConfiguration().set("mapreduce.map.java.opts", SystemConfiguration.getProperty("mapreduce.map.java.opts", "-Xmx1800m"));
	    finalResponseJob.getConfiguration().set("mapreduce.reduce.java.opts", SystemConfiguration.getProperty("mapreduce.reduce.java.opts", "-Xmx1800m"));

	    finalResponseJob.getConfiguration().set("pirMR.queryInputDir", SystemConfiguration.getProperty("pir.queryInput"));
	    finalResponseJob.getConfiguration().set("pirMR.outputFile", outputFile);

	    finalResponseJob.getConfiguration().set("mapreduce.map.speculative", "false");
	    finalResponseJob.getConfiguration().set("mapreduce.reduce.speculative", "false");

	    finalResponseJob.setJobName(finalResponseJobName);
	    finalResponseJob.setJarByClass(ColumnMultMapper.class);
	    finalResponseJob.setNumReduceTasks(1);

	    // Set the Mapper, InputFormat, and input path
	    finalResponseJob.setMapperClass(FinalResponseMapper.class);
	    finalResponseJob.setInputFormatClass(TextInputFormat.class);

	    FileStatus[] status = fs.listStatus(new Path(outputDirColumnMult));
	    for (FileStatus fstat : status)
	    {
	      if (fstat.getPath().getName().startsWith(FileConst.PIR_COLS))
	      {
	        logger.info("fstat.getPath() = " + fstat.getPath().toString());
	        FileInputFormat.addInputPath(finalResponseJob, fstat.getPath());
	      }
	    }
	    finalResponseJob.setMapOutputKeyClass(LongWritable.class);
	    finalResponseJob.setMapOutputValueClass(Text.class);

	    // Set the reducer and output options
	    finalResponseJob.setReducerClass(FinalResponseReducer.class);
	    finalResponseJob.setOutputKeyClass(LongWritable.class);
	    finalResponseJob.setOutputValueClass(Text.class);
	    finalResponseJob.getConfiguration().set("mapreduce.output.textoutputformat.separator", ",");

	    // Delete the output file, if it exists
	    if (fs.exists(outPathFinal))
	    {
	      fs.delete(outPathFinal, true);
	    }
	    FileOutputFormat.setOutputPath(finalResponseJob, outPathFinal);
	    MultipleOutputs.addNamedOutput(finalResponseJob, FileConst.PIR_FINAL, TextOutputFormat.class, LongWritable.class, Text.class);

	    // Submit job, wait for completion
	    success = finalResponseJob.waitForCompletion(true);

	    return success;
	  }
}
