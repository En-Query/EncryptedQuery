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
package org.enquery.encryptedquery.responder.wideskies.jdbc.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.wideskies.common.ColumnBasedResponderProcessor;
import org.enquery.encryptedquery.responder.wideskies.common.ProcessingUtils;
import org.enquery.encryptedquery.responder.wideskies.common.QueueRecord;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OracleDAOFactory extends DAOFactory {

	private static final Logger logger = LoggerFactory.getLogger(OracleDAOFactory.class);
    private DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");
    
	private Connection dbConnection;
	
	long loadedCounter = 0;
	
	private long maxQueueSize = SystemConfiguration.getLongProperty("responder.maxQueueSize", 1000000);
	private int pauseTimeForQueueCheck = SystemConfiguration.getIntProperty("responder.pauseTimeForQueueCheck", 5);
	private int maxHitsPerSelector = SystemConfiguration.getIntProperty("pir.maxHitsPerSelector", 16000);


	public OracleDAOFactory (Properties props) {
   
	   // Set the JDBC Driver
	   try {
           Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
       } catch (ClassNotFoundException ex)
	   {
           logger.error("Exception loading Oracle Driver {}", ex.getMessage());
           return;
	   } catch (Exception e) {
           logger.error("Exception loading Oracle Driver {}", e.getMessage());
           return;
	   }
	   
	   this.dbConnection = createConnection(props); 
	   
	}

    public Boolean isConnected() {
    	if (dbConnection == null) {
    		return false;
    	} else {
    		return true;
    	}
    }
    
    public long getRecordsLoaded() {
    	return loadedCounter;
    }
    
	public static Connection createConnection(Properties props) {
		Connection conn = null;
        Boolean useSSL = (Boolean)props.get("useSSL");
		
        if (  useSSL == true) {

        	String keystoreFile = props.getProperty("keystore", null);
    		String keystorePassword = props.getProperty("keystorePassword", null);
        	String truststoreFile = props.getProperty("truststore", null);
    		String truststorePassword = props.getProperty("truststorePassword", null);
    		logger.info("SSL Keystore File {}", keystoreFile);
    		logger.info("SSL Truststore File {}", truststoreFile);
    		System.setProperty("javax.net.ssl.keyStore",keystoreFile); 
    		System.setProperty("javax.net.ssl.keyStorePassword",keystorePassword);
    		System.setProperty("javax.net.ssl.trustStore",truststoreFile); 
    		System.setProperty("javax.net.ssl.trustStorePassword",truststorePassword);
        }
		
		String dbUrl = props.getProperty("databaseUrl");
		String dbUser = props.getProperty("databaseUser");
		String dbPassword = props.getProperty("databasePassword");
		String dbName = props.getProperty("databaseName");
		logger.info("Connecting to Oracle {}/{} database using username {}/{}", dbUrl, dbName, dbUser, dbPassword);
		try {
			String connectionUrl = "jdbc:oracle:thin:@//" + dbUrl + "/" + dbName;
			logger.debug("DB Connection URL: {}", connectionUrl);
		    conn =
		       DriverManager.getConnection(connectionUrl, dbUser, dbPassword);

		   logger.info("Connected to Oracle database.");

		} catch (SQLException ex) {
		    // handle any errors
		    System.out.println("SQLException: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		}		
		
		return conn;
	}
	
	public long loadResponderDataDAO(QueryInfo queryInfo, String queryStatement, int hashGroupSize, 
			List<ConcurrentLinkedQueue<QueueRecord>> queues, List<ColumnBasedResponderProcessor> processors) {
		Statement stmt = null;
		ResultSet rs = null;
		HashMap<Integer, Integer> rowIndexCounter; // keeps track of how many hits a given selector has
		HashMap<Integer, Integer> rowIndexOverflowCounter;   // Log how many records exceeded the maxHitsPerSelector
		
        rowIndexCounter = new HashMap<Integer,Integer>();
        rowIndexOverflowCounter = new HashMap<Integer,Integer>();

		
		logger.debug("Max Queue Size {} / Pause length between queue size checks ( {} ) in seconds", maxQueueSize, pauseTimeForQueueCheck);

		try {
			stmt = dbConnection.createStatement();
			rs = stmt.executeQuery(queryStatement);

			
			long recordCounter = 0;
			long noSelectorCount = 0;

			while (rs.next()) {

				int processResult = ProcessRecord.insertRecord(maxHitsPerSelector, rowIndexCounter, rowIndexOverflowCounter, 
			               queryInfo, rs, hashGroupSize, queues);
				if (processResult == 1) {
					loadedCounter++;
				} else if (processResult == 0) {
					noSelectorCount++;
				}
				recordCounter++;

				if ( (loadedCounter % 100000) == 0) {
					logger.info("{} records added to the Queue so far...", numFormat.format(loadedCounter));
	                long processed = ProcessingUtils.recordsProcessed(processors);
					if (loadedCounter - processed > maxQueueSize) {
						Boolean waitForProcessing = true;
	                    while (waitForProcessing) {
	                    	try {
								Thread.sleep(TimeUnit.SECONDS.toMillis(pauseTimeForQueueCheck));
								processed = ProcessingUtils.recordsProcessed(processors);
								long queueSize = loadedCounter - processed;
								
	                            if (queueSize < ( maxQueueSize * 0.01) ) {
	                            	logger.info("Resumed Loading data, Queye Size {}", numFormat.format(queueSize));
	                            	waitForProcessing = false;
	                            }
	                            logger.info("Loading paused to catchup on processing, Queue Size {}", numFormat.format(queueSize));
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								logger.error("Interrupted Exception waiting on main thread {}", e.getMessage() );
							}
	                    }
					}
				}
			}
			logger.info("Loaded {} Records of {} retrieved from database into Queues, {} had no selector.", 
					numFormat.format(loadedCounter), numFormat.format(recordCounter), numFormat.format(noSelectorCount));
		} catch (SQLException ex){
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException sqlEx) { } // ignore

				rs = null;
			}

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException sqlEx) { } // ignore

				stmt = null;
			}
		}
		return loadedCounter;
	}

	public void closeConnection() {
		try {
			dbConnection.close();
			logger.info("MySql Database Connection Closed");
			dbConnection = null;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			logger.error("Exception closing Mysql Connection {}", e.getMessage());
		}
	}

}