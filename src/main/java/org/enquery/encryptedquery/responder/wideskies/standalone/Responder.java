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

package org.enquery.encryptedquery.responder.wideskies.standalone;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import javax.management.timer.Timer;

import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.responder.wideskies.common.ConsolidateResponse;
import org.enquery.encryptedquery.responder.wideskies.common.ProcessingUtils;
import org.enquery.encryptedquery.responder.wideskies.common.QueueRecord;
import org.enquery.encryptedquery.responder.wideskies.common.ColumnBasedResponderProcessor;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.serialization.LocalFileSystemStore;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to perform Encrypted Query in a standalone method
 * Used primarily for testing, although it can be used anywhere in standalone mode
 * Does not use the DataFilter class -- assumes all filtering happens before calling addDataElement()
 * NOTE: Only uses in expLookupTables that are contained in the Query object, not in hdfs as this is a standalone responder
 */
public class Responder
{
	private static final Logger logger = LoggerFactory.getLogger(Responder.class);
	private DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	private Query query = null;
	private QueryInfo queryInfo = null;

	private List<ConcurrentLinkedQueue<QueueRecord>> newRecordQueues = new ArrayList<ConcurrentLinkedQueue<QueueRecord>>();
	private ConcurrentLinkedQueue<Response> responseQueue = new ConcurrentLinkedQueue<Response>();

	private long recordCounter = 0;
	private List<ColumnBasedResponderProcessor> responderProcessors;
	private List<Thread> responderProcessingThreads;

	private static final Integer numberOfProcessorThreads = Integer.valueOf(SystemConfiguration.getProperty("responder.processing.threads", "1"));
	private long maxQueueSize = SystemConfiguration.getLongProperty("responder.maxQueueSize", 1000000);
	private int pauseTimeForQueueCheck = SystemConfiguration.getIntProperty("responder.pauseTimeForQueueCheck", 10);
	private int maxHitsPerSelector = SystemConfiguration.getIntProperty("responder.maxHitsPerSelector", 16000);
	private HashMap<Integer, Integer> rowIndexCounter; // keeps track of how many hits a given selector has
	private HashMap<Integer, Integer> rowIndexOverflowCounter;   // Log how many records exceeded the maxHitsPerSelector
 
	public Responder(Query queryInput)
	{
		query = queryInput;
		queryInfo = query.getQueryInfo();
	}

	public int getPercentComplete() {
		return ProcessingUtils.getPercentComplete(recordCounter, responderProcessors);
	}

