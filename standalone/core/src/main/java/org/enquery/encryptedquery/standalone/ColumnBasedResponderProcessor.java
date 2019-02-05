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
package org.enquery.encryptedquery.standalone;

import java.security.PublicKey;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.responder.QueueRecord;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ColumnBasedResponderProcessor implements Callable<Response> {

	private static final Logger logger = LoggerFactory.getLogger(ColumnBasedResponderProcessor.class);
	private final DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	private final Query query;
	private QueryInfo queryInfo;
	private int dataChunkSize;
	private Response response;
	private long threadId;
	private long recordCount;
	private long computeThreshold;
	private Partitioner partitioner;
	private HashMap<Integer, List<Pair<Integer, byte[]>>> dataColumns;
	// the column values for the encrypted query calculations
	private TreeMap<Integer, CipherText> columns = null;
	// keeps track of column location for each rowIndex (Selector Hash)
	private int[] rowColumnCounters;
	private ArrayBlockingQueue<QueueRecord> inputQueue;
	private ConcurrentLinkedQueue<Response> responseQueue;
	private CryptoScheme crypto;
	private ColumnProcessor columnProcessor;

	public ColumnBasedResponderProcessor(ArrayBlockingQueue<QueueRecord> queue,
			ConcurrentLinkedQueue<Response> responseQueue,
			Query query,
			CryptoScheme crypto,
			Map<String, String> config,
			Partitioner partitioner) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

		Validate.notNull(queue);
		Validate.notNull(query);
		Validate.notNull(config);
		Validate.notNull(partitioner);
		Validate.notNull(crypto);

		this.query = query;
		this.inputQueue = queue;
		this.responseQueue = responseQueue;
		this.partitioner = partitioner;
		this.crypto = crypto;

		queryInfo = query.getQueryInfo();
		dataChunkSize = queryInfo.getDataChunkSize();

		String ct = config.get(StandaloneConfigurationProperties.COMPUTE_THRESHOLD);
		Validate.notNull(ct, StandaloneConfigurationProperties.COMPUTE_THRESHOLD + " property missing.");
		computeThreshold = Long.parseLong(ct);
	}

	public long getRecordsProcessed() {
		return recordCount;
	}

	@Override
	public Response call() throws PIRException, InterruptedException {

		threadId = Thread.currentThread().getId();
		logger.info("Starting Responder Processing Thread {}", threadId);

		resetResponse();

		// this Queue blocks until item is available
		QueueRecord nextRecord = null;
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

		response.addResponseElements(columns);
		responseQueue.add(response);

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
		List<byte[]> hitValPartitions = partitioner.createPartitions(qr.getParts(), dataChunkSize);

		// For Debugging Only
		// listPartitions(hitValPartitions);

		int rowIndex = qr.getRowIndex();
		int rowCounter = rowColumnCounters[rowIndex];

		for (int i = 0; i < hitValPartitions.size(); ++i) {
			if (!dataColumns.containsKey(i + rowCounter)) {
				dataColumns.put(i + rowCounter, new ArrayList<Pair<Integer, byte[]>>());
			}
			// logger.info("rowIndex {} Partition {} added to dataColumns index {} Value {}",
			// rowIndex,
			// i, i + rowCounter,
			// ConversionUtils.byteArrayToHexString(ConversionUtils.toPrimitives(hitValPartitions.get(i))));
			dataColumns.get(i + rowCounter).add(Pair.of(rowIndex, hitValPartitions.get(i)));
		}

		// Update the rowCounter (next free column position) for the selector
		rowColumnCounters[rowIndex] += hitValPartitions.size();
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
		final PublicKey publicKey = query.getQueryInfo().getPublicKey();

		for (final Map.Entry<Integer, List<Pair<Integer, byte[]>>> entry : dataColumns.entrySet()) {
			final int col = entry.getKey();
			final List<Pair<Integer, byte[]>> dataCol = entry.getValue();
			for (int i = 0; i < dataCol.size(); ++i) {
				if (null != dataCol.get(i)) {
					columnProcessor.insert(dataCol.get(i).getLeft(), dataCol.get(i).getRight());
				}
			}
			final CipherText newValue = columnProcessor.compute();
			CipherText prevValue = columns.get(col);
			if (prevValue == null) {
				prevValue = crypto.encryptionOfZero(publicKey);
			}
			final CipherText sum = crypto.computeCipherAdd(publicKey, prevValue, newValue);
			columns.put(col, sum);
		}
	}

	/**
	 * Reset The response for the next iteration
	 */
	private void resetResponse() {

		dataColumns = new HashMap<>();
		response = new Response(queryInfo);
		columns = new TreeMap<>();
		columnProcessor = crypto.makeColumnProcessor(query.getQueryInfo(), query.getQueryElements());

		// Initialize row counters
		final int size = 1 << queryInfo.getHashBitSize();
		if (rowColumnCounters == null) {
			rowColumnCounters = new int[size];
		} else {
			Arrays.fill(rowColumnCounters, 0);
		}
	}

}
