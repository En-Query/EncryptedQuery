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
package org.enquery.encryptedquery.responder.flink.jdbc.runner;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
		name = "EncryptedQuery Flink-JDBC Runner",
		description = "Allows for the instantiation of Flink-JDBC Runners through OSGi configuration.")
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

	@AttributeDefinition(name = "type",
			required = false,
			description = "Type: Batch or Streaming.")
	String type();

	@AttributeDefinition(name = "data.schema.name",
			required = false,
			description = "Name of the DataSchema describing the fields and partitioners.")
	String data_schema_name();

	@AttributeDefinition(name = ".column.buffer.memory.mb",
			required = false,
			description = "Amount of memory in MB to be allocated for Column processing.")
	String _column_buffer_memory_mb();

	@AttributeDefinition(name = ".flink.parallelism",
			required = false,
			description = "Number of Flink concurrent tasks assigned to execute this query.  Defaults to Flinks configured default.")
	String _flink_parallelism();

	@AttributeDefinition(name = "jdbc.driver",
			required = true,
			description = "Class name for the JDBC Driver to access the database.")
	String _jdbc_driver();

	@AttributeDefinition(name = "jdbc.url",
			required = true,
			description = "URL to connect to the database.")
	String _jdbc_url();

	@AttributeDefinition(name = "jdbc.query",
			required = true,
			description = "SQL query that selects the data to be queried.")
	String _jdbc_query();

	@AttributeDefinition(name = "Flink Installation Directory",
			required = true,
			description = "Directory where Flink runtime is installed.")
	String _flink_install_dir() default "/opt/flink";

	@AttributeDefinition(name = "Additional Flink Argumments",
			required = false,
			description = "Additional arguments to be passed to Flink 'run' command when executing the query.")
	String _additional_flink_arguments();

	@AttributeDefinition(name = "Application jar file",
			required = true,
			description = "Fully Qualified application jar file name.")
	String _application_jar_path();
	
	@AttributeDefinition(name = "Run Directory",
			required = true,
			description = "Path to a directory to use as the parent directory to store temporary files during the execution of the query."
					+ "Every execution will create temporary directories under this one.")
	String _run_directory();

}
