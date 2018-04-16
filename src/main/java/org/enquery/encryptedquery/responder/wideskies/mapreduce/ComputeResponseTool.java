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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.elasticsearch.hadoop.mr.EsInputFormat;
import org.enquery.encryptedquery.inputformat.hadoop.BaseInputFormat;
import org.enquery.encryptedquery.inputformat.hadoop.BytesArrayWritable;
import org.enquery.encryptedquery.inputformat.hadoop.InputFormatConst;
import org.enquery.encryptedquery.inputformat.hadoop.IntPairWritable;
import org.enquery.encryptedquery.inputformat.hadoop.IntBytesPairWritable;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.schema.data.DataSchemaLoader;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaLoader;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.enquery.encryptedquery.utils.FileConst;
import org.enquery.encryptedquery.utils.HDFS;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool for computing the PIR response in MapReduce
 * <p>
 * Each query run consists of two MR jobs:
 * <p>
 * (1) Map: Reads data using an extension of the BaseInputFormat or
 * Elasticsearch.  For each data element, extracts the selector
 * according to the QueryType and compute the hash value.  The range
 * of possible hash values is divided into subranges and the group ID
 * of the hash value is determined.  Outputs {@code <groupId,
 * (hash,dataElement)>}.
 * <p>
 * Reduce: Calculates the encrypted row values for each selector and
 * corresponding data element, striping across columns.  Encrypted
 * values corresponding to different hash values in the subrange are
 * multiplied together.  Groups of encrypted columns (with as many
 * columns as the number of parts in a partitioned data element) are
 * output key-value pairs {@code <startingColNum, listOfColumns>}.
 * <p>
 * (2) Map: Reads pairs {@code <startingColNum, listOfColumns>} from
 * the previous job and passes them through unchanged.
 * <p>
 * Reduce: Multiply together column groups corresponding to the same
 * starting column number.  Each reducer task returns a {@code
 * Response} object.
 * <P>
 * NOTE: If useHDFSExpLookupTable in the QueryInfo object is true, then the expLookupTable for the watchlist must be generated if it does not already exist in
 * hdfs.
 * <p>
 * TODO:
 * <p>
 * -Currently processes one query at time - can change to process multiple queries at the same time (under the same time interval and with same query
 * parameters) - using MultipleOutputs for extensibility to multiple queries per job later...
 * <p>
 * - Could place Query objects in DistributedCache (instead of a direct file based pull in task setup)
 * <p>
 * - Redesign exp lookup table to be smart and fully distributed/partitioned
 */
public class ComputeResponseTool extends Configured implements Tool
{
  private static final Logger logger = LoggerFactory.getLogger(ComputeResponseTool.class);

  private String dataInputFormat = null;
  private String inputFile = null;
  private String outputFile = null;
  private String outputDirExp = null;
  private String outputDirInit = null;
  private String outputDirColumnMult = null;
  private String outputDirFinal = null;
  private String queryInputDir = null;
  private String stopListFile = null;
  private int numReduceTasks = 1;

  private boolean useHDFSLookupTable = false;

  private String esQuery = "none";
  private String esResource = "none";

  private Configuration conf = null;
  private FileSystem fs = null;

  private Query query = null;
  private QueryInfo queryInfo = null;
  private QuerySchema qSchema = null;

  public ComputeResponseTool() throws IOException, PIRException
  {
    setupParameters();

    conf = new Configuration();
    fs = FileSystem.get(conf);

    // Load the schemas
    DataSchemaLoader.initialize(true, fs);
    QuerySchemaLoader.initialize(true, fs);

    query = new HadoopFileSystemStore(fs).recall(queryInputDir, Query.class);
    queryInfo = query.getQueryInfo();
    if (SystemConfiguration.getBooleanProperty("pir.allowAdHocQuerySchemas", false))
    {
      qSchema = queryInfo.getQuerySchema();
    }
    if (qSchema == null)
    {
      qSchema = QuerySchemaRegistry.get(queryInfo.getQueryType());
    }

    logger.info("outputFile = " + outputFile + " outputDirInit = " + outputDirInit + " outputDirColumnMult = " + outputDirColumnMult + " queryInputDir = "
        + queryInputDir + " stopListFile = " + stopListFile + " numReduceTasks = " + numReduceTasks + " esQuery = " + esQuery + " esResource = " + esResource);
  }

