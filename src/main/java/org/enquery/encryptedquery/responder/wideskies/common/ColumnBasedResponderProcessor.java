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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.commons.lang3.tuple.Pair;

import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.utils.ConversionUtils;
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

	private HashMap<Integer, List<Pair<Integer, Byte[]>>> dataColumns;

	private TreeMap<Integer,BigInteger> columns = null; // the column values for the encrypted query calculations

	private ArrayList<Integer> rowColumnCounters; // keeps track of column location for each rowIndex (Selector Hash)

	private ConcurrentLinkedQueue<QueueRecord> inputQueue;
	private ConcurrentLinkedQueue<Response> responseQueue;

	public ColumnBasedResponderProcessor(ConcurrentLinkedQueue<QueueRecord> inputQueue, ConcurrentLinkedQueue<Response> responseQueue,
			Query queryInput) {

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

		dataColumns = new HashMap<Integer, List<Pair<Integer, Byte[]>>>();

		encryptColumnMethod = SystemConfiguration.getProperty("responder.encryptColumnMethod");
		cec = ComputeEncryptedColumnFactory.getComputeEncryptedColumnMethod(encryptColumnMethod, query.getQueryElements(), NSquared, (1<<hashBitSize), dataPartitionBitSize);
		logger.info("Initialized Responder Processing Thread using encryptColumnMethod {} with processing threshold {}", 
				 encryptColumnMethod, numFormat.format(computeThreshold));
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
		logger.info("Starting Responder Processing Thread {}", threadId);
		QueueRecord nextRecord = null;
		while (!stopProcessing) {
			while ((nextRecord = inputQueue.poll()) != null) {
				try {
					addDataElement(nextRecord);
					recordCount++;
					if ( (recordCount % computeThreshold) == 0) {
						logger.info("Thread {} Retrieved {} records so far will pause queue to compute", threadId, numFormat.format(recordCount));
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
	 * Method to add a data record associated with the given selector to the Response
	 * Assumes that the query record contains the data in the schema specified
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
		// Convert from byte data into partitions of data partition size.
		List<Byte[]> hitValPartitions = QueryUtils.createPartitions(qr.getParts(), dataPartitionBitSize);
//        logger.info("rowIndex {} Part count: {}", qr.getRowIndex(), qr.getParts().size());
        
		int rowIndex = qr.getRowIndex();
		int rowCounter = rowColumnCounters.get(rowIndex);

		for (int i = 0; i < hitValPartitions.size(); ++i)
		{
			if (!dataColumns.containsKey(i + rowCounter))
			{
				dataColumns.put(i + rowCounter, new ArrayList<Pair<Integer, Byte[]>>());
			}
 //       	logger.info("rowIndex {} Partition {} added to dataColumns index {} Value {}", rowIndex,
 //      			i, i + rowCounter, ConversionUtils.byteArrayToHexString(ConversionUtils.toPrimitives(hitValPartitions.get(i))));
			dataColumns.get(i + rowCounter).add(Pair.of(rowIndex, hitValPartitions.get(i)));
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
    
    /*
     * This method adds all the entries ready for computation into the library and 
     * computes to a single value.  That value is then stored to be computed with the next 
     * batch of values.
     */
    
    public void computeEncryptedColumns()
    {
    	for(Map.Entry<Integer, List<Pair<Integer, Byte[]>>> entry : dataColumns.entrySet()) {
    		int col = entry.getKey();   
    		List<Pair<Integer, Byte[]>> dataCol = entry.getValue();
    		for (int i=0; i < dataCol.size(); ++i)
    		{
    			if (null != dataCol.get(i))
    			{
    				BigInteger BI = new BigInteger(1, ConversionUtils.toPrimitives(dataCol.get(i).getRight()));
    				cec.insertDataPart(dataCol.get(i).getLeft(), BI);
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