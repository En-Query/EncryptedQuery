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

import java.nio.file.Path;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.enquery.encryptedquery.hadoop.core.ProcessDataMapper;
import org.enquery.encryptedquery.hadoop.core.ProcessDataReducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessData {
	static final Logger log = LoggerFactory.getLogger(ProcessData.class);

	public static boolean run(Configuration conf, String inputFile, Path outputPath) throws Exception {

		Job job = Job.getInstance(conf, "EQ-MR-ProcessData");
		job.setSpeculativeExecution(false);

		job.setInputFormatClass(TextInputFormat.class);
		FileInputFormat.setInputPaths(job, inputFile);

		job.setJarByClass(ProcessDataMapper.class);
		job.setMapperClass(ProcessDataMapper.class);

		// mapper outputs (hash, dataElement)
		job.setMapOutputKeyClass(IntWritable.class);
		job.setMapOutputValueClass(BytesWritable.class);

		job.setReducerClass(ProcessDataReducer.class);
		job.setOutputKeyClass(LongWritable.class);
		job.setOutputValueClass(BytesWritable.class);

		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		FileOutputFormat.setOutputPath(job, new org.apache.hadoop.fs.Path(outputPath.toString()));

		log.info("Starting ProcessData MapReduce processing.");

		// Submit job, wait for completion
		return job.waitForCompletion(true);
	}

}
