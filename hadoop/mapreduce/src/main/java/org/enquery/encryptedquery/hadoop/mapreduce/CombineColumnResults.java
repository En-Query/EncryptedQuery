package org.enquery.encryptedquery.hadoop.mapreduce;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.enquery.encryptedquery.hadoop.core.CombineColumnResultsMapper;
import org.enquery.encryptedquery.hadoop.core.CombineColumnResultsReducer;
import org.enquery.encryptedquery.hadoop.core.CombineColumnResultsReducer_v1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CombineColumnResults {

	private static final Logger log = LoggerFactory.getLogger(CombineColumnResults.class);

	public static boolean run(
			Configuration conf,
			Path inputPath,
			Path outputPath,
			String outputFileName,
			boolean v1ProcessingMethod)
			throws FileNotFoundException, IOException, ClassNotFoundException, InterruptedException {

		boolean success;

		Job job = Job.getInstance(conf, "EQ-MR-Consolidation");
		job.getConfiguration().set("outputFileName", outputFileName);

		job.setNumReduceTasks(1);
		job.setJarByClass(CombineColumnResultsMapper.class);
		job.setMapperClass(CombineColumnResultsMapper.class);
		job.setInputFormatClass(SequenceFileInputFormat.class);
		FileInputFormat.addInputPath(job, new org.apache.hadoop.fs.Path(inputPath.toString()));

		job.setMapOutputKeyClass(LongWritable.class);
		job.setMapOutputValueClass(BytesWritable.class);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);

		// Set the reducer and output params
		if (v1ProcessingMethod) {
			job.setReducerClass(CombineColumnResultsReducer_v1.class);
		} else {
			job.setReducerClass(CombineColumnResultsReducer.class);
		}
		job.setOutputKeyClass(LongWritable.class);
		job.setOutputValueClass(Text.class);

		FileOutputFormat.setOutputPath(job,
				new org.apache.hadoop.fs.Path(outputPath.toString()));

		log.info("Starting Combine Column Results MapReduce processing.");

		// Submit job, wait for completion
		success = job.waitForCompletion(true);

		return success;
	}
}
