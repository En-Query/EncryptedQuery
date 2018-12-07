/*
 * EncryptedQuery is an open source project allowing user to query databases with queries under
 * homomorphic encryption to securing the query and results set from database owner inspection.
 * Copyright (C) 2018 EnQuery LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package org.enquery.encryptedquery.responder.wideskies.common;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.utils.ConversionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will process data from queue one row at a time. This is memory efficient, but
 * performance poor.
 *
 */
public class RowBasedResponderProcessor implements Runnable, ResponderProperties {

	private static final Logger logger = LoggerFactory.getLogger(RowBasedResponderProcessor.class);
	private boolean stopProcessing = false;
	private boolean isRunning = true;
	private final Query query;
	private QueryInfo queryInfo = null;
	private int dataChunkSize = 1;
	private Response response = null;
	private long threadId = 0;
	private long recordCount = 0;
	private Partitioner partitioner;
	private ModPowAbstraction modPowAbstraction;

	// the column values for the encrypted query calculations
	private TreeMap<Integer, BigInteger> columns = null;
	// keeps track of how many hit partitions have been recorded for each row/selector
	private ArrayList<Integer> rowColumnCounters;
	private ConcurrentLinkedQueue<QueueRecord> inputQueue;
	private ConcurrentLinkedQueue<Response> responseQueue;

	public RowBasedResponderProcessor(ModPowAbstraction modPowAbstraction,
			ConcurrentLinkedQueue<QueueRecord> inputQueue,
			ConcurrentLinkedQueue<Response> responseQueue,
			Query queryInput,
			Map<String, String> config) {

		Validate.notNull(modPowAbstraction);
		Validate.notNull(inputQueue);
		Validate.notNull(responseQueue);
		Validate.notNull(queryInput);
		Validate.notNull(config);


		logger.debug("Initializing Responder Processing Thread");
		this.modPowAbstraction = modPowAbstraction;
		this.query = queryInput;
		this.inputQueue = inputQueue;
		this.responseQueue = responseQueue;

		queryInfo = query.getQueryInfo();
		dataChunkSize = queryInfo.getDataChunkSize();

		partitioner = new Partitioner();

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
		logger.info("Starting Responder Processing Thread {}", threadId);
		QueueRecord nextRecord = null;

		while (!stopProcessing) {
			while ((nextRecord = inputQueue.poll()) != null) {
				try {
					addDataElement(nextRecord);
					recordCount++;
				} catch (Exception e) {
					throw new RuntimeException("Exception processing record:" + nextRecord, e);
				}
			}
		}

		// Process Response
		setResponseElements();
		responseQueue.add(response);
		logger.info("Thread {} processed {} records", threadId, recordCount);
		isRunning = false;

	}

	/**
	 * Method to add a data element associated with the given selector to the Response Assumes that
	 * the dataMap contains the data in the schema specified Initialize Paillier ciphertext values
	 * Y_i to 1 (as needed -- column values as the # of hits grows) Initialize 2^hashBitSize
	 * counters: c_t = 0, 0 <= t <= (2^hashBitSize - 1) For selector T: For data element D, split D
	 * into partitions of size partitionSize-many bits: D = D_0 || ...
	 * ||D_{\ceil{bitLength(D)/partitionSize} - 1)} Compute H_k(T); let E_T =
	 * query.getQueryElement(H_k(T)). For each data partition D_i: Compute/Update: Y_{i+c_{H_k(T)}}
	 * = (Y_{i+c_{H_k(T)}} * ((E_T)^{D_i} mod N^2)) mod N^2 ++c_{H_k(T)}
	 * 
	 */
	public void addDataElement(QueueRecord qr) throws Exception {
		List<Byte> inputData = qr.getParts();
		List<Byte[]> hitValPartitions = partitioner.createPartitions(inputData, dataChunkSize);

		int rowIndex = qr.getRowIndex();
		int rowCounter = rowColumnCounters.get(rowIndex);
		BigInteger rowQuery = query.getQueryElement(rowIndex);

		for (int i = 0; i < hitValPartitions.size(); ++i) {
			if (!columns.containsKey(i + rowCounter)) {
				columns.put(i + rowCounter, BigInteger.valueOf(1));
			}
			BigInteger column = columns.get(i + rowCounter); // the next 'free' column relative to
																// the selector
			BigInteger exp;
			BigInteger BI = new BigInteger(1, ConversionUtils.toPrimitives(hitValPartitions.get(i)));

			if (query.getQueryInfo().useExpLookupTable() && !query.getQueryInfo().useHDFSExpLookupTable()) // using
																											// the
																											// standalone
			// lookup table
			{
				exp = query.getExp(rowQuery, BI.intValue());
			} else
			// without lookup table
			{
				// logger.debug("i = " + i + " hitValPartitions.get(i).intValue() = " +
				// hitValPartitions.get(i).intValue());
				exp = modPowAbstraction.modPow(rowQuery, BI, query.getNSquared());
			}
			column = column.multiply(exp).mod(query.getNSquared());

			columns.put(i + rowCounter, column);
		}

		// Update the rowCounter (next free column position) for the selector
		rowColumnCounters.set(rowIndex, rowCounter + hitValPartitions.size());
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
		for (int i = 0; i < Math.pow(2, queryInfo.getHashBitSize()); ++i) {
			rowColumnCounters.add(0);
		}
	}

	public void setResponseElements() {
		logger.debug("There are {} columns in the response from QPT {}", columns.size(), Thread.currentThread().getId());
		response.addResponseElements(columns);
	}
}
