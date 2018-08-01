/*
 * Copyright 2017 EnQuery.
 * This product includes software licensed to EnQuery under 
 * one or more license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file has been modified from its original source.
 */
package org.enquery.encryptedquery.responder.wideskies.jdbc;


import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.management.timer.Timer;

import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.wideskies.common.ColumnBasedResponderProcessor;
import org.enquery.encryptedquery.responder.wideskies.common.ConsolidateResponse;
import org.enquery.encryptedquery.responder.wideskies.common.ProcessingUtils;
import org.enquery.encryptedquery.responder.wideskies.common.QueueRecord;
import org.enquery.encryptedquery.responder.wideskies.jdbc.dao.DAOFactory;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.serialization.LocalFileSystemStore;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.enquery.encryptedquery.utils.PIRException;


/**
 * Class to perform kafka responder functionalities
 * <p>
 * Does not bound the number of hits that can be returned per selector
 * <p>
 * Does not use the DataFilter class -- assumes all filtering happens before calling addDataElement()
 * <p>
 * NOTE: Only uses in expLookupTables that are contained in the Query object, not in hdfs
 */
public class JDBCQueryResponder {

	private static final Logger logger = LoggerFactory.getLogger(JDBCQueryResponder.class);
    private DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	private Query query = null;
	private QueryInfo queryInfo = null;

	private long recordsLoaded = 0;
	private long recordsProcessed = 0;

	private List<ConcurrentLinkedQueue<QueueRecord>> newRecordQueues = new ArrayList<ConcurrentLinkedQueue<QueueRecord>>();
	private ConcurrentLinkedQueue<Response> responseQueue = new ConcurrentLinkedQueue<Response>();

	private List<ColumnBasedResponderProcessor> responderProcessors;
	private List<Thread> responderProcessingThreads;

	private static final Integer numberOfProcessorThreads = Integer.valueOf(SystemConfiguration.getProperty("responder.processing.threads", "1"));
	private static final String databaseType = SystemConfiguration.getProperty("jdbc.database.type", null);

	private DAOFactory dao;
	private Properties jdbcProperties;

	private Response response = null;

	public JDBCQueryResponder(Query queryInput)
	{
		this.query = queryInput;
		this.queryInfo = queryInput.getQueryInfo();

		jdbcProperties = getJDBCConfig();

		if (databaseType == null) {
			logger.error("Database type property missing.  Set jdbc.database.type property !");
			return;
		}

	}

	private static Properties getJDBCConfig() {

		logger.info("Configuring JDBC");
		Properties props = new Properties();
		props.put("useSSL", SystemConfiguration.getBooleanProperty("jdbc.useSSL", false));
		props.put("keystore", SystemConfiguration.getProperty("jdbc.ssl.keystore", null));
		props.put("keystorePassword", SystemConfiguration.getProperty("jdbc.ssl.keystorePassword", null));
		props.put("truststore", SystemConfiguration.getProperty("jdbc.ssl.truststore", null));
		props.put("truststorePassword", SystemConfiguration.getProperty("jdbc.ssl.truststorePassword", null));
		props.put("databaseUrl", SystemConfiguration.getProperty("jdbc.database.url", null));
		props.put("databaseName", SystemConfiguration.getProperty("jdbc.database.name", null));
		props.put("databaseUser", SystemConfiguration.getProperty("jdbc.database.username", null));
		props.put("databasePassword", SystemConfiguration.getProperty("jdbc.database.password", null));

		return props;
	}
	/**
	 * Validate the SSL files necessary for the SSL connection to the database.   
	 * @param props
	 * @return Boolean if the SSL files are good
	 */
	private static boolean validateSSL(Properties props) {
		//TODO: Add code to validate SSL files/passwords
		return true;
	}

	public Response getResponse()
	{
		return response;
	}

	/**
	 * Returns the number of records loaded into the processing queue by the JDBC responder
	 * @return long - Records Loaded
	 */
	public long getRecordsLoaded()
	{
		if (dao != null) {
			long tempLoaded = dao.getRecordsLoaded();
			if (tempLoaded > recordsLoaded ) {
				recordsLoaded = tempLoaded;
			}
		} 
		return recordsLoaded;
	}

	/**
	 * Returns the Percent of records processed based on how many have been loaded into the queues
	 * @return int Percent Complete
	 */
	public int getPercentComplete()
	{
		if (responderProcessors != null) {
			return ProcessingUtils.getPercentComplete(getRecordsLoaded(), responderProcessors);
		}
		return 0;
	}

