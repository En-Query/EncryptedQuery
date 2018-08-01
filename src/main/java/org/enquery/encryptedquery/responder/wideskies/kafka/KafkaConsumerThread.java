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
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import javax.management.timer.Timer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.responder.wideskies.common.QueueRecord;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConsumerThread implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerThread.class);

	private final KafkaConsumer<String, String> consumer;
    private String hdfsURI;
    private String hdfsUser;
    private String hdfsFolder;
    private String hdfsFileCount;
    private String uuidString;
    private Properties kafkaProperties;
//    private Properties hdfsProperties;
    private final String topic;
    private List<ConcurrentLinkedQueue<QueueRecord>> inputQueues;
    private final QueryInfo queryInfo;
    private final int hashGroupSize;
    private volatile boolean stopConsumer = false;
    private boolean hdfsOutput = false;
	private long processedCounter = 0;
	private boolean pauseLoading = false;
    
	private int maxHitsPerSelector = SystemConfiguration.getIntProperty("pir.maxHitsPerSelector", 16000);
	private HashMap<Integer, Integer> rowIndexCounter; // keeps track of how many hits a given selector has
	private HashMap<Integer, Integer> rowIndexOverflowCounter;   // Log how many records exceeded the maxHitsPerSelector
	
	public KafkaConsumerThread(Properties kafkaProperties, String topic, Properties hdfsProperties,
			QueryInfo queryInfo, int hashGroupSize, List<ConcurrentLinkedQueue<QueueRecord>> inputQueues) {

		logger.info("Initializing kafka Consumer thread");
        this.kafkaProperties = kafkaProperties;
        this.inputQueues = inputQueues;
        if (hdfsProperties != null) {
        	logger.info("hdfsProperties {}", hdfsProperties.toString());
            this.hdfsURI = hdfsProperties.getProperty("hdfsUri");
    		this.hdfsUser = hdfsProperties.getProperty("hdfsUser");
    		this.hdfsFolder = hdfsProperties.getProperty("hdfsFolder");
    		this.hdfsFileCount = hdfsProperties.getProperty("hdfsFileCount");
            this.uuidString = hdfsProperties.getProperty("uuid", "none");
            hdfsOutput = true;        	
	    	logger.info("Kafka Consumer output to HDFS");
        } else if (inputQueues != null) {
	    	logger.info("Kafka Consumer output to Queue");
        } else {
	    	logger.error("Cannot determine output path for data: Hdfs & Queue info are null");
        }
        
 		this.topic = topic;
		this.queryInfo = queryInfo;
		this.hashGroupSize = hashGroupSize;

 		this.consumer = new KafkaConsumer<>(kafkaProperties);
		this.consumer.subscribe(Arrays.asList(topic));
	    	
	}
	
	public void pauseLoading(Boolean pauseLoading) {
        this.pauseLoading = pauseLoading;		
	}

	public void stopListening() {
	     stopConsumer = true;
	     logger.info("Stop consumer listening command received");
	   }
	
	public long recordsLoaded() {
		return processedCounter;
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

		JSONParser jsonParser = new JSONParser();
		long recordCounter = 0;
        long selectorNullCount = 0;
        rowIndexCounter = new HashMap<Integer,Integer>();
        rowIndexOverflowCounter = new HashMap<Integer,Integer>();

        while (!stopConsumer) {
        	ConsumerRecords<String, String> records = consumer.poll(100);
        	for (ConsumerRecord<String, String> record : records) {
        		//								logger.info("Receive message: " + record.value() + ", Partition: "
        		//										+ record.partition() + ", Offset: " + record.offset() + ", by ThreadID: "
        		//										+ Thread.currentThread().getId());

        		JSONObject jsonData = null;
        		String selector = null;
        		int whichQueue = 0;
        		try {
        			jsonData = (JSONObject) jsonParser.parse(record.value());
        			//					  logger.info("jsonData = " + jsonData.toJSONString());
        		} catch (Exception e) {
        			logger.error("Exception JSON parsing record {}", record.value());
        		}
        		if (jsonData != null) {
        			selector = QueryUtils.getSelectorByQueryTypeJSON(queryInfo.getQuerySchema(), jsonData);
        			if (selector != null && selector.length() > 0) {
        				try {
        					if (hdfsOutput) {
        						outputStream.writeBytes( record.value() + "\n");
        					} else {
        						int rowIndex = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selector);
        						whichQueue = rowIndex / hashGroupSize;
        						if ( rowIndexCounter.containsKey(rowIndex) ) {
        							rowIndexCounter.put(rowIndex, (rowIndexCounter.get(rowIndex) + 1));
        						} else {
        							rowIndexCounter.put(rowIndex, 1);
        						}
        						if ( rowIndexCounter.get(rowIndex) <= maxHitsPerSelector) {
        							List<Byte> parts = QueryUtils.partitionDataElement(queryInfo.getQuerySchema(), jsonData, queryInfo.getEmbedSelector());
        							QueueRecord qr = new QueueRecord(rowIndex, selector, parts);
        							//                                    logger.debug("Adding Queue Record: {}",qr.toString());
        							inputQueues.get(whichQueue).add(qr);
        							processedCounter++;			  
        						} else {
        							if ( rowIndexOverflowCounter.containsKey(rowIndex) ) {
        								rowIndexOverflowCounter.put(rowIndex, (rowIndexOverflowCounter.get(rowIndex) + 1));
        							} else {
        								rowIndexOverflowCounter.put(rowIndex, 1);
        							}
        						}

        					}
        				} catch (Exception e) {
        					logger.error("Exception parsing Record {}", e.getMessage());
        				}
        			} else {
        				selectorNullCount++;
        			}
        		} else {
        			logger.error("Error JSON parsing line {}", record.value());
        		}
        		recordCounter++;
        	}
        	//			logger.info("Read {} new records from Kafka in consumer thread {}", processedCounter, Thread.currentThread().getId());
        	//			processedCounter = 0;
        	while (pauseLoading && !stopConsumer) {
        		try {
        			logger.info("Kafka Consumer Thread {} has been paused", Thread.currentThread().getId());
        			Thread.sleep(Timer.ONE_SECOND);
        		} catch (InterruptedException e) {
        			logger.error("Interrupt Exception waiting for pause {}", e.getMessage());
        		}
        	}
        }
        logger.info("Consumed {} records from Kafka Topic {} in thread {}",recordCounter, topic, Thread.currentThread().getId());	
        long notProcessed = recordCounter - processedCounter;
        if (notProcessed > 0) {
        	logger.warn("{} Records not processed.  {} had no selector the rest failed processing in Thread {}", 
        			notProcessed, selectorNullCount, Thread.currentThread().getId());
        }
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