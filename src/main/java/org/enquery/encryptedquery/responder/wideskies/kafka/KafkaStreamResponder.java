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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.serialization.LocalFileSystemStore;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
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

  private List<QueryProcessingThread> queryProcessors;
  private List<Thread> queryProcessorThreads;

  private static final String kafkaClientId = SystemConfiguration.getProperty("kafka.clientId", "Encrypted-Query");
  private static final String kafkaBrokers = SystemConfiguration.getProperty("kafka.brokers", "localhost:9092");
  private static final String kafkaGroupId = SystemConfiguration.getProperty("kafka.groupId", "enquery");
  private static final String kafkaTopic = SystemConfiguration.getProperty("kafka.topic", "kafkaTopic");
  private static final Integer streamDuration = Integer.valueOf(SystemConfiguration.getProperty("kafka.streamDuration", "60"));
  private static final Integer streamIterations = Integer.valueOf(SystemConfiguration.getProperty("kafka.streamIterations", "0"));
  private static final Boolean forceFromStart = Boolean.parseBoolean(SystemConfiguration.getProperty("kafka.forceFromStart", "false"));
  private static final Integer numberOfProcessorThreads = Integer.valueOf(SystemConfiguration.getProperty("query.processing.threads", "1"));

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
			  logger.info("Processing Iteration {} for {} seconds", iterationCounter, streamDuration);

			  // Initialize & Start Threads
			  queryProcessors = new ArrayList<>();
			  queryProcessorThreads = new ArrayList<>();
			  for (int i = 0; i < numberOfProcessorThreads; i++) {
				  QueryProcessingThread qpThread =
						  new QueryProcessingThread(newRecordQueue, responseQueue, query); 
				  queryProcessors.add(qpThread);
				  Thread pt = new Thread(qpThread);
				  pt.start();
				  queryProcessorThreads.add(pt);

			  }
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

			  logger.info("Time Duration Expired, issuing stop commands to Threads" );
			  //Shutdown the Consumer Thread
			  consumerThread.stopListening();

			  // Wait for all Processors to finish 
			  try {
				  Thread.sleep(2000);
			  } catch (Exception e) {
				  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
			  }

			  for (QueryProcessingThread qpThread : queryProcessors) {
				  qpThread.stopProcessing();
			  }

			  // Now Wait for all Processors to Update response Queue 
			  try {
				  Thread.sleep(2000);
			  } catch (Exception e) {
				  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
			  }

			  // We have issued a stop order to queue processors and have waited 2 seconds for them to complete
			  // Now poll the processors for a max of 1 minute to see when they are finished
			  long stopCheckingTime = System.currentTimeMillis() + 60000;
			  int queryProcessorsStopped = 0;

			  int running = 0;
			  do {
				  running = 0;
				  queryProcessorsStopped = 0;
				  for (Thread thread : queryProcessorThreads) {
					  if (thread.isAlive()) {
						  running++;
					  } else {
						  queryProcessorsStopped++;
					  }
				  }
				  logger.info( "There are {} query processes still running",running);
				  try {
					  Thread.sleep(500);
				  } catch (Exception e) {
					  logger.error("Error exception sleeping in main Streaming Thread {}", e.getMessage());
				  }

			  } while ( ( running > 0 ) && (System.currentTimeMillis() < stopCheckingTime) );

			  logger.info("{} Query Processors of {} have stopped processing", queryProcessorsStopped, queryProcessors.size());
			  //			  logger.info("Current time {} supposed to finish time {}", System.currentTimeMillis(), endTime);

			  // Set the response object, extract, write to file. 
			  // There will be a separate file for each iteration.
			  String outputFile = SystemConfiguration.getProperty("pir.outputFile") + "-" +(iterationCounter + 1) ;
			  logger.info("Processed iteration {}, storing result in file(s) {}", (iterationCounter + 1), outputFile );
			  outputResponse(outputFile, iterationCounter + 1);
			  //			  new LocalFileSystemStore().store(outputFile, response);
			  iterationCounter++;

		  }

	  } catch (Exception e)
	  {
		  e.printStackTrace();
	  }

  }

  // Sets the elements of the response object that will be passed back to the
  // querier for decryption
  public void outputResponse(String outputFile, int iteration) 
  {
//    logger.debug("numResponseElements = " + columns.size());
    // for(int key: columns.keySet())
    // {
    // logger.debug("key = " + key + " column = " + columns.get(key));
    // }
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