	/**
	 * Method to compute the response
	 * <p>
	 * Assumes that the input data is from a kafka topic and is fully qualified
	 * @throws PIRException 
	 */
	public void computeJDBCResponse() throws IOException, PIRException
	{
		logger.info("Connecting to {} database.", databaseType);
		logger.info("Connection URL ( {} ) | Database ( {} ) |User ( {} )", jdbcProperties.getProperty("databaseUrl"),
				jdbcProperties.getProperty("databaseName"), jdbcProperties.getProperty("databaseUser"));

		// If using SSL make sure the key files are available
		if ( (boolean)jdbcProperties.get("useSSL") && validateSSL(jdbcProperties) ) {
			if (validateSSL(jdbcProperties)) {
				logger.info("Using SSL connection");
			} 
			else {
				logger.error("Error, invalid SSL configuration");
				return;      		
			} 
		}
		try {

			if (databaseType != null) {
				if ("MySQL".equalsIgnoreCase(databaseType)) {
					dao =  DAOFactory.getDAOFactory(DAOFactory.MYSQL, jdbcProperties); 
				} else if ("MariaDB".equalsIgnoreCase(databaseType)) {
					dao =  DAOFactory.getDAOFactory(DAOFactory.MARIADB, jdbcProperties); 
				} else if ("Oracle".equalsIgnoreCase(databaseType)) {
					dao =  DAOFactory.getDAOFactory(DAOFactory.ORACLE, jdbcProperties); 
				} else if ("Mongo".equalsIgnoreCase(databaseType)) {
					dao =  DAOFactory.getDAOFactory(DAOFactory.MONGO, jdbcProperties);
				} else {
					logger.error("Invalid database Type {}", databaseType);
				}
			}
			else {
				logger.error("Database type is required, Stopping Responder");
				return;
			}
		} catch (Exception e) {
			logger.error("Error initializing DAOFactory {}", e.getMessage());
			throw new PIRException("Error initializing DAO Factory", e);
		}

		if (!dao.isConnected()) {
			logger.error("No connection to database established, Stopping Responder");
			return;
		}

		String queryStatement = queryInfo.getQuerySchema().getDatabaseQuery();
		if (queryStatement == null) {
			logger.error("Query Statement missing from query schema, Stopping Responder");
			return;
		}

		// Based on the number of processor threads, calculate the number of hashes for each queue
		int hashGroupSize = ((1 << queryInfo.getHashBitSize()) + numberOfProcessorThreads -1 ) / numberOfProcessorThreads;
		logger.info("Based on {} Processor Thread(s) the Hash group size is {}",
				numberOfProcessorThreads, numFormat.format(hashGroupSize));
		for (int i = 0 ; i < numberOfProcessorThreads; i++) {
			newRecordQueues.add(new ConcurrentLinkedQueue<QueueRecord>());
		}

		// Initialize & Start Processing Threads
		responderProcessors = new ArrayList<>();
		responderProcessingThreads = new ArrayList<>();
		for (int i = 0; i < numberOfProcessorThreads; i++) {
			//			ResponderProcessingThread qpThread =
			//					new ResponderProcessingThread(newRecordQueues.get(i), responseQueue, query); 
			ColumnBasedResponderProcessor qpThread =
					new ColumnBasedResponderProcessor(newRecordQueues.get(i), responseQueue, query); 
			responderProcessors.add(qpThread);
			Thread pt = new Thread(qpThread);
			pt.start();
			responderProcessingThreads.add(pt);
		}

		if (queryStatement != null) {
			logger.info("Executing Query Statement: {}", queryStatement);
			recordsLoaded = dao.loadResponderDataDAO(query.getQueryInfo(), queryStatement, hashGroupSize, newRecordQueues, responderProcessors);
		}

		logger.info("Records Loaded {}", getRecordsLoaded());

		if (dao != null) {
			dao.closeConnection();
		}

		try
		{
			// Give the processors a few seconds to process any remaining records. 
			try {
				Thread.sleep(2000);
			} catch (Exception e) {
				logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
			}
			logger.info("Records Processed {} / Percent Complete {}", numFormat.format(recordsProcessed), getPercentComplete());

			for (ColumnBasedResponderProcessor qpThread : responderProcessors) {
				qpThread.stopProcessing();
			}

			// Wait for all Processors to Update response Queue 
			try {
				Thread.sleep(2000);
			} catch (Exception e) {
				logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
			}

			// We have issued a stop command to the responder processors
			// Now poll the processors until they are finished
			long notificationTimer = System.currentTimeMillis() + Timer.ONE_MINUTE;
			int running = 0;
			do {
				running = 0;
				for (Thread thread : responderProcessingThreads) {
					if (thread.isAlive()) {
						running++;
					}
				}
				// We do not want to bombard the log with messages so only post once a minute
				// If it takes a while for the processors to stop that means they could not keep up with the
				// rate of records being added to the queues.  If the system resources are not maxed out then 
				// increase the number of processing threads to help.
				if ( System.currentTimeMillis() > notificationTimer ) {

					logger.info( "There are {} responder processes running, {} records processed / {} % complete", 
							running, numFormat.format(recordsProcessed), numFormat.format(getPercentComplete()) );

					notificationTimer = System.currentTimeMillis() + Timer.ONE_MINUTE;
				}
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
					logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
				}

			} while ( running > 0 );
			logger.info("{} Responder threads of {} have finished processing", ( responderProcessors.size() - running ), responderProcessors.size());

			String outputFile = SystemConfiguration.getProperty("pir.outputFile") ;
			logger.info("Responder finished, storing result in file: {}", outputFile );
			outputResponse(outputFile);

		} catch (Exception e)
		{
			logger.error("Exception running Responder in JDBC Mode: {}", e.getMessage());
		}

	}

	// Compile the results from all the threads into one response file that will be passed back to the
	// querier for decryption
	public void outputResponse(String outputFile) 
	{
		Response outputResponse = ConsolidateResponse.consolidateResponse(responseQueue, query);
		try {
			new LocalFileSystemStore().store(outputFile, outputResponse);
		} catch (Exception e) {
			logger.error("Error writing Response File {} Exception: {}", outputFile, e.getMessage());
		}
	}
}