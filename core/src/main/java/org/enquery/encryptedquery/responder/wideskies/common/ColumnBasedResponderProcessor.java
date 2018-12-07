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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.utils.ConversionUtils;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ColumnBasedResponderProcessor implements Callable<Response> {

	private static final Logger logger = LoggerFactory.getLogger(ColumnBasedResponderProcessor.class);
	private final DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	private final Map<String, String> config;

	private final Query query;
	private QueryInfo queryInfo = null;
	private int dataChunkSize = 8;
	private Response response = null;
	private long threadId = 0;
	private long recordCount = 0;
	private BigInteger NSquared = null;
	private long computeThreshold = 0;
	private Partitioner partitioner;

	private HashMap<Integer, List<Pair<Integer, Byte[]>>> dataColumns;

	private TreeMap<Integer, BigInteger> columns = null; // the column values for the encrypted
															// query calculations

	private ArrayList<Integer> rowColumnCounters; // keeps track of column location for each
													// rowIndex (Selector Hash)

	private ArrayBlockingQueue<QueueRecord> inputQueue;
	private ConcurrentLinkedQueue<Response> responseQueue;

	transient private ComputeEncryptedColumn cec;
	transient private ModPowAbstraction modPowAbstraction;

	public ColumnBasedResponderProcessor(ArrayBlockingQueue<QueueRecord> inputQueue,
			ConcurrentLinkedQueue<Response> responseQueue, Query queryInput, Map<String, String> config, Partitioner partitioner) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

		this.query = queryInput;
		this.inputQueue = inputQueue;
		this.responseQueue = responseQueue;

		this.NSquared = query.getNSquared();
		queryInfo = query.getQueryInfo();
		dataChunkSize = queryInfo.getDataChunkSize();
		this.config = config;

		String ct = config.get(ResponderProperties.COMPUTE_THRESHOLD);
		Validate.notNull(ct, ResponderProperties.COMPUTE_THRESHOLD + " property missing.");
		computeThreshold = Long.parseLong(ct);

		queryInfo.getQuerySchema();

		resetResponse();

		dataColumns = new HashMap<>();

		if (cec == null) {
			logger.debug("cec is null, initializing..");
			initialize();
		}

		this.partitioner = partitioner;

		logger.debug("Initialized Responder Processing Thread using encryptColumnMethod {} with processing threshold {}",
				cec.name(), numFormat.format(computeThreshold));
	}

	@SuppressWarnings("unchecked")
	private void initialize() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		String cecClassName = config.get(ResponderProperties.COLUMN_ENCRYPTION_CLASS_NAME);
		Validate.notEmpty(cecClassName, "Missing or invalid property " + ResponderProperties.COLUMN_ENCRYPTION_CLASS_NAME);

		logger.debug("Column Encryption Class {}", cecClassName);

		Class<ComputeEncryptedColumn> cecClass = (Class<ComputeEncryptedColumn>) Class.forName(cecClassName);
		cec = cecClass.newInstance();

		String modPowClassName = config.get(ResponderProperties.MOD_POW_CLASS_NAME);
		Validate.notEmpty(modPowClassName, "Missing or invalid property " + ResponderProperties.MOD_POW_CLASS_NAME);

		logger.debug("Mod Power Class {}", modPowClassName);

		Class<ModPowAbstraction> modPowClass = (Class<ModPowAbstraction>) Class.forName(modPowClassName);
		modPowAbstraction = modPowClass.newInstance();

		cec.initialize(query.getQueryElements(), NSquared, modPowAbstraction, config);
	}

	public long getRecordsProcessed() {
		return recordCount;
	}

	@Override
	public Response call() throws PIRException, InterruptedException {

		threadId = Thread.currentThread().getId();
		logger.info("Starting Responder Processing Thread {}", threadId);
		QueueRecord nextRecord = null;

		// blocks until available
		while ((nextRecord = inputQueue.take()) != null) {

			// magic marker, input is exhausted
			if (nextRecord.isEndOfFile()) break;

			try {
				addDataElement(nextRecord);
				recordCount++;
				if ((recordCount % computeThreshold) == 0) {
					logger.info("Thread {} Retrieved {} records so far will pause queue to compute", threadId, numFormat.format(recordCount));
					processColumns();
					logger.info("Thread {} compute finished, resuming queue input", threadId);
				}
			} catch (Exception e) {
				throw new RuntimeException(
						String.format(
								"Exception processing record %s in Queue Processing Thread %s",
								nextRecord,
								threadId),
						e);
			}
		}

		// Process remaining data in dataColumns array
		computeEncryptedColumns();

		setResponseElements();
		responseQueue.add(response);
		cec.free();

		logger.info("Thread {} processed {} records", threadId, recordCount);

		return response;
	}

	/**
	 * Method to add a data record associated with the given selector to the Response Assumes that
	 * the query record contains the data in the schema specified Initialize Paillier ciphertext
	 * values Y_i to 1 (as needed -- column values as the # of hits grows) Initialize 2^hashBitSize
	 * counters: c_t = 0, 0 <= t <= (2^hashBitSize - 1) For selector T: For data element D, split D
	 * into partitions of size partitionSize-many bits: D = D_0 || ...
	 * ||D_{\ceil{bitLength(D)/partitionSize} - 1)} Compute H_k(T); let E_T =
	 * query.getQueryElement(H_k(T)). For each data partition D_i: Compute/Update: Y_{i+c_{H_k(T)}}
	 * = (Y_{i+c_{H_k(T)}} * ((E_T)^{D_i} mod N^2)) mod N^2 ++c_{H_k(T)}
	 * 
	 */
	public void addDataElement(QueueRecord qr) throws Exception {
		// Convert from byte data into partitions of data partition size.
		List<Byte[]> hitValPartitions = partitioner.createPartitions(qr.getParts(), dataChunkSize);

		// For Debugging Only
		// listPartitions(hitValPartitions);

		int rowIndex = qr.getRowIndex();
		int rowCounter = rowColumnCounters.get(rowIndex);

		for (int i = 0; i < hitValPartitions.size(); ++i) {
			if (!dataColumns.containsKey(i + rowCounter)) {
				dataColumns.put(i + rowCounter, new ArrayList<Pair<Integer, Byte[]>>());
			}
			// logger.info("rowIndex {} Partition {} added to dataColumns index {} Value {}",
			// rowIndex,
			// i, i + rowCounter,
			// ConversionUtils.byteArrayToHexString(ConversionUtils.toPrimitives(hitValPartitions.get(i))));
			dataColumns.get(i + rowCounter).add(Pair.of(rowIndex, hitValPartitions.get(i)));
		}

		// Update the rowCounter (next free column position) for the selector
		rowColumnCounters.set(rowIndex, rowCounter + hitValPartitions.size());
	}

	/**
	 * Since we do not have an endless supply of memory to process unlimited data process the data
	 * in chunks.
	 */
	private void processColumns() {
		computeEncryptedColumns();
		dataColumns.clear();
	}

	/*
	 * This method adds all the entries ready for computation into the library and computes to a
	 * single value. That value is then stored to be computed with the next batch of values.
	 */
	public void computeEncryptedColumns() {
		for (Map.Entry<Integer, List<Pair<Integer, Byte[]>>> entry : dataColumns.entrySet()) {
			int col = entry.getKey();
			List<Pair<Integer, Byte[]>> dataCol = entry.getValue();
			for (int i = 0; i < dataCol.size(); ++i) {
				if (null != dataCol.get(i)) {
					BigInteger BI = new BigInteger(1, ConversionUtils.toPrimitives(dataCol.get(i).getRight()));
					cec.insertDataPart(dataCol.get(i).getLeft(), BI);
				}
			}
			BigInteger newColumn = cec.computeColumnAndClearData();
			if (!columns.containsKey(col)) {
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
		for (int i = 0; i < (1 << queryInfo.getHashBitSize()); ++i) {
			rowColumnCounters.add(0);
		}
	}

	public void setResponseElements() {
		response.addResponseElements(columns);
	}

}
