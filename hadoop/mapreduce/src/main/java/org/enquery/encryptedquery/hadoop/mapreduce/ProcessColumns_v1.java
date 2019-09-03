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
import java.nio.file.Path;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.enquery.encryptedquery.hadoop.core.IntBytesPairWritable;
import org.enquery.encryptedquery.hadoop.core.ProcessColumnsMapper_v1;
import org.enquery.encryptedquery.hadoop.core.ProcessColumnsReducer_v1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessColumns_v1 {

	private static final Logger log = LoggerFactory.getLogger(ProcessColumns_v1.class);

	public static boolean run(Configuration conf,
			Path inputPath,
			Path outputPath) throws FileNotFoundException, IOException, ClassNotFoundException, InterruptedException {

		Job job = Job.getInstance(conf, "EQ-MR-ProcessColumns");
		job.setSpeculativeExecution(false);

		job.setJarByClass(ProcessColumnsMapper_v1.class);
		job.setMapperClass(ProcessColumnsMapper_v1.class);
		job.setInputFormatClass(SequenceFileInputFormat.class);
		org.apache.hadoop.fs.Path path = new org.apache.hadoop.fs.Path(inputPath.toString());
		FileInputFormat.addInputPath(job, path);

		job.setMapOutputKeyClass(IntWritable.class);
		job.setMapOutputValueClass(IntBytesPairWritable.class);

		// Set the reducer and output params
		job.setReducerClass(ProcessColumnsReducer_v1.class);
		job.setOutputKeyClass(LongWritable.class);
		job.setOutputValueClass(BytesWritable.class);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);

		FileOutputFormat.setOutputPath(job, new org.apache.hadoop.fs.Path(outputPath.toString()));

		log.info("Starting Process Columns MapReduce processing.");

		// Submit job, wait for completion
		return job.waitForCompletion(true);
	}
}
