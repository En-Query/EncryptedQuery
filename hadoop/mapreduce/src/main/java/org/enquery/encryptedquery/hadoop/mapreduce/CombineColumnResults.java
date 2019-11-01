package org.enquery.encryptedquery.hadoop.mapreduce;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.enquery.encryptedquery.hadoop.core.ColumnWritable;
import org.enquery.encryptedquery.hadoop.core.CombineColumnResultsReducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CombineColumnResults {

	private static final Logger log = LoggerFactory.getLogger(CombineColumnResults.class);

	public static boolean run(
			Configuration conf,
			Path inputPath,
			Path outputPath)
			throws FileNotFoundException, IOException, ClassNotFoundException, InterruptedException {


		Job job = Job.getInstance(conf, "EQ-MR-Consolidation");

		job.setJarByClass(CombineColumnResultsReducer.class);
		job.setInputFormatClass(SequenceFileInputFormat.class);
		FileInputFormat.addInputPath(job, new org.apache.hadoop.fs.Path(inputPath.toString()));

		job.setMapOutputKeyClass(IntWritable.class);
		job.setMapOutputValueClass(ColumnWritable.class);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);

		// Set the reducer and output params
		job.setReducerClass(CombineColumnResultsReducer.class);
		job.setOutputKeyClass(IntWritable.class);
		job.setOutputValueClass(BytesWritable.class);

		FileOutputFormat.setOutputPath(job, new org.apache.hadoop.fs.Path(outputPath.toString()));

		log.info("Starting Combine Column Results MapReduce processing.");

		// Submit job, wait for completion
		return job.waitForCompletion(true);
	}
}
