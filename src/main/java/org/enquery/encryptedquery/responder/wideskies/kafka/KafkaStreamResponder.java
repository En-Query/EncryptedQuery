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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import javax.management.timer.Timer;

import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.wideskies.common.ConsolidateResponse;
import org.enquery.encryptedquery.responder.wideskies.common.ProcessingUtils;
import org.enquery.encryptedquery.responder.wideskies.common.QueueRecord;
import org.enquery.encryptedquery.responder.wideskies.common.RowBasedResponderProcessor;
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
  private DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

  private Query query = null;
  private QueryInfo queryInfo = null;
  
  private List<ConcurrentLinkedQueue<QueueRecord>> newRecordQueues = new ArrayList<ConcurrentLinkedQueue<QueueRecord>>();
  private ConcurrentLinkedQueue<Response> responseQueue = new ConcurrentLinkedQueue<Response>();

  private List<RowBasedResponderProcessor> responderProcessors;
  private List<Thread> responderProcessingThreads;

  private static final String kafkaClientId = SystemConfiguration.getProperty("kafka.clientId", "Encrypted-Query");
  private static final String kafkaBrokers = SystemConfiguration.getProperty("kafka.brokers", "localhost:9092");
  private static final String kafkaGroupId = SystemConfiguration.getProperty("kafka.groupId", "enquery");
  private static final String kafkaTopic = SystemConfiguration.getProperty("kafka.topic", "kafkaTopic");
  private static final Integer streamDuration = Integer.valueOf(SystemConfiguration.getProperty("kafka.streamDuration", "60"));
  private static final Integer streamIterations = Integer.valueOf(SystemConfiguration.getProperty("kafka.streamIterations", "0"));
  private static Boolean forceFromStart = Boolean.parseBoolean(SystemConfiguration.getProperty("kafka.forceFromStart", "false"));
  private static final Integer numberOfProcessorThreads = Integer.valueOf(SystemConfiguration.getProperty("responder.processing.threads", "1"));
  private int pauseTimeForQueueCheck = SystemConfiguration.getIntProperty("responder.pauseTimeForQueueCheck", 5);
  private long maxQueueSize = SystemConfiguration.getLongProperty("responder.maxQueueSize", 1000000);

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

		  // Based on the number of processor threads, calculate the number of hashes for each queue
		  int hashGroupSize = ((1 << queryInfo.getHashBitSize()) + numberOfProcessorThreads -1 ) / numberOfProcessorThreads;
		  logger.info("Based on numberOfProcessorThreads {}, the hashGroupSize is {}", numberOfProcessorThreads, hashGroupSize);
		  for (int i = 0 ; i < numberOfProcessorThreads; i++) {
			  newRecordQueues.add(new ConcurrentLinkedQueue<QueueRecord>());
		  }

		  logger.debug("Max Queue Size {} / Pause length between queue size checks ( {} ) in seconds", maxQueueSize, pauseTimeForQueueCheck);

		  int iterationCounter = 0;
		  while ( (streamIterations == 0 ||  iterationCounter < streamIterations ) ) {
			  logger.info("Processing Iteration {} for {} seconds", iterationCounter + 1, streamDuration);

			  // Initialize & Start Processing Threads
			  responderProcessors = new ArrayList<>();
			  responderProcessingThreads = new ArrayList<>();
			  for (int i = 0; i < numberOfProcessorThreads; i++) {
				  RowBasedResponderProcessor qpThread =
						  new RowBasedResponderProcessor(newRecordQueues.get(i), responseQueue, query); 
				  responderProcessors.add(qpThread);
				  Thread pt = new Thread(qpThread);
				  pt.start();
				  responderProcessingThreads.add(pt);
			  }

			  // Start the Consumer Thread 
			  Properties hdfsProperties = null;
			  KafkaConsumerThread consumerThread =
					  new KafkaConsumerThread(kafkaProperties, kafkaTopic,
							  hdfsProperties, queryInfo, hashGroupSize, newRecordQueues); 

			  Thread ct = new Thread(consumerThread);
			  ct.start();

			  //Wait for Time Window to expire
			  long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(streamDuration);
			  Boolean paused = false;
			  while (System.currentTimeMillis() < endTime) {
				  long queueSize = consumerThread.recordsLoaded() - ProcessingUtils.recordsProcessedRowBased(responderProcessors);
				  if (queueSize > maxQueueSize) {
					  consumerThread.pauseLoading(true);
					  logger.info("Pause loading to catchup on processing, Queue Size {}", numFormat.format(queueSize));
					  paused = true;
				  } else {
					  consumerThread.pauseLoading(false);
					  if (paused) {
						  logger.info("Resuming queue loading, Queue Size {}", numFormat.format(queueSize));
						  paused = false;
					  }
				  }
				  try {
					  Thread.sleep(Timer.ONE_SECOND);
				  } catch (InterruptedException e) {
					  // TODO Auto-generated catch block
					  logger.error("Interrupted Exception waiting on main thread {}", e.getMessage() );
				  }
			  }

			  logger.info("Iteration {} time duration expired, issuing stop command to Threads", iterationCounter + 1 );
			  consumerThread.stopListening();

			  // Give the processors a few seconds to process any remaining records. 
			  try {
				  Thread.sleep(2000);
			  } catch (Exception e) {
				  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
			  }

			  for (RowBasedResponderProcessor qpThread : responderProcessors) {
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
					  logger.info( "There are {} responder processes still running", running);
					  notificationTimer = System.currentTimeMillis() + Timer.ONE_MINUTE;
				  }
				  try {
					  Thread.sleep(1000);
				  } catch (Exception e) {
					  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
				  }

			  } while ( running > 0 );
			  logger.info("{} Responder threads of {} have finished processing", ( responderProcessors.size() - running ), responderProcessors.size());

			  String outputFile = SystemConfiguration.getProperty("pir.outputFile") + "-" +(iterationCounter + 1) ;
			  logger.info("Iteration {} finished, storing result in file: {}", (iterationCounter + 1), outputFile );
			  outputResponse(outputFile);
			  iterationCounter++;
			  // We do not want to re-process records already done in the 1st iteration if forceFromStart was set.  
			  // This will set the remainder of the iterations to start from the last record processed in the stream.
			  if (forceFromStart) {
				  forceFromStart = false;
			  }

		  }

	  } catch (Exception e)
	  {
		  logger.error("Exception running Responder in Kafka Streaming Mode: {}", e.getMessage());
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
