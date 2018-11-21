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
package org.enquery.encryptedquery.responder.standalone.runner;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
		name = "EncryptedQuery Standalone Runner",
		description = "Allows for the instantiation of Standalone Runners through OSGi configuration.")
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
			description = "Absolute File name for the source data to be queried against.")
	String data_source_file();

	@AttributeDefinition(name = "data.source.record.type",
			required = true,
			description = "Record type (i.e. json, csv, etc). Only JSON is supported at the moment.")
	String data_source_record_type();

	@AttributeDefinition(name = ".column.encryption.class.name",
			required = true,
			description = "Class name of the encryption method to be used.")
	String _column_encryption_class_name();

	@AttributeDefinition(name = ".mod.pow.class.name",
			required = true,
			description = "Class name of the Modulus Power method to be used.")
	String _mod_pow_class_name();

	@AttributeDefinition(name = "Run Directory",
			required = true,
			description = "Path to a directory to use as the parent directory to store temporary files during the execution of the query."
					+ "Every execution will create temporary directories under this one.")
	String _run_directory();

	@AttributeDefinition(name = "Java executable path",
			required = true,
			description = "Path to the java executable program.")
	String _java_path() default "/usr/bin/java";

	@AttributeDefinition(name = "Java runtime options.",
			required = true,
			description = "Options to Java runtime, such as Heap size, etc.")
	String _java_options();

	@AttributeDefinition(name = "Standalone application jar path",
			required = true,
			description = "Path to the standalone application jar.")
	String _application_jar_path();

	@AttributeDefinition(name = "Number of thread to use.",
			required = false,
			description = "Number of threads to use for processing of the Query.")
	int _number_of_threads() default 1;

	@AttributeDefinition(name = "Internal Max Queue Size.",
			required = false,
			description = "Internal processing maximum queue size.")
	int _max_queue_size() default 1000;

	@AttributeDefinition(name = "Compute threshold.",
			required = false,
			description = "Compute threshold.")
	long _compute_threshold() default 30000L;

	@AttributeDefinition(name = ".jni.library.path",
			required = false,
			description = "Comma separated list of native libraries to load. Optional.")
	String _jni_library_path();
}
