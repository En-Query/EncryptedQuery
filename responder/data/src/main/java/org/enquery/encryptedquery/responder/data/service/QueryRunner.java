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
package org.enquery.encryptedquery.responder.data.service;

import java.io.OutputStream;
import java.util.Map;

import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.responder.data.entity.DataSourceType;

/**
 * Base interface encapsulating adapters for external applications implementing the PIR algorithm.
 * 
 * The job of a QueryRunner is to invoke an external application that executes PIR on a particular
 * environment/platform/datasource.
 * 
 * A QueryRunner should not implement the PIR directly, but it is an interface to execute an
 * externally implemented one.
 * 
 * A QueryRunner can load the data it needs from the Responder local database to pass in to the
 * external application.
 *
 */
public interface QueryRunner {
	/**
	 * Executes the given query and store response to the specified responseFileName. The standard
	 * output is captured in the provided stdOutFileName file.
	 * 
	 * @param query
	 * @param parameters
	 * @param responseFileName
	 * @param stdOutput
	 */
	void run(Query query, Map<String, String> parameters, String responseFileName, OutputStream stdOutput);

	/**
	 * Unique name of this Query Runner.
	 * 
	 * @return
	 */
	String name();

	/**
	 * Description of this Query Runner
	 * 
	 * @return
	 */
	String description();

	/**
	 * The name of the Data Schema this Query Runner operates on.
	 * 
	 * @return
	 */
	String dataSchemaName();

	/**
	 * Type: Batch or Streaming
	 * 
	 * @return
	 */
	DataSourceType getType();
}
