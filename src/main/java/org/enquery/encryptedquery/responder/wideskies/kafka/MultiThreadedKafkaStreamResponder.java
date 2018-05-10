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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.SystemConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiThreadedKafkaStreamResponder {

	private static final Logger logger = LoggerFactory.getLogger(MultiThreadedKafkaStreamResponder.class);

	private UUID uuid;
	private List<UUID> dataWindowIds = new ArrayList<UUID>();
	private Query query = null;

	private static final String kafkaClientId = SystemConfiguration.getProperty("kafka.clientId", "Encrypted-Query");
	private static final String kafkaBrokers = SystemConfiguration.getProperty("kafka.brokers", "localhost:9092");
	private static final String kafkaGroupId = SystemConfiguration.getProperty("kafka.groupId", "enquery");
	private static final String kafkaTopic = SystemConfiguration.getProperty("kafka.topic", "kafkaTopic");
	private static final int kafkaNumberOfConsumers = SystemConfiguration.getIntProperty("kafka.number.of.consumers", 10);
	private static Boolean forceFromStart = Boolean.parseBoolean(SystemConfiguration.getProperty("kafka.forceFromStart", "false"));

	private static final String hdfsURI = SystemConfiguration.getProperty("hadoop.URI", "hdfs://localhost:9000");
	private static final String hdfsUser = SystemConfiguration.getProperty("hadoop.user", "enquery");
	private static final String hdfsFolder = SystemConfiguration.getProperty("hadoop.folder", "encrypted-query");
	private static final Integer hdfsFileCount = SystemConfiguration.getIntProperty("hadoop.file.count", 4);
	private static String workingFolder;

	private static final Integer streamDuration = Integer.valueOf(SystemConfiguration.getProperty("kafka.streamDuration", "60"));
	private static final Integer streamIterations = Integer.valueOf(SystemConfiguration.getProperty("kafka.streamIterations", "0"));

	public MultiThreadedKafkaStreamResponder(Query query)
	{
		this.query = query;
		logger.info("MultiThreaded Kafka Stream Responder....");
		logger.info("hdfsfilecount {}", hdfsFileCount);
	}

	public static void setWorkingFolder(String workingFolder) {
		MultiThreadedKafkaStreamResponder.workingFolder = workingFolder;
		logger.info("Working Folder set to {}", workingFolder);
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

	/**
	 * Method to compute the response
	 * <p>
	 * Assumes that the input data is from a kafka topic and is fully qualified
	 */
	public void computeKafkaStreamResponse() throws IOException
	{
		try
		{
			// Based on the number of processor threads, calculate the number of hashes for each queue
			int hashGroupSize = ((1 << query.getQueryInfo().getHashBitSize()) + hdfsFileCount -1 ) / hdfsFileCount;
			logger.info("Based on hdfsNodes {}, the hashGroupSize is {}", hdfsFileCount, hashGroupSize);

			Properties kafkaProperties = null;
			Properties hdfsProperties = new Properties();
			hdfsProperties.put("hdfsUri", hdfsURI);
			hdfsProperties.put("hdfsUser", hdfsUser);
			hdfsProperties.put("hdfsFolder", hdfsFolder);
			hdfsProperties.put("hdfsFileCount", hdfsFileCount);

			int iterationCounter = 0;
			while (streamIterations == 0 || iterationCounter < streamIterations ) {

				kafkaProperties = createConsumerConfig(kafkaBrokers, kafkaGroupId, kafkaClientId, forceFromStart);
				logger.info("Kafka: Brokers {} | GroupId {} | Topic {} | ForceFromStart {}", 
						kafkaBrokers, kafkaGroupId, kafkaTopic, forceFromStart);
				logger.info("HDFS: URI {} | User {} | Folder {} | Number of Consumers {}", 
						hdfsURI, hdfsUser, hdfsFolder, kafkaNumberOfConsumers);

				// Unique Id is used to keep each window of data separate
				uuid = UUID.randomUUID();
				dataWindowIds.add(uuid);
				hdfsProperties.put("uuid", uuid.toString());
				logger.info("Processing Iteration {} of {} for {} seconds", iterationCounter, 
						streamIterations, streamDuration);

				KafkaConsumerGroup consumerGroup =
						new KafkaConsumerGroup(kafkaProperties, kafkaTopic, hdfsProperties, kafkaNumberOfConsumers, query.getQueryInfo(), hashGroupSize);
				consumerGroup.execute();

				// Wait until the time window has finished before we stop the consumers 
				long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(streamDuration);
				while (System.currentTimeMillis() < endTime)
				{

					try {
						Thread.sleep(1000);
					} catch (InterruptedException ie) {

					}					
				}

				consumerGroup.stopConsumers();
				//It may take a few seconds to stop the thread so lets wait before we process the results
				try {
					Thread.sleep(2000);
				} catch (InterruptedException ie) {

				}				

				String outputFolder = KafkaUtils.getOutputFolder(workingFolder, hdfsFolder, uuid.toString());

				//Since we are sending this to Hadoop Map/Reduce we need to remove the hdfsURI from the folder
				if (outputFolder.startsWith(hdfsURI)) {
					outputFolder = outputFolder.substring(hdfsURI.length());
				}
				logger.info("HDFS output Folder {}", outputFolder);

                // Start the Hadoop Map/Reduce Job to process the data captured in this time window
				try
				{
					KafkaMapReduceThread mrThread = new KafkaMapReduceThread(outputFolder, iterationCounter);
					Thread t = new Thread(mrThread);
					t.start();
				} catch (Exception e)
				{
					throw new PIRException(e);
				}

				// We do not want to re-process records already done in the 1st iteration if forceFromStart was set.  
				// This will set the remainder of the iterations to start from the last record processed in the stream.
				if (forceFromStart) {
					forceFromStart = false;
				}

				iterationCounter++;

			}
			logger.info("Finished processing {} iterations", iterationCounter);

		} catch (Exception e)
		{
			e.printStackTrace();
		}

	}
}