	/**
	 * Method to compute the standalone response
	 * Assumes that the input data is a single file in the local filesystem and is fully qualified
	 */
	public void computeStandaloneResponse() throws IOException
	{
		String inputData = SystemConfiguration.getProperty("pir.inputData");
        long selectorNullCount = 0;
        rowIndexCounter = new HashMap<Integer,Integer>();
        rowIndexOverflowCounter = new HashMap<Integer,Integer>();
        
		// Based on the number of processor threads, calculate the number of hashes for each queue
		int hashGroupSize = ((1 << queryInfo.getHashBitSize()) + numberOfProcessorThreads -1 ) / numberOfProcessorThreads;
		logger.info("Based on {} Processor Thread(s) the hashGroupSize is {} and maxQueueSize {}",
				numberOfProcessorThreads, numFormat.format(hashGroupSize), numFormat.format(maxQueueSize));

        // Create a Queue for each thread
		for (int i = 0 ; i < numberOfProcessorThreads; i++) {
			newRecordQueues.add(new ConcurrentLinkedQueue<QueueRecord>());
		}

		logger.debug("Max Queue Size {} / Pause length between queue size checks ( {} ) in seconds", maxQueueSize, pauseTimeForQueueCheck);

		// Initialize & Start Processing Threads
		responderProcessors = new ArrayList<>();
		responderProcessingThreads = new ArrayList<>();
		for (int i = 0; i < numberOfProcessorThreads; i++) {
			ColumnBasedResponderProcessor qpThread =
					new ColumnBasedResponderProcessor(newRecordQueues.get(i), responseQueue, query);
			responderProcessors.add(qpThread);
			Thread pt = new Thread(qpThread);
			pt.start();
			responderProcessingThreads.add(pt);
		}

		// Read data file and add records to the queue
		BufferedReader br = new BufferedReader(new FileReader(inputData));
		String line;
		logger.info("Reading and processing input file: {}", inputData);
		JSONParser jsonParser = new JSONParser();

		Boolean waitForProcessing = false;
		while ((line = br.readLine()) != null )
		{
			JSONObject jsonData = null;
			String selector = null;
			try {
				jsonData = (JSONObject) jsonParser.parse(line);
				//					  logger.info("jsonData = " + jsonData.toJSONString());
			} catch (Exception e) {
				logger.error("Exception ( {} ) JSON parsing input record {}", e.getMessage(), line);
			}
			if (jsonData != null) {
				selector = QueryUtils.getSelectorByQueryTypeJSON(queryInfo.getQuerySchema(), jsonData).trim();
				if (selector != null && selector.length() > 0) {
					try {
						int rowIndex = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selector);
//			            logger.info("Selector {} / Hash {}", selector, rowIndex);			

						// Track how many "hits" there are for each selector (Converted into rowIndex) 
						if ( rowIndexCounter.containsKey(rowIndex) ) {
							rowIndexCounter.put(rowIndex, (rowIndexCounter.get(rowIndex) + 1));
						} else {
							rowIndexCounter.put(rowIndex, 1);
						}
						
						// If we are not over the max hits value add the record to the appropriate queue
						if ( rowIndexCounter.get(rowIndex) <= maxHitsPerSelector) {
							List<Byte> parts = QueryUtils.partitionDataElement(queryInfo.getQuerySchema(), jsonData, queryInfo.getEmbedSelector());
							QueueRecord qr = new QueueRecord(rowIndex, selector, parts);
							int whichQueue = rowIndex / hashGroupSize;
							newRecordQueues.get(whichQueue).add(qr);
							recordCounter++;
						} else {
							if ( rowIndexOverflowCounter.containsKey(rowIndex) ) {
								rowIndexOverflowCounter.put(rowIndex, (rowIndexOverflowCounter.get(rowIndex) + 1));
							} else {
								rowIndexOverflowCounter.put(rowIndex, 1);
							}
						}
					} catch (Exception e) {
						logger.error("Exception computing hash for selector {}, Exception: {}", selector, e.getMessage());
					}
				} else {
					selectorNullCount++;
				}
			} else {
				logger.error("Error JSON parsing line {}", line);
			}

			if ( (recordCounter % 100000) == 0) {
				logger.info("{} records added to the Queue so far...", numFormat.format(recordCounter));

				// Adding records to the queue is faster than removing them.  Pause loading if there are
				// too many records on the queue so we do not blow through memory.
				long processed = ProcessingUtils.recordsProcessed(responderProcessors);
				if (recordCounter - processed > maxQueueSize) {
					waitForProcessing = true;
					while (waitForProcessing) {
						try {
							Thread.sleep(TimeUnit.SECONDS.toMillis(pauseTimeForQueueCheck));
							processed = ProcessingUtils.recordsProcessed(responderProcessors);
							long queueSize = recordCounter - processed;

							if (queueSize < ( maxQueueSize * 0.01 )) {
								waitForProcessing = false;
							}
							logger.info("Loading paused to catchup on processing, Queue Size {}", numFormat.format(queueSize));
						} catch (Exception e) {
							logger.error("Interrupted Exception waiting on main thread {}", e.getMessage() );
						}
					}
				}
			}

		}
		if (selectorNullCount > 0) {
			logger.warn("{} Records had a null selector from source", selectorNullCount);
		}
		br.close();
		logger.info("Imported {} records for processing", numFormat.format(recordCounter));
        if (rowIndexOverflowCounter.size() > 0) {
            for ( int i : rowIndexOverflowCounter.keySet() ) {
            	logger.info("rowIndex {} exceeded max Hits {} by {}", i, maxHitsPerSelector, rowIndexOverflowCounter.get(i));
            }
        }
		// All data has been submitted to the queue so issue a stop command to the threads so they will return
		// results when the queue is empty
		for (ColumnBasedResponderProcessor qpThread : responderProcessors) {
			qpThread.stopProcessing();
		}

        // Loop through processing threads until they are finished processing.  Report how many are still running every minute.
		long notificationTimer = System.currentTimeMillis() + Timer.ONE_MINUTE;
		int responderProcessorsStopped = 0;
		int running = 0;
		do {
			running = 0;
			responderProcessorsStopped = 0;
			for (Thread thread : responderProcessingThreads) {
				if (thread.isAlive()) {
					running++;
				} else {
					responderProcessorsStopped++;
				}
			}
			if ( System.currentTimeMillis() > notificationTimer ) {
				long recordsProcessed = ProcessingUtils.recordsProcessed(responderProcessors);
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

		logger.info("{} responder threads of {} have finished processing", responderProcessorsStopped, responderProcessors.size());

		String outputFile = SystemConfiguration.getProperty("pir.outputFile");
		outputResponse( outputFile );
	}

	// Compile the results from all the threads into one response file.
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

