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
package org.enquery.encryptedquery.responder.wideskies.kafka;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import javax.management.timer.Timer;

import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.wideskies.common.ResponderProcessingThread;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.serialization.LocalFileSystemStore;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to perform kafka responder functionalities
 * <p>
 * Does not bound the number of hits that can be returned per selector
 * <p>
 * Does not use the DataFilter class -- assumes all filtering happens before calling addDataElement()
 * <p>
 * NOTE: Only uses in expLookupTables that are contained in the Query object, not in hdfs
 */
public class KafkaStreamResponder
{
  private static final Logger logger = LoggerFactory.getLogger(KafkaStreamResponder.class);

  private Query query = null;
  private QueryInfo queryInfo = null;
  
  private  ConcurrentLinkedQueue<String> newRecordQueue = new ConcurrentLinkedQueue<String>();
  private ConcurrentLinkedQueue<Response> responseQueue = new ConcurrentLinkedQueue<Response>();

  private List<ResponderProcessingThread> responderProcessors;
  private List<Thread> responderProcessingThreads;

  private static final String kafkaClientId = SystemConfiguration.getProperty("kafka.clientId", "Encrypted-Query");
  private static final String kafkaBrokers = SystemConfiguration.getProperty("kafka.brokers", "localhost:9092");
  private static final String kafkaGroupId = SystemConfiguration.getProperty("kafka.groupId", "enquery");
  private static final String kafkaTopic = SystemConfiguration.getProperty("kafka.topic", "kafkaTopic");
  private static final Integer streamDuration = Integer.valueOf(SystemConfiguration.getProperty("kafka.streamDuration", "60"));
  private static final Integer streamIterations = Integer.valueOf(SystemConfiguration.getProperty("kafka.streamIterations", "0"));
  private static final Boolean forceFromStart = Boolean.parseBoolean(SystemConfiguration.getProperty("kafka.forceFromStart", "false"));
  private static final Integer numberOfProcessorThreads = Integer.valueOf(SystemConfiguration.getProperty("responder.processing.threads", "1"));

  private Properties kafkaProperties;

  private Response response = null;

  public KafkaStreamResponder(Query queryInput)
  {
    this.query = queryInput;
    this.queryInfo = queryInput.getQueryInfo();
    
    kafkaProperties = createConsumerConfig(kafkaBrokers, kafkaGroupId, kafkaClientId, forceFromStart);

  }

	private static Properties createConsumerConfig(String brokers, String groupId, String clientId, 
			boolean forceFromStart) {

		logger.info("Configuring Kafka Consumer");
		Properties props = new Properties();
		props.put("bootstrap.servers", brokers);
		props.put("group.id", groupId);
//		props.put("client.id", clientId);
		props.put("enable.auto.commit", "true");
		props.put("auto.commit.interval.ms", "1000");
		props.put("session.timeout.ms", "30000");

        if (forceFromStart) {
        	props.put("auto.offset.reset", "earliest");
        	
        } else {
        	props.put("auto.offset.reset", "latest");
        }
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		return props;
	}
 
  public Response getResponse()
  {
    return response;
  }

  /**
   * Method to compute the response
   * <p>
   * Assumes that the input data is from a kafka topic and is fully qualified
   */
  public void computeKafkaStreamResponse() throws IOException
  {

	  try
	  {
		  logger.info("Kafka: ClientId {} | Brokers {} | GroupId {} | Topic {} | ForceFromStart {}", 
				  kafkaClientId, kafkaBrokers, kafkaGroupId, kafkaTopic, forceFromStart);
		  logger.info("Responder info: TimeDuration {} | Iterations {} | Number of Processors {}", 
				  streamDuration, streamIterations, numberOfProcessorThreads);
		  int iterationCounter = 0;
		  while ( (streamIterations == 0 ||  iterationCounter < streamIterations ) ) {
			  logger.info("Processing Iteration {} for {} seconds", iterationCounter + 1, streamDuration);

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
			  
			  // Start the Consumer Thread 
			  KafkaConsumerThread consumerThread =
					  new KafkaConsumerThread(kafkaProperties, kafkaTopic,
							  null, null, null, null, newRecordQueue); 
			  Thread ct = new Thread(consumerThread);
			  ct.start();

			  //Wait for Time Window to expire
			  try {
				  Thread.sleep(TimeUnit.SECONDS.toMillis(streamDuration));
			  } catch (Exception e) {
				  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
			  }

			  logger.info("Iteration {} time duration expired, issuing stop command to Threads", iterationCounter + 1 );
			  consumerThread.stopListening();

			  // Give the processors a few seconds to process any remaining records. 
			  try {
				  Thread.sleep(2000);
			  } catch (Exception e) {
				  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
			  }

			  for (ResponderProcessingThread qpThread : responderProcessors) {
				  qpThread.stopProcessing();
			  }

			  // Now Wait for all Processors to Update response Queue 
			  try {
				  Thread.sleep(2000);
			  } catch (Exception e) {
				  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
			  }

			  // We have issued a stop command to the responder processors
			  // Now poll the processors until they are finished
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

			  logger.info("{} Responder threads of {} have finished processing", responderProcessorsStopped, responderProcessors.size());

			  String outputFile = SystemConfiguration.getProperty("pir.outputFile") + "-" +(iterationCounter + 1) ;
			  logger.info("Iteration {} finished, storing result in file: {}", (iterationCounter + 1), outputFile );
			  outputResponse(outputFile, iterationCounter + 1);
			  iterationCounter++;

		  }

	  } catch (Exception e)
	  {
		  e.printStackTrace();
	  }

  }

  // Compile the results from all the threads into one response file that will be passed back to the
  // querier for decryption
  public void outputResponse(String outputFile, int iteration) 
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
	  logger.info("Combined responses from {} processors into outputFile {}", processorCounter, outputFile);
	  try {
		  new LocalFileSystemStore().store(outputFile, outputResponse);
		  processorCounter++;
	  } catch (Exception e) {
		  logger.error("Error writing Response File {} Exception: {}", outputFile, e.getMessage());
	  }
  }
}
