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
package org.enquery.encryptedquery.flink.batch;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.flink.api.common.functions.RichGroupReduceFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.flink.QueueRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataPartitionsReduceFunction extends RichGroupReduceFunction<QueueRecord, Tuple2<Integer, CipherText>> {

	private static final long serialVersionUID = -2249924018671569475L;
	private static final Logger log = LoggerFactory.getLogger(DataPartitionsReduceFunction.class);
	private final DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	private final Map<String, String> cryptoSchemeConfig;
	private final long computeThreshold;
	private final Query query;
	private UUID processId;

	// non serializable
	private transient CryptoScheme crypto;
	private transient ColumnProcessor columnProcessor;
	// private transient boolean initialized = false;
	// keeps track of column location for each rowIndex (Selector Hash)
	private transient int[] rowColumnCounters;
	private transient HashMap<Integer, List<Pair<Integer, byte[]>>> dataColumns;
	// the column values for the encrypted query calculations
	private transient HashMap<Integer, CipherText> columns;
	private transient byte[] queryHandle;

	public DataPartitionsReduceFunction(Query query, long computeThreshold, Map<String, String> cryptoSchemeConfig) {
		Validate.notNull(query);
		Validate.notNull(cryptoSchemeConfig);
		this.query = query;
		this.computeThreshold = computeThreshold;
		this.cryptoSchemeConfig = cryptoSchemeConfig;
	}

	@Override
	public void open(Configuration parameters) throws Exception {

		processId = UUID.randomUUID();
		crypto = CryptoSchemeFactory.make(cryptoSchemeConfig);

		queryHandle = crypto.loadQuery(query.getQueryInfo(), query.getQueryElements());
		columnProcessor = crypto.makeColumnProcessor(queryHandle);
	}

	@Override
	public void close() throws Exception {
		columnProcessor = null;
		if (crypto != null) {
			if (queryHandle != null) {
				crypto.unloadQuery(queryHandle);
				queryHandle = null;
			}
			crypto.close();
			crypto = null;
		}
		rowColumnCounters = null;
		columns = null;
		dataColumns = null;
	}

	@Override
	public void reduce(Iterable<QueueRecord> values, Collector<Tuple2<Integer, CipherText>> out) throws Exception {

		log.info("Processing data with computeThreshold {}", computeThreshold);

		columns = new HashMap<>();
		dataColumns = new HashMap<>();
		if (rowColumnCounters == null) {
			rowColumnCounters = new int[1 << query.getQueryInfo().getHashBitSize()];
		} else {
			Arrays.fill(rowColumnCounters, 0);
		}

		int recordCount = 0;
		for (QueueRecord entry : values) {
			addDataElement(entry);
			recordCount++;
			if (recordCount % computeThreshold == 0) {
				log.info("Process {} Compute threshold {} reached, will pause to encrypt/reduce value.  ", processId, numFormat.format(computeThreshold));
				processColumns();
				log.info("Process {} Compute finished, resuming data processing. Total records processed so far {}", processId, numFormat.format(recordCount));
			}
		}

		// Process remaining data in dataColumns array
		processColumns();

		columns.forEach((k, v) -> out.collect(Tuple2.of(k, v)));

		log.info("Process {} Processed {} records", processId, recordCount);
		columns.clear();
	}

	private void addDataElement(QueueRecord qr) throws Exception {
		final List<byte[]> hitValPartitions = qr.getHitValPartitions();
		final int rowIndex = qr.getRowIndex();
		final int rowCounter = rowColumnCounters[rowIndex];

		for (int i = 0; i < hitValPartitions.size(); ++i) {
			final int key = i + rowCounter;

			List<Pair<Integer, byte[]>> list = dataColumns.get(key);
			if (list == null) {
				list = new ArrayList<>();
				dataColumns.put(key, list);
			}
			list.add(Pair.of(rowIndex, hitValPartitions.get(i)));
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


	/**
	 * This method adds all the entries ready for computation into the library and computes to a
	 * single value. That value is then stored to be computed with the next batch of values.
	 */
	private void computeEncryptedColumns() {
		for (Map.Entry<Integer, List<Pair<Integer, byte[]>>> entry : dataColumns.entrySet()) {
			int col = entry.getKey();
			List<Pair<Integer, byte[]>> dataCol = entry.getValue();
			for (int i = 0; i < dataCol.size(); ++i) {
				if (null != dataCol.get(i)) {
					columnProcessor.insert(dataCol.get(i).getLeft(), dataCol.get(i).getRight());
				}
			}
			CipherText newValue = columnProcessor.computeAndClear();
			CipherText prevValue = columns.get(col);
			if (prevValue != null) {
				newValue = crypto.computeCipherAdd(//
						query.getQueryInfo().getPublicKey(),
						prevValue,
						newValue);
			}
			columns.put(col, newValue);
		}
	}
}
