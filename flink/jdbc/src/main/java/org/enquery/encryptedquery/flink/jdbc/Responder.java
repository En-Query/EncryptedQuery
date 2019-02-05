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
package org.enquery.encryptedquery.flink.jdbc;

import java.sql.ResultSet;

import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.jdbc.JDBCInputFormat;
import org.enquery.encryptedquery.flink.BaseQueryExecutor;

public class Responder extends BaseQueryExecutor {

	private String driverClassName;
	private String connectionUrl;
	private String sqlQuery;

	public String getSqlQuery() {
		return sqlQuery;
	}

	public void setSqlQuery(String sqlQuery) {
		this.sqlQuery = sqlQuery;
	}

	public String getDriverClassName() {
		return driverClassName;
	}

	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}

	public String getConnectionUrl() {
		return connectionUrl;
	}

	public void setConnectionUrl(String connectionUrl) {
		this.connectionUrl = connectionUrl;
	}

	public void run() throws Exception {
		initializeCommon();
		initializeBatch();

		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		JDBCInputFormat inputBuilder = JDBCInputFormat.buildJDBCInputFormat()
				.setDrivername(driverClassName)
				.setDBUrl(connectionUrl)
				.setQuery(sqlQuery)
				.setRowTypeInfo(getRowTypeInfo())
				.setResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE)
				.finish();

		run(env, env.createInput(inputBuilder));
	}
}
