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
 */
package org.enquery.encryptedquery.responder.wideskies.common;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.utils.JavaUtilities;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ColumnBasedResponderProcessor implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(ColumnBasedResponderProcessor.class);
	private DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");
	
	private boolean stopProcessing = false;
	private boolean isRunning = true;
	private final Query query;
	private QueryInfo queryInfo = null;
	private QuerySchema qSchema = null;
	private int dataPartitionBitSize = 8;
	private Response response = null;
	private long threadId = 0; 
	private long recordCount = 0;
	private BigInteger NSquared = null;
	private int hashBitSize = 0;
	private String encryptColumnMethod = null;
	private ComputeEncryptedColumn cec = null;
	private long computeThreshold = 0;

	// dataColumns[column number][row index] = data part (i.e. exponent)
	private HashMap<Integer, BigInteger[]> dataColumns;

	private long dataStart, dataTime = 0;
	private long mathStart, mathTime = 0;
	private long respStart, respTime = 0;
	private long arrayStart, arrayTime = 0;
	private long adeStart, adeTime = 0;

	private TreeMap<Integer,BigInteger> columns = null; // the column values for the encrypted query calculations

	private ArrayList<Integer> rowColumnCounters; // keeps track of how many hit partitions have been recorded for each row/selector

	private ConcurrentLinkedQueue<QueueRecord> inputQueue;
	private ConcurrentLinkedQueue<Response> responseQueue;

	public ColumnBasedResponderProcessor(ConcurrentLinkedQueue<QueueRecord> inputQueue, ConcurrentLinkedQueue<Response> responseQueue,
			Query queryInput) {

		logger.debug("Initializing Responder Processing Thread");
		this.query = queryInput;
		this.inputQueue = inputQueue;
		this.responseQueue = responseQueue;

		this.NSquared = query.getNSquared();
		hashBitSize = query.getQueryInfo().getHashBitSize();
		queryInfo = query.getQueryInfo();
		String queryType = queryInfo.getQueryType();
		dataPartitionBitSize = queryInfo.getDataPartitionBitSize();
		computeThreshold = SystemConfiguration.getLongProperty("responder.computeThreshold", 100000);

		if (SystemConfiguration.getBooleanProperty("pir.allowAdHocQuerySchemas", false))
		{
			qSchema = queryInfo.getQuerySchema();
		}
		if (qSchema == null)
		{
			qSchema = QuerySchemaRegistry.get(queryType);
		}

		resetResponse();

		dataColumns = new HashMap<Integer, BigInteger[]>();

		encryptColumnMethod = SystemConfiguration.getProperty("responder.encryptColumnMethod");
		cec = ComputeEncryptedColumnFactory.getComputeEncryptedColumnMethod(encryptColumnMethod, query.getQueryElements(), NSquared, (1<<hashBitSize), dataPartitionBitSize);
	}

	public void stopProcessing() {
		stopProcessing = true;
		logger.debug("Stop responder processing command received for thread {}", threadId);
	}

	public boolean isRunning() {
		return isRunning;
	}

	public long getRecordsProcessed() {
		//		logger.info("{} Records processed in thread {}", recordCount, Thread.currentThread().getId());
		return recordCount;
	}

	@Override
	public void run() {

		threadId = Thread.currentThread().getId();
		logger.info("Starting Responder Processing Thread {} with encryptColumnMethod {}", threadId, encryptColumnMethod);
		QueueRecord nextRecord = null;
		while (!stopProcessing) {
			while ((nextRecord = inputQueue.poll()) != null) {
				try {
					//logger.info("Processing selector {}", selector);
					addDataElement(nextRecord);
					recordCount++;
					if ( (recordCount % computeThreshold) == 0) {
						logger.info("Thread {} Retrieved {} records so far will pause queue to compute", threadId, recordCount);
						processColumns();
						logger.info("Thread {} compute finished, resuming queue input", threadId);
					}
				} catch (Exception e) {
					logger.error("Exception processing record {} in Queue Processing Thread {}; Exception: {}", nextRecord, threadId, e.getMessage());
				}
			}
		}

        // Process remaining data in dataColumns array
		computeEncryptedColumns();

		setResponseElements();
		responseQueue.add(response);
		logger.info("Thread {} processed {} records", threadId, recordCount);
		isRunning = false;

		cec.free();
	}

	/**
	 * Method to add a data element associated with the given selector to the Response
	 * Assumes that the dataMap contains the data in the schema specified
	 * Initialize Paillier ciphertext values Y_i to 1 (as needed -- column values as the # of hits grows)
	 * Initialize 2^hashBitSize counters: c_t = 0, 0 <= t <= (2^hashBitSize - 1)
	 * For selector T:
	 * For data element D, split D into partitions of size partitionSize-many bits:
	 * D = D_0 || ... ||D_{\ceil{bitLength(D)/partitionSize} - 1)}
	 * Compute H_k(T); let E_T = query.getQueryElement(H_k(T)).
	 * For each data partition D_i:
	 * Compute/Update:
	 * Y_{i+c_{H_k(T)}} = (Y_{i+c_{H_k(T)}} * ((E_T)^{D_i} mod N^2)) mod N^2 ++c_{H_k(T)}
	 * 
	 */
	public void addDataElement(QueueRecord qr) throws Exception
	{
		// Extract the data from the input record into byte chunks.
		List<BigInteger> inputData = qr.getParts();

		// Convert from byte data into partitions of data partition size.
		List<BigInteger> hitValPartitions = QueryUtils.createPartitions(inputData, dataPartitionBitSize);

		int rowIndex = qr.getRowIndex();
		int rowCounter = rowColumnCounters.get(rowIndex);

		for (int i = 0; i < hitValPartitions.size(); ++i)
		{
			if (!dataColumns.containsKey(i + rowCounter))
			{
				dataColumns.put(i + rowCounter, new BigInteger[1 << queryInfo.getHashBitSize()]);
			}
			dataColumns.get(i + rowCounter)[rowIndex] = hitValPartitions.get(i);
		}

		// Update the rowCounter (next free column position) for the selector
		rowColumnCounters.set(rowIndex, rowCounter + hitValPartitions.size());
	}

	/**
	 * Since we do not have an endless supply of memory to process unlimited data
	 * process the data in chunks.
	 */
    private void processColumns() {
    	computeEncryptedColumns();
        dataColumns.clear();
    }
    
    
    public void computeEncryptedColumns()
    {
    	for(Map.Entry<Integer, BigInteger[]> entry : dataColumns.entrySet()) {
    		int col = entry.getKey();
    		BigInteger[] dataCol = entry.getValue();
    		for (int rowIndex=0; rowIndex < dataCol.length; ++rowIndex)
    		{
    			if (null != dataCol[rowIndex])
    			{
    				cec.insertDataPart(rowIndex, dataCol[rowIndex]);
    			}
    		}
    		BigInteger newColumn = cec.computeColumnAndClearData();
    		if (!columns.containsKey(col))
    		{
    			columns.put(col, BigInteger.valueOf(1));
    		}
    		BigInteger column = columns.get(col);
    		column = (column.multiply(newColumn)).mod(query.getNSquared());
    		columns.put(col, column);
    	}
    }

	/**
	 * Reset The response for the next iteration
	 */
	private void resetResponse() {

		response = new Response(queryInfo);
		columns = new TreeMap<>();

		// Initialize row counters
		rowColumnCounters = new ArrayList<>();
		for (int i = 0; i < (1 << queryInfo.getHashBitSize()); ++i)
		{
			rowColumnCounters.add(0);
		}
	}

	public void setResponseElements()
	{
		response.addResponseElements(columns);
	}

}