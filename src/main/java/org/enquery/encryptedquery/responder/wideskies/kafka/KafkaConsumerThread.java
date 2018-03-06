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
import java.net.URI;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConsumerThread implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerThread.class);

	private final KafkaConsumer<String, String> consumer;
    private final String hdfsURI;
    private final String hdfsUser;
    private final String hdfsFolder;
    private final String uuidString;
    private final Properties kafkaProperties;
    private final String topic;
    private ConcurrentLinkedQueue<String> inputQueue;
    private volatile boolean stopConsumer = false;
    private boolean hdfsOutput = false;
    
	public KafkaConsumerThread(Properties kafkaProperties, String topic, String hdfsuri,
			     String hdfsUser, String hdfsFolder, UUID uuid, ConcurrentLinkedQueue<String> inputQueue) {
		logger.info("Initializing kafka Consumer thread");
        this.kafkaProperties = kafkaProperties;
        this.inputQueue = inputQueue;
        this.hdfsURI = hdfsuri;
 		this.topic = topic;
		this.hdfsUser = hdfsUser;
		this.hdfsFolder = hdfsFolder;
		if (uuid == null) {
			this.uuidString = "none";
		} else {
	        this.uuidString = uuid.toString();
		}
 		this.consumer = new KafkaConsumer<>(kafkaProperties);
		this.consumer.subscribe(Arrays.asList(topic));

	    if (hdfsuri != null && hdfsUser != null && hdfsFolder != null) {
	    	hdfsOutput = true;
	    	logger.info("Kafka Consumer output to HDFS");
	    } else if (inputQueue != null) {
	    	hdfsOutput = false;
	    	logger.info("Kafka Consumer output to Queue");
	    } else {
	    	logger.error("Cannot determine output path for data: Hdfs & Queue info are null");
	    }
	    	
	}

	public void stopListening() {
	     stopConsumer = true;
	     logger.info("Stop consumer listening command received");
	   }

	@Override
	public void run() {

		logger.info("Running kafka Consumer thread {}", Thread.currentThread().getId());
		FSDataOutputStream outputStream = null;

		//Test connection to Kafka Server and the topic's existence
		try {
			Map<String, List<PartitionInfo>> topics = consumer.listTopics();
			if (topics.containsKey(topic)) {
				logger.info("Consuming records from Kafka Topic {} which has {} partitions"
						, topic, topics.get(topic).size());
			} else {
				stopConsumer = true;
				logger.error("Kafka Topic {} not found in server", topic);
			}

		} catch (Exception e) {
			logger.error("Connection Error to Kafka Broker {} exception: {}", kafkaProperties.getProperty("bootstrap.servers", null), e.getMessage());
			stopConsumer = true;			
		}

		if (!stopConsumer && hdfsOutput) {
               outputStream = setupHdfs();
		}
		long recordCounter = 0;
		//		int processedCounter = 0;
		while (!stopConsumer) {
			ConsumerRecords<String, String> records = consumer.poll(100);
			for (ConsumerRecord<String, String> record : records) {
//								logger.info("Receive message: " + record.value() + ", Partition: "
//										+ record.partition() + ", Offset: " + record.offset() + ", by ThreadID: "
//										+ Thread.currentThread().getId());
				try {
                    if (hdfsOutput) {
					    outputStream.writeBytes( record.value() + "\n");
                    } else {
                    	inputQueue.add( record.value() + "\n");
                    }
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				recordCounter++;
				//				processedCounter++;
			}
			//			logger.info("Read {} new records from Kafka in consumer thread {}", processedCounter, Thread.currentThread().getId());
			//			processedCounter = 0;
		}
		logger.info("Consumed {} records from Kafka Topic {} in thread {}",recordCounter, topic, Thread.currentThread().getId());	
		try {
			if (outputStream != null ) {
				outputStream.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private FSDataOutputStream setupHdfs() {
	    FSDataOutputStream outputStream;
		String hdfsuri = hdfsURI;

	    // ====== Init HDFS File System Object
		Configuration conf = new Configuration();
		conf.set("fs.defaultFS", hdfsuri);
		conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
		conf.set("fs.file.impl", org.apache.hadoop.fs.LocalFileSystem.class.getName());
		System.setProperty("HADOOP_USER_NAME", hdfsUser);
		System.setProperty("hadoop.home.dir", "/user");
		FileSystem fs = null;
		try {
			fs = FileSystem.get(URI.create(hdfsuri), conf);
		} catch (IOException e) {
			logger.error("Exception connecting to Hadoop filesystem {} error: {}", hdfsuri, e.getMessage());
			stopConsumer = true;
			e.printStackTrace();
		}
		//==== Create folder in HDFS if it does not already exists
		Path hdfsWorkingDir = fs.getWorkingDirectory();
		MultiThreadedKafkaStreamResponder.setWorkingFolder(hdfsWorkingDir.toString());
		String path = KafkaUtils.getOutputFolder(hdfsWorkingDir.toString(),  hdfsFolder,  uuidString);
		path += "/data";
		logger.info("HDFS Data Output folder {}", path);
		Path newFolderPath= new Path(path);
		try {
			if(!fs.exists(newFolderPath)) {
				// Create new Directory
				fs.mkdirs(newFolderPath);
				logger.debug("HDFS Path "+path+" created.");
			}
		} catch (Exception e) {
			e.printStackTrace(); 
		}

		//=== Create the HDFS file ===
		try {
			Path hdfsFile = new Path(newFolderPath + "/" + topic + "-" + Thread.currentThread().getId());
			outputStream = fs.create(hdfsFile);
			return outputStream;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}