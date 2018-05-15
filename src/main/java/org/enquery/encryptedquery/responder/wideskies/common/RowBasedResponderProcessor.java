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
package org.enquery.encryptedquery.responder.wideskies.common;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RowBasedResponderProcessor implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(RowBasedResponderProcessor.class);
	private boolean stopProcessing = false;
	private boolean isRunning = true;
	private final Query query;
	private QueryInfo queryInfo = null;
	private QuerySchema qSchema = null;
	private int dataPartitionBitSize = 8;
	private Response response = null;
	private long threadId = 0; 
	private long recordCount = 0;

	private TreeMap<Integer,BigInteger> columns = null; // the column values for the encrypted query calculations
	private ArrayList<Integer> rowColumnCounters; // keeps track of how many hit partitions have been recorded for each row/selector

	private ConcurrentLinkedQueue<QueueRecord> inputQueue;
	private ConcurrentLinkedQueue<Response> responseQueue;

	public RowBasedResponderProcessor(ConcurrentLinkedQueue<QueueRecord> inputQueue, ConcurrentLinkedQueue<Response> responseQueue,
			Query queryInput) {

		logger.debug("Initializing Responder Processing Thread");
		this.query = queryInput;
		this.inputQueue = inputQueue;
		this.responseQueue = responseQueue;

		queryInfo = query.getQueryInfo();
		String queryType = queryInfo.getQueryType();
		dataPartitionBitSize = queryInfo.getDataPartitionBitSize();

		if (SystemConfiguration.getBooleanProperty("pir.allowAdHocQuerySchemas", false))
		{
			qSchema = queryInfo.getQuerySchema();
		}
		if (qSchema == null)
		{
			qSchema = QuerySchemaRegistry.get(queryType);
		}

		resetResponse();

	}

	public void stopProcessing() {
		stopProcessing = true;
		logger.debug("Stop responder processing command received for thread {}", threadId);
	}

	public boolean isRunning() {
		return isRunning;
	}

	public long getRecordsProcessed() {
		return recordCount;
	}
	
	@Override
	public void run() {

		threadId = Thread.currentThread().getId();
		logger.debug("Starting Responder Processing Thread {}", threadId);
		QueueRecord nextRecord = null;

		while (!stopProcessing) {
			while ((nextRecord = inputQueue.poll()) != null) {
//				logger.info("Retrieved rowIndex: {} selector: {}", nextRecord.getRowIndex(), nextRecord.getSelector());

						try {
							//logger.info("Processing selector {}", selector);
							addDataElement(nextRecord);
							recordCount++;
							//if ( (recordCount % 1000) == 0) {
							//	logger.info("Processed {} records so far in Queue Processing Thread {}", recordCount, threadId);
							//}
						} catch (Exception e) {
							logger.error("Exception processing record {} Exception: {}", nextRecord, e.getMessage());
						}
				}
			}

		//Process Response
		setResponseElements();
		responseQueue.add(response);
		logger.debug("Processed {} total records in Thread {}", recordCount, threadId);
		logger.debug("Added to responseQueue size ( {} ) from Thread {}", responseQueue.size(), threadId);
		isRunning = false;

	}

	/**
	 * Method to add a data element associated with the given selector to the Response
	 * <p>
	 * Assumes that the dataMap contains the data in the schema specified
	 * <p>
	 * Initialize Paillier ciphertext values Y_i to 1 (as needed -- column values as the # of hits grows)
	 * <p>
	 * Initialize 2^hashBitSize counters: c_t = 0, 0 <= t <= (2^hashBitSize - 1)
	 * <p>
	 * For selector T:
	 * <p>
	 * For data element D, split D into partitions of size partitionSize-many bits:
	 * <p>
	 * D = D_0 || ... ||D_{\ceil{bitLength(D)/partitionSize} - 1)}
	 * <p>
	 * Compute H_k(T); let E_T = query.getQueryElement(H_k(T)).
	 * <p>
	 * For each data partition D_i:
	 * <p>
	 * Compute/Update:
	 * <p>
	 * Y_{i+c_{H_k(T)}} = (Y_{i+c_{H_k(T)}} * ((E_T)^{D_i} mod N^2)) mod N^2 ++c_{H_k(T)}
	 * 
	 */
	public void addDataElement(QueueRecord qr) throws Exception
	{
		List<BigInteger> inputData = qr.getParts();
		List<BigInteger> hitValPartitions = QueryUtils.createPartitions(inputData, dataPartitionBitSize);

//		int index = 1;
//		for (BigInteger bi : hitValPartitions) {
//			logger.debug("Part {} BigInt {} / Byte {}", index, bi.toString(), bi.toString(16) );
//			index++;
//		}

		int rowIndex = qr.getRowIndex();
		int rowCounter = rowColumnCounters.get(rowIndex);
		BigInteger rowQuery = query.getQueryElement(rowIndex);

		//	    logger.debug("hitValPartitions.size() = " + hitValPartitions.size() + " rowIndex = " + rowIndex + " rowCounter = " + rowCounter + " rowQuery = "
		//	        + rowQuery.toString() + " pirWLQuery.getNSquared() = " + query.getNSquared().toString());

		// Update the associated column values
		for (int i = 0; i < hitValPartitions.size(); ++i)
		{
			if (!columns.containsKey(i + rowCounter))
			{
				columns.put(i + rowCounter, BigInteger.valueOf(1));
			}
			BigInteger column = columns.get(i + rowCounter); // the next 'free' column relative to the selector
			//	      logger.debug("Before: columns.get(" + (i + rowCounter) + ") = " + columns.get(i + rowCounter));

			BigInteger exp;
			if (query.getQueryInfo().useExpLookupTable() && !query.getQueryInfo().useHDFSExpLookupTable()) // using the standalone
				// lookup table
			{
				exp = query.getExp(rowQuery, hitValPartitions.get(i).intValue());
			}
			else
				// without lookup table
			{
				//	        logger.debug("i = " + i + " hitValPartitions.get(i).intValue() = " + hitValPartitions.get(i).intValue());
				exp = ModPowAbstraction.modPow(rowQuery, hitValPartitions.get(i), query.getNSquared());
			}
			column = (column.multiply(exp)).mod(query.getNSquared());

			columns.put(i + rowCounter, column);

			//	      logger.debug(
			//	          "exp = " + exp + " i = " + i + " partition = " + hitValPartitions.get(i) + " = " + hitValPartitions.get(i).toString(2) + " column = " + column);
			//	      logger.debug("After: columns.get(" + (i + rowCounter) + ") = " + columns.get(i + rowCounter));
		}

		// Update the rowCounter (next free column position) for the selector
		rowColumnCounters.set(rowIndex, (rowCounter + hitValPartitions.size()));
		//	    logger.debug("rowIndex {} next column is {}", rowIndex, rowColumnCounters.get(rowIndex));
	}


	/**
	 * Reset The response for the next iteration
	 */
	private void resetResponse() {

		response = new Response(queryInfo);

		// Columns are allocated as needed, initialized to 1
		columns = new TreeMap<>();

		// Initialize row counters
		rowColumnCounters = new ArrayList<>();
		for (int i = 0; i < Math.pow(2, queryInfo.getHashBitSize()); ++i)
		{
			rowColumnCounters.add(0);
		}
	}

	public void setResponseElements()
	{
		// logger.debug("numResponseElements = " + columns.size());
		// for(int key: columns.keySet())
		// {
		//      logger.debug("key = " + key + " column = " + columns.get(key));
		// }
		logger.debug("There are {} columns in the response from QPT {}", columns.size(), Thread.currentThread().getId());
		response.addResponseElements(columns);
	}
}