  @Override
  public int run(String[] arg0) throws Exception
  {
    boolean success = true;

    Path outPathInit = new Path(outputDirInit);
    Path outPathColumnMult = new Path(outputDirColumnMult);
    Path outPathFinal = new Path(outputDirFinal);

    // If we are using distributed exp tables -- Create the expTable file in hdfs for this query, if it doesn't exist
    if ((queryInfo.useHDFSExpLookupTable() || useHDFSLookupTable) && query.getExpFileBasedLookup().isEmpty())
    {
      success = computeExpTable();
    }

    // Read and sort the data by row (selector hash)
    if (success)
    {
      success = sortDataIntoRows(outPathInit);
    }

    // Compute a partial response for each hash range
    if (success)
    {
//      success = processHashRanges(outPathColumnMult);
      success = processColumns(outPathColumnMult);
    }

    // Concatenate the output to one file
    if (success)
    {
//      success = combineHashRangeResults(outPathFinal);
      success = combineColumnResults(outPathFinal);
    }

    // XXX
    // Clean up
    fs.delete(outPathInit, true);
    fs.delete(outPathColumnMult, true);
    fs.delete(outPathFinal, true);

    return success ? 0 : 1;
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
      inputFile = SystemConfiguration.getProperty("pir.inputData", "none");
      if (inputFile.equals("none"))
      {
        throw new IllegalArgumentException("For inputFormat = " + dataInputFormat + " an inputFile must be specified");
      }
      logger.info("inputFile = " + inputFile);
    }
    else if (dataInputFormat.equals(InputFormatConst.ES))
    {
      esQuery = SystemConfiguration.getProperty("pir.esQuery", "none");
      esResource = SystemConfiguration.getProperty("pir.esResource", "none");
      if (esQuery.equals("none"))
      {
        throw new IllegalArgumentException("esQuery must be specified");
      }
      if (esResource.equals("none"))
      {
        throw new IllegalArgumentException("esResource must be specified");
      }
      logger.info("esQuery = " + esQuery + " esResource = " + esResource);
    }

    outputFile = SystemConfiguration.getProperty("pir.outputFile");
    outputDirInit = outputFile + "_init";
    outputDirExp = outputFile + "_exp";
    outputDirColumnMult = outputFile + "_colMult";
    outputDirFinal = outputFile + "_final";
    queryInputDir = SystemConfiguration.getProperty("pir.queryInput");
    stopListFile = SystemConfiguration.getProperty("pir.stopListFile");

    useHDFSLookupTable = SystemConfiguration.isSetTrue("pir.useHDFSLookupTable");

