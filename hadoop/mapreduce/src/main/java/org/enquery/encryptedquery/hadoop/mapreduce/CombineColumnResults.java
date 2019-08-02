package org.enquery.encryptedquery.hadoop.mapreduce;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.hadoop.core.CombineColumnResultsMapper;
import org.enquery.encryptedquery.hadoop.core.CombineColumnResultsReducer;
import org.enquery.encryptedquery.hadoop.core.CombineColumnResultsReducer_v1;
import org.enquery.encryptedquery.hadoop.core.HadoopConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CombineColumnResults {

	private static final Logger log = LoggerFactory.getLogger(CombineColumnResults.class);

	public static boolean run(Map<String, String> jobConfig, QueryInfo queryInfo, FileSystem hdfs, Path inputPath,
			Path outputPath, String outputFileName, java.nio.file.Path configFilePath, Boolean v1ProcessingMethod)
		    throws FileNotFoundException, IOException, ClassNotFoundException, InterruptedException {
		
		boolean success;

		Job job = Job.getInstance(hdfs.getConf(), "EQ-MR-Consolidation");
		job.setSpeculativeExecution(false);

		String jobName = "EQ-" + queryInfo.getQueryName() + "_CombineResults";
		job.setJobName(jobName);
		// Set the memory and heap options
		if (jobConfig.containsKey(HadoopConfigurationProperties.MR_MAP_MEMORY_MB)) {
			job.getConfiguration().set(HadoopConfigurationProperties.MR_MAP_MEMORY_MB,
					jobConfig.get(HadoopConfigurationProperties.MR_MAP_MEMORY_MB));
		}
		if (jobConfig.containsKey(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB)) {
			job.getConfiguration().set(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB,
					jobConfig.get(HadoopConfigurationProperties.MR_REDUCE_MEMORY_MB));
		}
		if (jobConfig.containsKey(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS)) {
			job.getConfiguration().set(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS,
					jobConfig.get(HadoopConfigurationProperties.MR_MAP_JAVA_OPTS));
		}
		if (jobConfig.containsKey(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS)) {
			job.getConfiguration().set(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS,
					jobConfig.get(HadoopConfigurationProperties.MR_REDUCE_JAVA_OPTS));
		}
		
        job.getConfiguration().set("hadoopWorkingFolder", jobConfig.get(HadoopConfigurationProperties.HDFSWORKINGFOLDER));
        job.getConfiguration().set("configFileName", configFilePath.getFileName().toString());
        job.getConfiguration().set("outputFileName", outputFileName);
		
		// Set necessary files for Mapper setup
		job.getConfiguration().set("mapreduce.map.speculative", "false");
		job.getConfiguration().set("mapreduce.reduce.speculative", "false");
		
		job.setNumReduceTasks(1);
		job.setJarByClass(CombineColumnResultsMapper.class);
		job.setMapperClass(CombineColumnResultsMapper.class);
		job.setInputFormatClass(SequenceFileInputFormat.class);

		FileStatus[] status = hdfs.listStatus(inputPath);
		for (FileStatus fstat : status) {
			if (fstat.getPath().getName().startsWith(HadoopConfigurationProperties.EQ_COLS)) {
				log.info("fstat.getPath() = " + fstat.getPath().toString());
				FileInputFormat.addInputPath(job, fstat.getPath());
			}
		}
		
		job.setMapOutputKeyClass(LongWritable.class);
		job.setMapOutputValueClass(BytesWritable.class);

		// Set the reducer and output params
		if (v1ProcessingMethod) {
			job.setReducerClass(CombineColumnResultsReducer_v1.class);
		} else {
			job.setReducerClass(CombineColumnResultsReducer.class);
		}
		job.setOutputKeyClass(LongWritable.class);
		job.setOutputValueClass(Text.class);

		// Delete the output directory if it exists
		if (hdfs.exists(outputPath)) {
			hdfs.delete(outputPath, true);
		}
		FileOutputFormat.setOutputPath(job, outputPath);

		MultipleOutputs.addNamedOutput(job, HadoopConfigurationProperties.EQ_FINAL, TextOutputFormat.class,
				LongWritable.class, Text.class);
        log.info("Starting Combine Column Results MapReduce processing.");

		// Submit job, wait for completion
		success = job.waitForCompletion(true);

		return success;
		
	}
}
