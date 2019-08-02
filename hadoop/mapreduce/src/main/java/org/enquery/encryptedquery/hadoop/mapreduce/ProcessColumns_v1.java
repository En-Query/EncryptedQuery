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
package org.enquery.encryptedquery.hadoop.mapreduce;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties;
import org.enquery.encryptedquery.hadoop.core.IntBytesPairWritable;
import org.enquery.encryptedquery.hadoop.core.ProcessColumnsMapper_v1;
import org.enquery.encryptedquery.hadoop.core.ProcessColumnsReducer_v1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessColumns_v1 {

	private static final Logger log = LoggerFactory.getLogger(ProcessColumns_v1.class);

	public static boolean run(Map<String, String> jobConfig, QueryInfo queryInfo, FileSystem hdfs, Path inputPath,
			Path outputPath, java.nio.file.Path queryFilePath, java.nio.file.Path configFilePath) throws FileNotFoundException, IOException, ClassNotFoundException, InterruptedException {
		boolean success;

		Job job = Job.getInstance(hdfs.getConf(), "EQ-MR-ProcessColumns");
		job.setSpeculativeExecution(false);

		String jobName = "EQ-" + queryInfo.getQueryName() + "_Phase-2";
		job.setJobName(jobName);

		// Set timeout option
		if (jobConfig.containsKey(HadoopConfigurationProperties.MR_TASK_TIMEOUT)) {
			job.getConfiguration().set(HadoopConfigurationProperties.MR_TASK_TIMEOUT,
					jobConfig.get(HadoopConfigurationProperties.MR_TASK_TIMEOUT));
		}

		// Set the memory and heap options
		if (jobConfig.containsKey(HadoopConfigurationProperties.MR_MAP_MEMORY_MB)) {
	    	log.info("ProcessColumns Map Memory: {}",jobConfig.get(HadoopConfigurationProperties.MR_MAP_MEMORY_MB) );
			job.getConfiguration().set(HadoopConfigurationProperties.MR_MAP_MEMORY_MB,
					jobConfig.get(HadoopConfigurationProperties.MR_MAP_MEMORY_MB));
		}
		if (jobConfig.containsKey(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB)) {
	    	log.info("ProcessColumns Reduce Memory: {}",jobConfig.get(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB) );
			job.getConfiguration().set(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB,
					jobConfig.get(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB));
		}
		if (jobConfig.containsKey(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS)) {
	    	log.info("ProcessColumns Map Java Opts: {}",jobConfig.get(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS) );
			job.getConfiguration().set(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS,
					jobConfig.get(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS));
		}
		if (jobConfig.containsKey(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS)) {
	    	log.info("ProcessColumns Redice Java Opts: {}",jobConfig.get(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS) );
			job.getConfiguration().set(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS,
					jobConfig.get(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS));
		}

		// Set necessary files for Mapper setup
		job.getConfiguration().set("mapreduce.map.speculative", "false");
		job.getConfiguration().set("mapreduce.reduce.speculative", "false");

		// job.getConfiguration().set("pirWL.useLocalCache",
		// SystemConfiguration.getProperty("pir.useLocalCache", "true"));
		// job.getConfiguration().set("pirMR.queryInputDir",
		// SystemConfiguration.getProperty("pir.queryInput"));
		job.getConfiguration().set("hashBitSize", Integer.toString(queryInfo.getHashBitSize()));
		job.getConfiguration().set("dataChunkSize", Integer.toString(queryInfo.getDataChunkSize()));
		job.getConfiguration().set(HadoopConfigurationProperties.CHUNKING_BYTE_SIZE,
				jobConfig.get(HadoopConfigurationProperties.CHUNKING_BYTE_SIZE));
		job.getConfiguration().set(HadoopConfigurationProperties.HDFSWORKINGFOLDER,
				jobConfig.get(HadoopConfigurationProperties.HDFSWORKINGFOLDER));

		job.getConfiguration().set("queryFileName", queryFilePath.getFileName().toString());
		job.getConfiguration().set("configFileName", configFilePath.getFileName().toString());
		job.getConfiguration().set("hadoopWorkingFolder", jobConfig.get(HadoopConfigurationProperties.HDFSWORKINGFOLDER));

		job.setJarByClass(ProcessColumnsMapper_v1.class);
		job.setMapperClass(ProcessColumnsMapper_v1.class);
		job.setInputFormatClass(SequenceFileInputFormat.class);

		FileStatus[] status = hdfs.listStatus(inputPath);
		for (FileStatus fstat : status) {
			if (fstat.getPath().getName().startsWith(HadoopConfigurationProperties.EQ)) {
				log.info("fstat.getPath() = " + fstat.getPath().toString());
				FileInputFormat.addInputPath(job, fstat.getPath());
			}
		}

		job.setMapOutputKeyClass(IntWritable.class);
		job.setMapOutputValueClass(IntBytesPairWritable.class);

		// Set the reducer and output params
		job.setNumReduceTasks(Integer.valueOf(jobConfig.get(HadoopConfigurationProperties.HADOOP_REDUCE_TASKS)));
		job.setReducerClass(ProcessColumnsReducer_v1.class);
		job.setOutputKeyClass(LongWritable.class);
		job.setOutputValueClass(BytesWritable.class);

		// Delete the output directory if it exists
		if (hdfs.exists(outputPath)) {
			hdfs.delete(outputPath, true);
		}
		FileOutputFormat.setOutputPath(job, outputPath);

		MultipleOutputs.addNamedOutput(job, HadoopConfigurationProperties.EQ_COLS, SequenceFileOutputFormat.class,
				LongWritable.class, BytesWritable.class);
        log.info("Starting Process Columns MapReduce processing.");
		// Submit job, wait for completion
		success = job.waitForCompletion(true);

		return success;
	}
}