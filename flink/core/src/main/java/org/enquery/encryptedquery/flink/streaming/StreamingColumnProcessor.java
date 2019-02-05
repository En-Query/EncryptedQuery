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
package org.enquery.encryptedquery.flink.streaming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.flink.TimestampFormatter;
import org.enquery.encryptedquery.responder.QueueRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class StreamingColumnProcessor extends ProcessWindowFunction<QueueRecord, WindowPerRowHashResult, Tuple, TimeWindow> {

	private static final long serialVersionUID = -2249924018671569475L;
	private static final Logger log = LoggerFactory.getLogger(StreamingColumnProcessor.class);

	private final Map<String, String> cryptoSchemeConfig;
	private final long computeThreshold;
	private final Query query;

	// non serializable
	// the column values for the encrypted query calculations
	private transient HashMap<Integer, CipherText> columns;
	// keeps track of column location for each rowIndex (Selector Hash)
	// Initialize row counters
	private transient int size;
	private transient int[] rowColumnCounters;
	HashMap<Integer, List<Pair<Integer, byte[]>>> dataColumns;
	private transient CryptoScheme crypto;
	private transient ColumnProcessor cec;
	private transient boolean initialized = false;

	public StreamingColumnProcessor(Query query,
			long computeThreshold,
			Map<String, String> cryptoSchemeConfig) //
	{
		Validate.notNull(query);
		Validate.notNull(cryptoSchemeConfig);
		this.query = query;
		this.computeThreshold = computeThreshold;
		this.cryptoSchemeConfig = cryptoSchemeConfig;
	}

	private void initialize() throws Exception {

		crypto = CryptoSchemeFactory.make(cryptoSchemeConfig);
		cec = crypto.makeColumnProcessor(query.getQueryInfo(), query.getQueryElements());
		size = 1 << query.getQueryInfo().getHashBitSize();
		rowColumnCounters = new int[size];
		columns = new HashMap<>();
		dataColumns = new HashMap<>();
		initialized = true;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction#process(java.lang.
	 * Object, org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction.Context,
	 * java.lang.Iterable, org.apache.flink.util.Collector)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void process(Tuple rowHashTuple1,
			ProcessWindowFunction<QueueRecord, WindowPerRowHashResult, Tuple, TimeWindow>.Context context,
			Iterable<QueueRecord> values,
			Collector<WindowPerRowHashResult> out) throws Exception {

		if (!initialized) {
			initialize();
		}

		columns.clear();
		dataColumns.clear();
		Arrays.fill(rowColumnCounters, 0);

		int recordCount = 0;
		for (QueueRecord entry : values) {
			addDataElement(entry);
			recordCount++;
			if (recordCount % computeThreshold == 0) {
				processColumns();
			}
		}

		// Process remaining data in dataColumns array
		processColumns();

		TimeWindow window = context.window();

		int rowHash = ((Tuple1<Integer>) rowHashTuple1).f0;

		columns.forEach((column, value) -> out.collect(
				new WindowPerRowHashResult(
						window.getStart(),
						window.maxTimestamp(),
						rowHash,
						column,
						value)));

		if (log.isDebugEnabled()) {
			log.debug("Encryped {} columns of window ending in '{}' with rowHash {}. This window contained {} elements.",
					columns.size(),
					TimestampFormatter.format(window.maxTimestamp()),
					rowHash,
					recordCount);
		}
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
					cec.insert(dataCol.get(i).getLeft(), dataCol.get(i).getRight());
				}
			}
			CipherText newValue = cec.compute();
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
