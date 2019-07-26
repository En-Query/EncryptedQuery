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

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.io.DefaultInputSplitAssigner;
import org.apache.flink.api.common.io.RichInputFormat;
import org.apache.flink.api.common.io.statistics.BaseStatistics;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.io.GenericInputSplit;
import org.apache.flink.core.io.InputSplit;
import org.apache.flink.core.io.InputSplitAssigner;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.data.DataSchemaElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class JDBCSource extends RichInputFormat<Map<String, Object>, InputSplit> {

	private static final long serialVersionUID = -3901276465418711328L;
	private static final Logger log = LoggerFactory.getLogger(JDBCSource.class);

	private final String query;
	private final String drivername;
	private final String dbURL;
	private final Integer fetchSize;
	private final DataSchema dataSchema;

	private transient Connection dbConn;
	private transient PreparedStatement statement;
	private transient ResultSet resultSet;
	private boolean hasNext;

	/**
	 * 
	 */
	public JDBCSource(String query,
			String drivername,
			String dbURL,
			Integer fetchSize,
			DataSchema dataSchema) {

		Validate.notNull(query);
		Validate.notNull(drivername);
		Validate.notNull(dbURL);
		Validate.notNull(dataSchema);

		this.query = query;
		this.drivername = drivername;
		this.dbURL = dbURL;
		this.fetchSize = fetchSize;
		this.dataSchema = dataSchema;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.api.common.io.InputFormat#open(org.apache.flink.core.io.InputSplit)
	 */
	@Override
	public void open(InputSplit split) throws IOException {
		try {
			Class.forName(drivername);
			dbConn = DriverManager.getConnection(dbURL);
			statement = dbConn.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			if (fetchSize != null) {
				statement.setFetchSize(fetchSize);
			}
			if (log.isDebugEnabled()) {
				log.debug("Query: {}", query);
				log.debug("Query Statement: {}", statement.toString());
			}
			resultSet = statement.executeQuery();
			hasNext = resultSet.next();
		} catch (SQLException se) {
			throw new IllegalArgumentException("open() failed." + se.getMessage(), se);
		} catch (ClassNotFoundException cnfe) {
			throw new IllegalArgumentException("JDBC driver not found. - " + cnfe.getMessage(), cnfe);
		}
	}

	@Override
	public void close() {
		if (resultSet != null) {
			try {
				resultSet.close();
			} catch (SQLException se) {
				log.info("Inputformat ResultSet couldn't be closed - " + se.getMessage());
			}
			resultSet = null;
		}

		if (statement != null) {
			try {
				statement.close();
			} catch (SQLException se) {
				log.info("Inputformat Statement couldn't be closed - " + se.getMessage());
			}
			statement = null;
		}

		if (dbConn != null) {
			try {
				dbConn.close();
			} catch (SQLException se) {
				log.info("Inputformat couldn't be closed - " + se.getMessage());
			}
			dbConn = null;
		}
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.api.common.io.InputFormat#nextRecord(java.lang.Object)
	 */
	@Override
	public Map<String, Object> nextRecord(Map<String, Object> reuse) throws IOException {
		if (!hasNext) return null;
		try {
			Map<String, Object> record = loadRecord();
			if (log.isDebugEnabled()) {
				log.debug("Loaded record: {}", record);
			}

			// update hasNext after we've read the record
			hasNext = resultSet.next();
			return record;
		} catch (SQLException se) {
			throw new IOException("Couldn't read data - " + se.getMessage(), se);
		} catch (NullPointerException npe) {
			throw new IOException("Couldn't access resultSet", npe);
		}
	}

	/**
	 * @return
	 * @throws SQLException
	 */
	private Map<String, Object> loadRecord() throws SQLException {
		Map<String, Object> result = new HashMap<>(dataSchema.elementCount());
		for (DataSchemaElement dse : dataSchema.elements()) {
			result.put(dse.getName(), resultSet.getObject(dse.getPosition() + 1));
		}
		return result;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.api.common.io.InputFormat#configure(org.apache.flink.configuration.
	 * Configuration)
	 */
	@Override
	public void configure(Configuration parameters) {}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.api.common.io.InputFormat#getStatistics(org.apache.flink.api.common.io.
	 * statistics.BaseStatistics)
	 */
	@Override
	public BaseStatistics getStatistics(BaseStatistics cachedStatistics) throws IOException {
		return cachedStatistics;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.api.common.io.InputFormat#createInputSplits(int)
	 */
	@Override
	public InputSplit[] createInputSplits(int minNumSplits) throws IOException {
		return new GenericInputSplit[] {new GenericInputSplit(0, 1)};
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.flink.api.common.io.InputFormat#getInputSplitAssigner(org.apache.flink.core.io.
	 * InputSplit[])
	 */
	@Override
	public InputSplitAssigner getInputSplitAssigner(InputSplit[] inputSplits) {
		return new DefaultInputSplitAssigner(inputSplits);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.api.common.io.InputFormat#reachedEnd()
	 */
	@Override
	public boolean reachedEnd() throws IOException {
		return !hasNext;
	}

}
