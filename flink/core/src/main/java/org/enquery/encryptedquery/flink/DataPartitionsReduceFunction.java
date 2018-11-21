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
package org.enquery.encryptedquery.flink;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.util.Collector;
import org.enquery.encryptedquery.core.FieldTypes;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.responder.wideskies.common.ComputeEncryptedColumn;
import org.enquery.encryptedquery.responder.wideskies.common.QueueRecord;
import org.enquery.encryptedquery.utils.ConversionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataPartitionsReduceFunction implements
		GroupReduceFunction<QueueRecord, Tuple2<Integer, BigInteger>>, FieldTypes {

	private static final Logger log = LoggerFactory.getLogger(DataPartitionsReduceFunction.class);
	// private final DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");
	// private boolean debugLogOn = log.isDebugEnabled();

	private static final long serialVersionUID = -2249924018671569475L;

	private long computeThreshold = 0;
	// private final int dataPartitionBitSize;
	// private int hashBitSize;

	private HashMap<Integer, List<Pair<Integer, Byte[]>>> dataColumns;

	// the column values for the encrypted query calculations
	private HashMap<Integer, BigInteger> columns = null;

	// keeps track of column location for each rowIndex (Selector Hash)
	private ArrayList<Integer> rowColumnCounters;

	private final Map<Integer, BigInteger> queryElements;
	private final BigInteger nSquared;
	private final Map<String, String> config;
	private final int hashBitSize;

	// non serializable
	private transient ComputeEncryptedColumn cec;
	private transient ModPowAbstraction modPowAbstraction;
	private transient boolean initialized = false;

	public DataPartitionsReduceFunction(Map<Integer, BigInteger> queryElements,
			BigInteger nSquared,
			Map<String, String> config,
			int hashBitSize) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		this.queryElements = queryElements;
		this.nSquared = nSquared;
		this.config = config;
		this.hashBitSize = hashBitSize;
	}

	@SuppressWarnings("unchecked")
	private void initialize() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		String cecClassName = config.get(FlinkConfigurationProperties.COLUMN_ENCRYPTION_CLASS_NAME);
		Validate.notEmpty(cecClassName, "Missing or invalid property " + FlinkConfigurationProperties.COLUMN_ENCRYPTION_CLASS_NAME);

		Class<ComputeEncryptedColumn> cecClass = (Class<ComputeEncryptedColumn>) Class.forName(cecClassName);
		cec = cecClass.newInstance();

		String modPowClassName = config.get(FlinkConfigurationProperties.MOD_POW_CLASS_NAME);
		Validate.notEmpty(modPowClassName, "Missing or invalid property " + FlinkConfigurationProperties.MOD_POW_CLASS_NAME);

		Class<ModPowAbstraction> modPowClass = (Class<ModPowAbstraction>) Class.forName(modPowClassName);
		modPowAbstraction = modPowClass.newInstance();


		if (config.containsKey(FlinkConfigurationProperties.COMPUTE_THRESHOLD)) {
			String ct = config.get(FlinkConfigurationProperties.COMPUTE_THRESHOLD).toString();
			computeThreshold = Long.parseLong(ct);
		} else {
			computeThreshold = 30000;
		}

		cec.initialize(queryElements, nSquared, modPowAbstraction, config);

		// Initialize row counters
		rowColumnCounters = new ArrayList<>();
		for (int i = 0; i < (1 << hashBitSize); ++i) {
			rowColumnCounters.add(0);
		}

		columns = new HashMap<>();
		dataColumns = new HashMap<>();

		initialized = true;
	}

	@Override
	public void reduce(Iterable<QueueRecord> values, Collector<Tuple2<Integer, BigInteger>> out) throws Exception {

		if (!initialized) {
			initialize();
		}

		log.info("Processing data with computeThreshold {}", computeThreshold);

		int recordCount = 0;
		for (QueueRecord entry : values) {
			addDataElement(entry);
			recordCount++;
			if ((recordCount % computeThreshold) == 0) {
				processColumns();
			}
		}

		// Process remaining data in dataColumns array
		processColumns();

		columns.forEach((k, v) -> out.collect(Tuple2.of(k, v)));

		log.info("Processed {} records", recordCount);
		columns.clear();
	}

	private void addDataElement(QueueRecord qr) throws Exception {
		final List<Byte[]> hitValPartitions = qr.getHitValPartitions();
		final int rowIndex = qr.getRowIndex();
		final int rowCounter = rowColumnCounters.get(rowIndex);

		for (int i = 0; i < hitValPartitions.size(); ++i) {
			final int key = i + rowCounter;

			List<Pair<Integer, Byte[]>> list = dataColumns.get(key);
			if (list == null) {
				list = new ArrayList<>();
				dataColumns.put(key, list);
			}
			list.add(Pair.of(rowIndex, hitValPartitions.get(i)));
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


	/**
	 * This method adds all the entries ready for computation into the library and computes to a
	 * single value. That value is then stored to be computed with the next batch of values.
	 */
	private void computeEncryptedColumns() {
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
			column = (column.multiply(newColumn)).mod(nSquared);
			columns.put(col, column);
		}
	}

}
