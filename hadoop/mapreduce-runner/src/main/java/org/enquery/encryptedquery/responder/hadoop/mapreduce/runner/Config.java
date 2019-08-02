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
package org.enquery.encryptedquery.responder.hadoop.mapreduce.runner;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
		name = "EncryptedQuery Hadoop-MapReduce Runner",
		description = "Allows for the instantiation of Hadoop-MapReduce Runners through OSGi configuration.")
public @interface Config {

	@AttributeDefinition(name = "Name",
			required = true,
			description = "Short name of this query runner. This name is used "
					+ "to identify the DataSource to the Querier component"
					+ "so queries can be submitted using this name for execution.")
	String name();

	@AttributeDefinition(name = "Description",
			required = true,
			description = "Description of this query runner for display to the end user. ")
	String description();

	@AttributeDefinition(name = "data.schema.name",
			required = false,
			description = "Name of the DataSchema describing the fields and partitioners.")
	String data_schema_name();

	@AttributeDefinition(name = "data.source.file",
			required = true,
			description = "HDFS Absolute File name or folder for the source data.  If a folder then all files within that folder will be searched.")
	String data_source_file();

	@AttributeDefinition(name = "data.source.record.type",
			required = true,
			description = "Record type (i.e. json, csv, etc).")
	String data_source_record_type();
	
	@AttributeDefinition(name = ".computer.threshold",
			required = false,
			description = "Amount of data to process before consolidation.   Larger numbers require more memory per task. Defaults to 30000")
	String _compute_threshold() default "30000";

	@AttributeDefinition(name = "limit.hits.per.selector",
			required = false,
			description = "Limit number of hits per Selector Hash.  If true then maxHitsPerSelector is the max number of hits")
	String _limit_hits_per_selector() default "true";
	
	@AttributeDefinition(name = "Hadoop Server URI",
			required = true,
			description = "Hadoop Master URI (i.e. hdfs://namenodeserver:8020)")
	String _hadoop_server_uri() default "hdfs://localhost:8020";

	@AttributeDefinition(name = "Hadoop Username",
			required = true,
			description = "Username used to access Hadoop.")
	String _hadoop_username() default "hadoop";

	@AttributeDefinition(name = "Hadoop Run Directory",
			required = true,
			description = "HDFS Path to a folder to use as the parent folder to store temporary files during the execution of the query."
					+ "Every execution will create temporary files in this folder.")
	String _hdfs_run_directory();
	
	@AttributeDefinition(name = "Hadoop Installation Directory",
			required = true,
			description = "Directory where Hadoop runtime is installed.")
	String _hadoop_install_dir() default "/opt/hadoop";

	@AttributeDefinition(name = "Hadoop Reduce Tasks",
			required = false,
			description = "Number of Tasks to use for Map Reduce.")
	String _hadoop_reduce_tasks() default "10";
	
	@AttributeDefinition(name = "Hadoop MR Map Memory MB",
			required = false,
			description = "Amount of Memory for each MapReduce Map task.")
	String _hadoop_mapreduce_map_memory_mb() ;

	@AttributeDefinition(name = "Hadoop MR Reduce Memory MB",
			required = false,
			description = "Amount of Memory for each MapReduce Reduce task.")
	String _hadoop_mapreduce_reduce_memory_mb();

	@AttributeDefinition(name = "Hadoop MR Map Java Opts",
			required = false,
			description = "Java Options for MapReduce Map task.")
	String _hadoop_mapreduce_map_java_opts() ;

	@AttributeDefinition(name = "Hadoop MR Reduce Java Opts",
			required = false,
			description = "Java Options for MapReduce Reduce task.")
	String _hadoop_mapreduce_reduce_java_opts();

	@AttributeDefinition(name = "Hadoop Chunking Byte Size",
				required = false,
				description = "Chunking size for Hadoop processing.")
	String _hadoop_chunking_byte_size() default "100";	
			
	@AttributeDefinition(name = "Hadoop MR Processing Method",
			required = false,
			description = "Method for processing data in MR, v1 for processing columns method, v2 for processing by record method.")
    String _hadoop_processing_method() default "v2";	
	
	@AttributeDefinition(name = "Additional Hadoop Argumments",
			required = false,
			description = "Additional arguments to be passed to Hadoop 'run' command when executing the query.")
	String _additional_hadoop_arguments();

	@AttributeDefinition(name = ".jar.file.path",
			required = true,
			description = "Path to the hadoop-map-reduce jar file.  This is the jar file implementing the query execution.")
	String _application_jar_path();

	@AttributeDefinition(name = "Run Directory",
			required = true,
			description = "Path to a directory to use as the parent directory to store temporary files during the execution of the query."
					+ "Every execution will create temporary directories under this one.")
	String _run_directory();

}
