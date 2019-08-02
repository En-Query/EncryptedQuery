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

import java.util.Map;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.Job;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties;
import org.enquery.encryptedquery.hadoop.core.IntPairWritable;
import org.enquery.encryptedquery.hadoop.core.SortDataIntoRowsMapper_v1;
import org.enquery.encryptedquery.hadoop.core.SortDataIntoRowsReducer_v1;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SortDataIntoRows {
	
	public static boolean run(Path inputFile, QueryInfo queryInfo, Map<String, String> jobConfig, FileSystem hdfs, 
			Path outputPath) throws Exception
	{
    	final Logger log = LoggerFactory.getLogger(SortDataIntoRowsMapper_v1.class);

		boolean success = false;
		
	    Job job = Job.getInstance(hdfs.getConf(), "EQ-MR-Sort");
	    job.setSpeculativeExecution(false);
		String jobName = "EQ-" + queryInfo.getQueryName() + "_Phase-1";
		job.setJobName(jobName);
	    
	    // Set the memory and heap options
	    if (jobConfig.containsKey(HadoopConfigurationProperties.MR_MAP_MEMORY_MB)) {
	    	log.info("SortDataIntoRowsMapper Map Memory: {}",jobConfig.get(HadoopConfigurationProperties.MR_MAP_MEMORY_MB) );
	    	job.getConfiguration().set(HadoopConfigurationProperties.MR_MAP_MEMORY_MB, jobConfig.get(HadoopConfigurationProperties.MR_MAP_MEMORY_MB));
	    }
	    if (jobConfig.containsKey(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB)) {
	    	log.info("SortDataIntoRowsMapper Reduce Memory: {}",jobConfig.get(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB) );
	    	job.getConfiguration().set(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB, jobConfig.get(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB));
	    }
	    if (jobConfig.containsKey(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS)) {
	    	log.info("SortDataIntoRowsMapper Map Java Opts: {}",jobConfig.get(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS) );
	    	job.getConfiguration().set(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS, jobConfig.get(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS));
	    }
	    if (jobConfig.containsKey(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS)) {
	    	log.info("SortDataIntoRowsMapper Reduce Java Opts: {}",jobConfig.get(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS) );
	    	job.getConfiguration().set(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS, jobConfig.get(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS));
	    }
	    job.getConfiguration().set("mapreduce.map.speculative", "false");
	    job.getConfiguration().set("mapreduce.reduce.speculative", "false");

	    job.getConfiguration().set(ResponderProperties.MAX_HITS_PER_SELECTOR, jobConfig.get(ResponderProperties.MAX_HITS_PER_SELECTOR));
	    job.getConfiguration().setBoolean(ResponderProperties.LIMIT_HITS_PER_SELECTOR, Boolean.valueOf(jobConfig.get(ResponderProperties.LIMIT_HITS_PER_SELECTOR)));
	    job.getConfiguration().set("dataChunkSize", Integer.toString(queryInfo.getDataChunkSize()));
	    job.getConfiguration().set(HadoopConfigurationProperties.CHUNKING_BYTE_SIZE, jobConfig.get(HadoopConfigurationProperties.CHUNKING_BYTE_SIZE));
        job.getConfiguration().set(HadoopConfigurationProperties.HDFSWORKINGFOLDER, jobConfig.get(HadoopConfigurationProperties.HDFSWORKINGFOLDER));
        
         job.setInputFormatClass(TextInputFormat.class);
        FileInputFormat.setInputPaths(job, inputFile);
        
	    job.setJarByClass(SortDataIntoRowsMapper_v1.class);
	    job.setMapperClass(SortDataIntoRowsMapper_v1.class);

	    // mapper outputs (hash, dataElement)
	    job.setMapOutputKeyClass(IntWritable.class);
	    job.setMapOutputValueClass(BytesWritable.class);

	    // Set the reducer and output params
	    job.setNumReduceTasks(Integer.valueOf(jobConfig.get(HadoopConfigurationProperties.HADOOP_REDUCE_TASKS)));

	    job.setReducerClass(SortDataIntoRowsReducer_v1.class);

	    // reducer outputs ((hash, col), dataBytes)
	    job.setOutputKeyClass(IntPairWritable.class);
	    job.setOutputValueClass(BytesWritable.class);

		// Delete the output directory if it exists
		if (hdfs.exists(outputPath)) {
			hdfs.delete(outputPath, true);
		}
	    
	    FileOutputFormat.setOutputPath(job, outputPath);
	    MultipleOutputs.addNamedOutput(job, HadoopConfigurationProperties.EQ, SequenceFileOutputFormat.class, IntPairWritable.class, BytesWritable.class);
		// Delete the output directory if it exists

	    log.info("Starting SortDataIntoRows MapReduce processing.");
	    // Submit job, wait for completion
	    success = job.waitForCompletion(true);

	    return success;
	}

}