    numReduceTasks = SystemConfiguration.getIntProperty("pir.numReduceTasks", 1);
  }

  private boolean computeExpTable() throws IOException, ClassNotFoundException, InterruptedException
  {
    boolean success;

    logger.info("Creating expTable");

    // The split location for the interim calculations, delete upon completion
    Path splitDir = new Path("/tmp/splits-" + queryInfo.getIdentifier());
    if (fs.exists(splitDir))
    {
      fs.delete(splitDir, true);
    }
    // Write the query hashes to the split files
    Map<Integer,BigInteger> queryElements = query.getQueryElements();
    List<Integer> keys = new ArrayList<>(queryElements.keySet());

    int numSplits = SystemConfiguration.getIntProperty("pir.expCreationSplits", 100);
    int elementsPerSplit = queryElements.size() / numSplits; // Integral division.
    logger.info("numSplits = " + numSplits + " elementsPerSplit = " + elementsPerSplit);
    for (int i = 0; i < numSplits; ++i)
    {
      // Grab the range of the thread
      int start = i * elementsPerSplit;
      int stop = start + elementsPerSplit - 1;
      if (i == (numSplits - 1))
      {
        stop = queryElements.size() - 1;
      }
      HDFS.writeFileIntegers(keys.subList(start, stop), fs, new Path(splitDir, "split-" + i), false);
    }

    // Run the job to generate the expTable
    // Job jobExp = new Job(mrConfig.getConfig(), "pirExp-" + pirWL.getWatchlistNum());
    Job jobExp = Job.getInstance(conf, "pirExp-" + queryInfo.getIdentifier());

    jobExp.setSpeculativeExecution(false);
    jobExp.getConfiguration().set("mapreduce.map.speculative", "false");
    jobExp.getConfiguration().set("mapreduce.reduce.speculative", "false");

    // Set the memory and heap options
    jobExp.getConfiguration().set("mapreduce.map.memory.mb", SystemConfiguration.getProperty("mapreduce.map.memory.mb", "10000"));
    jobExp.getConfiguration().set("mapreduce.reduce.memory.mb", SystemConfiguration.getProperty("mapreduce.reduce.memory.mb", "10000"));
    jobExp.getConfiguration().set("mapreduce.map.java.opts", SystemConfiguration.getProperty("mapreduce.map.java.opts", "-Xmx9000m"));
    jobExp.getConfiguration().set("mapreduce.reduce.java.opts", SystemConfiguration.getProperty("mapreduce.reduce.java.opts", "-Xmx9000m"));
    jobExp.getConfiguration().set("mapreduce.reduce.shuffle.parallelcopies", "5");

    jobExp.getConfiguration().set("pirMR.queryInputDir", SystemConfiguration.getProperty("pir.queryInput"));
    jobExp.getConfiguration().setBoolean("mapreduce.input.fileinputformat.input.dir.recursive", true);

    jobExp.setInputFormatClass(TextInputFormat.class);
    FileInputFormat.setInputPaths(jobExp, splitDir);

    jobExp.setJarByClass(ExpTableMapper.class);
    jobExp.setMapperClass(ExpTableMapper.class);

    jobExp.setMapOutputKeyClass(Text.class);
    jobExp.setMapOutputValueClass(Text.class);

    // Set the reducer and output params
    int numExpLookupPartitions = SystemConfiguration.getIntProperty("pir.numExpLookupPartitions", 100);
    jobExp.setNumReduceTasks(numExpLookupPartitions);
    jobExp.setReducerClass(ExpTableReducer.class);

    // Delete the output directory if it exists
    Path outPathExp = new Path(outputDirExp);
    if (fs.exists(outPathExp))
    {
      fs.delete(outPathExp, true);
    }
    jobExp.setOutputKeyClass(Text.class);
    jobExp.setOutputValueClass(Text.class);
    FileOutputFormat.setOutputPath(jobExp, outPathExp);
    jobExp.getConfiguration().set("mapreduce.output.textoutputformat.separator", ",");
    MultipleOutputs.addNamedOutput(jobExp, FileConst.PIR, TextOutputFormat.class, Text.class, Text.class);
    MultipleOutputs.addNamedOutput(jobExp, FileConst.EXP, TextOutputFormat.class, Text.class, Text.class);

    // Submit job, wait for completion
    success = jobExp.waitForCompletion(true);

    // Assemble the exp table from the output
    // element_index -> fileName
    Map<Integer,String> expFileTable = new HashMap<>();
    FileStatus[] status = fs.listStatus(outPathExp);
    for (FileStatus fstat : status)
    {
      if (fstat.getPath().getName().startsWith(FileConst.PIR))
      {
        logger.info("fstat.getPath().getName().toString() = " + fstat.getPath().getName());
        try
        {
          try (BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(fstat.getPath()))))
          {
            String line;
            while ((line = br.readLine()) != null)
            {
              String[] rowValTokens = line.split(","); // form is element_index,reducerNumber
              String fileName = fstat.getPath().getParent() + "/" + FileConst.EXP + "-r-" + rowValTokens[1];
              logger.info("fileName = " + fileName);
              expFileTable.put(Integer.parseInt(rowValTokens[0]), fileName);
            }
          }
        } catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    }

    // Place exp table in query object
    query.setExpFileBasedLookup(expFileTable);
    new HadoopFileSystemStore(fs).store(queryInputDir, query);

    logger.info("Completed creation of expTable");

    return success;
  }

  private boolean sortDataIntoRows(Path outPathInit) throws Exception
  {
    boolean success;

    Job job = Job.getInstance(conf, "pirMR");
    job.setSpeculativeExecution(false);

    // Set the data and query schema properties
    job.getConfiguration().set("dataSchemaName", qSchema.getDataSchemaName());
    job.getConfiguration().set("data.schemas", SystemConfiguration.getProperty("data.schemas"));
    job.getConfiguration().set("query.schemas", SystemConfiguration.getProperty("query.schemas"));

    // Set the memory and heap options
    job.getConfiguration().set("mapreduce.map.memory.mb", SystemConfiguration.getProperty("mapreduce.map.memory.mb"));
    job.getConfiguration().set("mapreduce.reduce.memory.mb", SystemConfiguration.getProperty("mapreduce.reduce.memory.mb"));
    job.getConfiguration().set("mapreduce.map.java.opts", SystemConfiguration.getProperty("mapreduce.map.java.opts"));
    job.getConfiguration().set("mapreduce.reduce.java.opts", SystemConfiguration.getProperty("mapreduce.reduce.java.opts"));

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

    if (dataInputFormat.equals(InputFormatConst.ES))
    {
      String jobName = "pirMR_es_" + esResource + "_" + esQuery + "_" + System.currentTimeMillis();
      job.setJobName(jobName);

      job.getConfiguration().set("es.nodes", SystemConfiguration.getProperty("es.nodes"));
      job.getConfiguration().set("es.port", SystemConfiguration.getProperty("es.port"));
      job.getConfiguration().set("es.resource", esResource);
      job.getConfiguration().set("es.query", esQuery);

      job.setInputFormatClass(EsInputFormat.class);
    }
    else if (dataInputFormat.equals(InputFormatConst.BASE_FORMAT))
    {
      String baseQuery = SystemConfiguration.getProperty("pir.baseQuery");
      String jobName = "pirMR_base_" + baseQuery + "_" + System.currentTimeMillis();
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

      FileInputFormat.setInputPaths(job, inputFile);
    }

    job.setJarByClass(SortDataIntoRowsMapper.class);
    job.setMapperClass(SortDataIntoRowsMapper.class);

    // mapper outputs (hash, dataElement)
    job.setMapOutputKeyClass(IntWritable.class);
    job.setMapOutputValueClass(BytesWritable.class);

    // Set the reducer and output params
    job.setNumReduceTasks(numReduceTasks);
    job.setReducerClass(SortDataIntoRowsReducer.class);
    // reducer outputs ((hash, col), dataBytes)
    job.setOutputKeyClass(IntPairWritable.class);
    job.setOutputValueClass(BytesWritable.class);

    // Delete the output directory if it exists
    if (fs.exists(outPathInit))
    {
      fs.delete(outPathInit, true);
    }
    FileOutputFormat.setOutputPath(job, outPathInit);
    MultipleOutputs.addNamedOutput(job, FileConst.PIR, SequenceFileOutputFormat.class, IntPairWritable.class, BytesWritable.class);

    // Submit job, wait for completion
    success = job.waitForCompletion(true);

    return success;
  }

  private boolean processHashRanges(Path outPathColumnMult) throws Exception
  {
    boolean success;

    Job job = Job.getInstance(conf, "pirMR");
    job.setSpeculativeExecution(false);

    // Set the memory and heap options
    job.getConfiguration().set("mapreduce.map.memory.mb", SystemConfiguration.getProperty("mapreduce.map.memory.mb"));
    job.getConfiguration().set("mapreduce.reduce.memory.mb", SystemConfiguration.getProperty("mapreduce.reduce.memory.mb"));
    job.getConfiguration().set("mapreduce.map.java.opts", SystemConfiguration.getProperty("mapreduce.map.java.opts"));
    job.getConfiguration().set("mapreduce.reduce.java.opts", SystemConfiguration.getProperty("mapreduce.reduce.java.opts"));

    // Set necessary files for Mapper setup
    job.getConfiguration().set("mapreduce.map.speculative", "false");
    job.getConfiguration().set("mapreduce.reduce.speculative", "false");

    job.getConfiguration().set("pirWL.useLocalCache", SystemConfiguration.getProperty("pir.useLocalCache", "true"));
    job.getConfiguration().set("pirMR.queryInputDir", SystemConfiguration.getProperty("pir.queryInput"));
    job.getConfiguration().set("dataPartitionBitSize", Integer.toString(queryInfo.getDataPartitionBitSize()));
    job.getConfiguration().set("numPartsPerElement", Integer.toString(queryInfo.getNumPartitionsPerDataElement()));
    job.getConfiguration().set("hashBitSize", Integer.toString(queryInfo.getHashBitSize()));

    int hashGroupSize;
    String hashGroupSizeStr = SystemConfiguration.getProperty("pir.hashGroupSize");
    if (null == hashGroupSizeStr)
    {
      // ceil(2^hb / nr)
      hashGroupSize = ((1 << queryInfo.getHashBitSize()) + numReduceTasks - 1) / numReduceTasks;
    }
    else
    {
      hashGroupSize = Integer.valueOf(hashGroupSizeStr);
    }
    job.getConfiguration().set("hashGroupSize", Integer.toString(hashGroupSize));

    job.setJarByClass(ProcessHashRangesMapper.class);
    job.setMapperClass(ProcessHashRangesMapper.class);
    job.setInputFormatClass(SequenceFileInputFormat.class);

    FileStatus[] status = fs.listStatus(new Path(outputDirInit));
    for (FileStatus fstat : status)
    {
      if (fstat.getPath().getName().startsWith(FileConst.PIR))
      {
        logger.info("fstat.getPath() = " + fstat.getPath().toString());
        FileInputFormat.addInputPath(job, fstat.getPath());
      }
    }

    job.setMapOutputKeyClass(LongWritable.class);
    job.setMapOutputValueClass(IntBytesPairWritable.class);

    // Set the reducer and output params
    job.setNumReduceTasks(numReduceTasks);
    job.setReducerClass(ProcessHashRangesReducer.class);
    job.setOutputKeyClass(LongWritable.class);
    job.setOutputValueClass(BytesArrayWritable.class);

    // Delete the output directory if it exists
    if (fs.exists(outPathColumnMult))
    {
      fs.delete(outPathColumnMult, true);
    }
    FileOutputFormat.setOutputPath(job, outPathColumnMult);

    MultipleOutputs.addNamedOutput(job, FileConst.PIR_COLS, SequenceFileOutputFormat.class, LongWritable.class, BytesArrayWritable.class);

    // Submit job, wait for completion
    success = job.waitForCompletion(true);

    return success;
  }

  private boolean combineHashRangeResults(Path outPathFinal) throws ClassNotFoundException, IOException, InterruptedException
  {
    boolean success;

    Job finalResponseJob = Job.getInstance(conf, "pir_finalResponse");
    finalResponseJob.setSpeculativeExecution(false);

    String finalResponseJobName = "pir_finalResponse";

    // Set the same job configs as for the first iteration
    finalResponseJob.getConfiguration().set("mapreduce.map.memory.mb", SystemConfiguration.getProperty("mapreduce.map.memory.mb"));
    finalResponseJob.getConfiguration().set("mapreduce.reduce.memory.mb", SystemConfiguration.getProperty("mapreduce.reduce.memory.mb"));
    finalResponseJob.getConfiguration().set("mapreduce.map.java.opts", SystemConfiguration.getProperty("mapreduce.map.java.opts"));
    finalResponseJob.getConfiguration().set("mapreduce.reduce.java.opts", SystemConfiguration.getProperty("mapreduce.reduce.java.opts"));

    finalResponseJob.getConfiguration().set("pirMR.queryInputDir", SystemConfiguration.getProperty("pir.queryInput"));
    finalResponseJob.getConfiguration().set("pirMR.outputFile", outputFile);
    finalResponseJob.getConfiguration().set("numPartsPerElement", Integer.toString(queryInfo.getNumPartitionsPerDataElement()));

    finalResponseJob.getConfiguration().set("mapreduce.map.speculative", "false");
    finalResponseJob.getConfiguration().set("mapreduce.reduce.speculative", "false");

    finalResponseJob.setJobName(finalResponseJobName);
    finalResponseJob.setJarByClass(CombineHashRangeResultsMapper.class);
    finalResponseJob.setNumReduceTasks(numReduceTasks);

    // Set the Mapper, InputFormat, and input path
    finalResponseJob.setMapperClass(CombineHashRangeResultsMapper.class);
    finalResponseJob.setInputFormatClass(SequenceFileInputFormat.class);

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
    finalResponseJob.setMapOutputValueClass(BytesArrayWritable.class);

    // Set the reducer and output options
    finalResponseJob.setReducerClass(CombineHashRangeResultsReducer.class);
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

  private boolean processColumns(Path outPathColumnMult) throws Exception
  {
    boolean success;

    Job job = Job.getInstance(conf, "pirMR");
    job.setSpeculativeExecution(false);

    // Set the memory and heap options
    job.getConfiguration().set("mapreduce.map.memory.mb", SystemConfiguration.getProperty("mapreduce.map.memory.mb"));
    job.getConfiguration().set("mapreduce.reduce.memory.mb", SystemConfiguration.getProperty("mapreduce.reduce.memory.mb"));
    job.getConfiguration().set("mapreduce.map.java.opts", SystemConfiguration.getProperty("mapreduce.map.java.opts"));
    job.getConfiguration().set("mapreduce.reduce.java.opts", SystemConfiguration.getProperty("mapreduce.reduce.java.opts"));

    // Set necessary files for Mapper setup
    job.getConfiguration().set("mapreduce.map.speculative", "false");
    job.getConfiguration().set("mapreduce.reduce.speculative", "false");

    job.getConfiguration().set("pirWL.useLocalCache", SystemConfiguration.getProperty("pir.useLocalCache", "true"));
    job.getConfiguration().set("pirMR.queryInputDir", SystemConfiguration.getProperty("pir.queryInput"));
    job.getConfiguration().set("dataPartitionBitSize", Integer.toString(queryInfo.getDataPartitionBitSize()));
    job.getConfiguration().set("numPartsPerElement", Integer.toString(queryInfo.getNumPartitionsPerDataElement()));
    job.getConfiguration().set("hashBitSize", Integer.toString(queryInfo.getHashBitSize()));

    job.setJarByClass(ProcessColumnsMapper.class);
    job.setMapperClass(ProcessColumnsMapper.class);
    job.setInputFormatClass(SequenceFileInputFormat.class);

    FileStatus[] status = fs.listStatus(new Path(outputDirInit));
    for (FileStatus fstat : status)
    {
      if (fstat.getPath().getName().startsWith(FileConst.PIR))
      {
        logger.info("fstat.getPath() = " + fstat.getPath().toString());
        FileInputFormat.addInputPath(job, fstat.getPath());
      }
    }

    job.setMapOutputKeyClass(IntWritable.class);
    job.setMapOutputValueClass(IntBytesPairWritable.class);

    // Set the reducer and output params
    job.setNumReduceTasks(numReduceTasks);
    job.setReducerClass(ProcessColumnsReducer.class);
    job.setOutputKeyClass(LongWritable.class);
    job.setOutputValueClass(BytesWritable.class);

    // Delete the output directory if it exists
    if (fs.exists(outPathColumnMult))
    {
      fs.delete(outPathColumnMult, true);
    }
    FileOutputFormat.setOutputPath(job, outPathColumnMult);

    MultipleOutputs.addNamedOutput(job, FileConst.PIR_COLS, SequenceFileOutputFormat.class, LongWritable.class, BytesWritable.class);

    // Submit job, wait for completion
    success = job.waitForCompletion(true);

    return success;
  }

  private boolean combineColumnResults(Path outPathFinal) throws ClassNotFoundException, IOException, InterruptedException
  {
    boolean success;

    Job finalResponseJob = Job.getInstance(conf, "pir_finalResponse");
    finalResponseJob.setSpeculativeExecution(false);

    String finalResponseJobName = "pir_finalResponse";

    // Set the same job configs as for the first iteration
    finalResponseJob.getConfiguration().set("mapreduce.map.memory.mb", SystemConfiguration.getProperty("mapreduce.map.memory.mb"));
    finalResponseJob.getConfiguration().set("mapreduce.reduce.memory.mb", SystemConfiguration.getProperty("mapreduce.reduce.memory.mb"));
    finalResponseJob.getConfiguration().set("mapreduce.map.java.opts", SystemConfiguration.getProperty("mapreduce.map.java.opts"));
    finalResponseJob.getConfiguration().set("mapreduce.reduce.java.opts", SystemConfiguration.getProperty("mapreduce.reduce.java.opts"));

    finalResponseJob.getConfiguration().set("pirMR.queryInputDir", SystemConfiguration.getProperty("pir.queryInput"));
    finalResponseJob.getConfiguration().set("pirMR.outputFile", outputFile);
    finalResponseJob.getConfiguration().set("numPartsPerElement", Integer.toString(queryInfo.getNumPartitionsPerDataElement()));

    finalResponseJob.getConfiguration().set("mapreduce.map.speculative", "false");
    finalResponseJob.getConfiguration().set("mapreduce.reduce.speculative", "false");

    finalResponseJob.setJobName(finalResponseJobName);
    finalResponseJob.setJarByClass(CombineHashRangeResultsMapper.class);
    finalResponseJob.setNumReduceTasks(1);

    // Set the Mapper, InputFormat, and input path
    finalResponseJob.setMapperClass(CombineColumnResultsMapper.class);
    finalResponseJob.setInputFormatClass(SequenceFileInputFormat.class);

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
    finalResponseJob.setMapOutputValueClass(BytesWritable.class);

    // Set the reducer and output options
    finalResponseJob.setReducerClass(CombineColumnResultsReducer.class);
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
