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

	@AttributeDefinition(name = ".hadoop.username",
			required = false,
			description = "Username used to access Hadoop. If not specified, the current user account running Responder is used.")
	String _hadoop_username();

	@AttributeDefinition(name = "Hadoop Run Directory",
			required = true,
			description = "HDFS directory to store temp files. Required.")
	String _hdfs_run_directory();

	@AttributeDefinition(name = ".hadoop.install.dir",
			required = true,
			description = "Directory where Hadoop runtime is installed.")
	String _hadoop_install_dir() default "/opt/hadoop";

	@AttributeDefinition(name = ".additional.hadoop.arguments",
			required = false,
			description = "Additional arguments to be passed to Hadoop command when executing the query.")
	String _additional_hadoop_arguments();

	@AttributeDefinition(name = ".application.jar.path",
			required = true,
			description = "Path to the hadoop-map-reduce jar file.  This is the jar file implementing the query execution.")
	String _application_jar_path();

	@AttributeDefinition(name = ".run.directory",
			required = true,
			description = "Path to a directory to use as the parent directory to store temporary files during the execution of the query."
					+ "Every execution will create temporary directories under this one.")
	String _run_directory();

	@AttributeDefinition(name = ".hadoop.config.file",
			required = false,
			description = "Hadoop Config File. Optional path to a Hadoop configuration file (in the local machine) containing additional Hadoop settings.")
	String _hadoop_config_file();

	@AttributeDefinition(name = ".chunk.size",
			required = false,
			description = "Overrides the chunk size specified by Querier when processing a query.")
	String _chunk_size();

	@AttributeDefinition(name = ".column.buffer.memory.mb",
			required = false,
			description = "Size of column buffer in MB.  Default is 16 MB.")
	int _column_buffer_memory_mb() default 16;
}
