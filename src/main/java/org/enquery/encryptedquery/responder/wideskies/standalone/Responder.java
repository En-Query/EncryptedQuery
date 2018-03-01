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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import javax.management.timer.Timer;

import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.wideskies.streamProcessing.ResponderProcessingThread;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.serialization.LocalFileSystemStore;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to perform stand alone responder functionalities
 * <p>
 * Used primarily for testing, although it can be used anywhere in standalone mode
 * <p>
 * Does not bound the number of hits that can be returned per selector
 * <p>
 * Does not use the DataFilter class -- assumes all filtering happens before calling addDataElement()
 * <p>
 * NOTE: Only uses in expLookupTables that are contained in the Query object, not in hdfs as this is a standalone responder
 */
public class Responder
{
  private static final Logger logger = LoggerFactory.getLogger(Responder.class);

  private Query query = null;
  private QueryInfo queryInfo = null;

  private ConcurrentLinkedQueue<String> newRecordQueue = new ConcurrentLinkedQueue<String>();
  private ConcurrentLinkedQueue<Response> responseQueue = new ConcurrentLinkedQueue<Response>();
  
  private List<ResponderProcessingThread> responderProcessors;
  private List<Thread> responderProcessingThreads;

  private static final Integer numberOfProcessorThreads = Integer.valueOf(SystemConfiguration.getProperty("query.processing.threads", "1"));

  public Responder(Query queryInput)
  {
    query = queryInput;

  }

  /**
   * Method to compute the standalone response
   * <p>
   * Assumes that the input data is a single file in the local filesystem and is fully qualified
   */
  public void computeStandaloneResponse() throws IOException
  {
	  String inputData = SystemConfiguration.getProperty("pir.inputData");
	  try
	  {
		  // Initialize & Start Processing Threads
		  responderProcessors = new ArrayList<>();
		  responderProcessingThreads = new ArrayList<>();
		  for (int i = 0; i < numberOfProcessorThreads; i++) {
			  ResponderProcessingThread qpThread =
					  new ResponderProcessingThread(newRecordQueue, responseQueue, query); 
			  responderProcessors.add(qpThread);
			  Thread pt = new Thread(qpThread);
			  pt.start();
			  responderProcessingThreads.add(pt);
		  }    

          // Read data file and add records to the queue
		  BufferedReader br = new BufferedReader(new FileReader(inputData));
		  String line;
		  logger.info("Reading and processing datafile...");
		  int lineCounter = 0;
		  while ((line = br.readLine()) != null)
		  {
			  newRecordQueue.add(line);

			  lineCounter++;
			  if ( (lineCounter % 100000) == 0) {
				  logger.info("{} records added to the Queue so far...", lineCounter);
			  }
		  }
		  br.close();
		  logger.info("Imported {} total records for processing", lineCounter);
		  
		  //Wait 10 seconds for the processing threads to start their work
		  try {
			  Thread.sleep(TimeUnit.SECONDS.toMillis(10));
		  } catch (Exception e) {
			  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
		  }
		  
          // All data has been submitted to the queue so issue a stop command to the threads so they will return 
		  // results when the queue is empty
		  for (ResponderProcessingThread qpThread : responderProcessors) {
			  qpThread.stopProcessing();
		  }

		  // Wait a few seconds for the stop order to be registered with all the threads then start
		  // polling for them to finish 
		  try {
			  Thread.sleep(2000);
		  } catch (Exception e) {
			  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
		  }

		  long notificationTimer = System.currentTimeMillis() + Timer.ONE_MINUTE;
		  int responderProcessorsStopped = 0;
          int stoppedProcessorsReported = -1;
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
              if ( ( stoppedProcessorsReported != responderProcessorsStopped ) || ( stoppedProcessorsReported == -1 ) ||
            		  ( System.currentTimeMillis() > notificationTimer )) {
            	  logger.info( "There are {} responder processes still running",running);
            	  stoppedProcessorsReported = responderProcessorsStopped;
            	  notificationTimer = System.currentTimeMillis() + Timer.ONE_MINUTE;
              }
			  try {
				  Thread.sleep(1000);
			  } catch (Exception e) {
				  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
			  }

		  } while ( running > 0 );

		  logger.info("{} responder threads of {} have finished processing", responderProcessorsStopped, responderProcessors.size());
		  
	  } catch (Exception e)
	  {
		  e.printStackTrace();
	  }

	  String outputFile = SystemConfiguration.getProperty("pir.outputFile");
	  outputResponse( outputFile );
  }
  
  // Compile the results from all the threads into one response file that will be passed back to the
  // querier for decryption
  public void outputResponse(String outputFile) 
  {
	  Response outputResponse = new Response(queryInfo);
	  Response nextResponse;
	  int processorCounter = 0;
	  while ((nextResponse = responseQueue.poll()) != null) {
		  for (TreeMap<Integer, BigInteger> nextItem : nextResponse.getResponseElements()) {
			  outputResponse.addResponseElements(nextItem);
		  }
		  processorCounter++;
	  }
	  logger.info("Combined {} response files into outputFile {}", processorCounter, outputFile);
	  try {
		  new LocalFileSystemStore().store(outputFile, outputResponse);
		  processorCounter++;
	  } catch (Exception e) {
		  logger.error("Error writing Response File {} Exception: {}", outputFile, e.getMessage());
	  }
  }

}
