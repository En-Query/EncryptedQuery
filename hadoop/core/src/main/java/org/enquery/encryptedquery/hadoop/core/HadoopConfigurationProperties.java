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
package org.enquery.encryptedquery.hadoop.core;

/**
 * Properties constants for Hadoop
 */
// TODO: move from here
public interface HadoopConfigurationProperties {
	String HDFSSERVER = "hadoop.server.uri";
	String HDFSUSERNAME = "hadoop.username";
	String HDFSWORKINGFOLDER = "hadoop.working.folder";
	String DATA_SOURCE_RECORD_TYPE = "data.source.record.type";
	String QUERY_FILE="query.file";
	String CONFIG_PROPERTIES_FILE = "configuration.properties.file";
	String RESPONSE_FILE = "response.file";
	String HADOOP_REDUCE_TASKS = "hadoop.reduce.tasks";
	String MR_MAP_MEMORY_MB = "mapreduce.map.memory.mb";
	String MR_REDUCE_MEMORY_MB = "mapreduce.reduce.memory.mb";
	String MR_MAP_JAVA_OPTS = "mapreduce.map.java.opts";
	String MR_REDUCE_JAVA_OPTS = "mapreduce.reduce.java.opts";
	String MR_TASK_TIMEOUT = "mapreduce.task.timeout";
	String CHUNKING_BYTE_SIZE = "chunkingByteSize";
	String PROCESSING_METHOD = "processing.method";
	
	  // For general output
	String COUNTS = "counts";
	String DETAILS = "details";

	  // For HDFS Storage
	String EQ = "eq";
	String EXP = "exp";
	String EQ_COLS = "eqCols";
	String EQ_FINAL = "eqFinal";
	
	public enum MRStats
	{
	  NUM_RECORDS_INIT_MAPPER, NUM_RECORDS_PROCESSED_INIT_MAPPER, 
	  NUM_RECORDS_INIT_REDUCER, NUM_HASHS_INIT_REDUCER,
	  NUM_HASHS_OVER_MAX_HITS, NUM_HASHES_REDUCER, 
	  NUM_COLUMNS, TOTAL_COLUMN_COUNT
	}
	
}
