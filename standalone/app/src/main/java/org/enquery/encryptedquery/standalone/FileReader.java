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

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.RecordEncoding;
import org.enquery.encryptedquery.filter.RecordFilter;
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class FileReader implements Callable<Long> {

	private static final Logger log = LoggerFactory.getLogger(FileReader.class);
	private static final DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	private long selectorNullCount;
	private final Path inputDataFile;
	private final Integer maxHitsPerSelector;
	private final AtomicLong globalRecordsRead;
	private AtomicLong lineNumber = new AtomicLong(0);
	private long skippedCount;

	// keeps track of how many hits a given
	// selector has
	private HashMap<Integer, Integer> rowIndexCounter = new HashMap<>();
	private final Partitioner partitioner;

	// Log how many records exceeded the maxHitsPerSelector limit
	private HashMap<Integer, Integer> rowIndexOverflowCounter = new HashMap<>();
	private final QueryInfo queryInfo;
	private final BlockingQueue<Record> outputQueue;
	private final RecordEncoding recordEncoding;
	private final JSONStringConverter jsonConverter;
	private final RecordFilter recordFilter;

	/**
	 * 
	 */
	public FileReader(Path inputDataFile,
			QueryInfo queryInfo,
			Integer maxHitsPerSelector,
			BlockingQueue<Record> outputQueue,
			Partitioner partitioner,
			AtomicLong globalRecordsRead) {
		this.inputDataFile = inputDataFile;
		this.queryInfo = queryInfo;
		this.maxHitsPerSelector = maxHitsPerSelector;
		this.outputQueue = outputQueue;
		this.partitioner = partitioner;
		this.globalRecordsRead = globalRecordsRead;

		recordEncoding = new RecordEncoding(queryInfo);
		jsonConverter = new JSONStringConverter(queryInfo.getQuerySchema().getDataSchema());

		String filterExpr = queryInfo.getFilterExpression();
		if (filterExpr != null) {
			recordFilter = new RecordFilter(filterExpr, queryInfo.getQuerySchema().getDataSchema());
			log.info("Initialized using filter expression: '{}'", filterExpr);
		} else {
			recordFilter = null;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public Long call() throws Exception {
		log.info("Start reading file: " + inputDataFile);

		lineNumber.set(0);
		globalRecordsRead.set(0);
		skippedCount = 0;

		try (Stream<String> lines = Files.lines(inputDataFile)) {
			lines.forEach(line -> {
				try {
					processLine(line);
				} catch (PIRException | InterruptedException e) {
					throw new RuntimeException("Error in file '" + inputDataFile + "' line number: " + lineNumber.get(), e);
				}
				lineNumber.incrementAndGet();
			});
			sendEof();
		}

		if (log.isWarnEnabled()) {
			if (selectorNullCount > 0) {
				log.warn("{} Records had a null selector from source", selectorNullCount);
			}
		}

		if (log.isInfoEnabled()) {
			log.info("Read {}, processed {}, and skipped {} records.",
					numFormat.format(lineNumber.get()),
					numFormat.format(globalRecordsRead.get()),
					numFormat.format(skippedCount));

			if (rowIndexOverflowCounter.size() > 0) {
				log.warn("{} Row Hashs overflowed because of MaxHitsPerSelector.  Increase MaxHitsPerSelector to reduce this if resources allow.", rowIndexOverflowCounter.size());
				if (log.isDebugEnabled()) {
					for (int i : rowIndexOverflowCounter.keySet()) {
						log.debug("rowIndex {} exceeded max Hits {} by {}", i, maxHitsPerSelector,
								rowIndexOverflowCounter.get(i));
					}
				}
			}
		}

		return globalRecordsRead.get();
	}


	/**
	 * @throws InterruptedException
	 * 
	 */
	private void sendEof() throws InterruptedException {
		// All data has been submitted to the queue so send an EOF marker
		outputQueue.put(new EofRecord());
	}

	private void processLine(String line) throws PIRException, InterruptedException {
		Map<String, Object> record = null;
		try {
			record = jsonConverter.toStringObjectFlatMap(line);
		} catch (Exception e) {
			skippedCount++;
			log.warn("Failed to parse input record. Skipping. Line number: {}, Error: {}", lineNumber.get(), e.getMessage());
			return;
		}

		if (recordFilter == null || recordFilter.satisfiesFilter(record)) {
			processRow(record);
		} else {
			skippedCount++;
		}
	}

	private void processRow(Map<String, Object> rowData) throws PIRException, InterruptedException {

		final String selector = recordEncoding.getSelectorStringValue(rowData);
		// log.info("Selector Value {}", selector);
		if (selector == null || selector.trim().length() <= 0) {
			selectorNullCount++;
			return;
		}

		final int rowIndex = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selector);

		if (maxHitsPerSelector == null) {
			ingestRecord(rowData, rowIndex);
		} else {
			// Track how many "hits" there are for each selector (Converted into rowIndex)
			Integer accumulator = incrementCounter(rowIndexCounter, rowIndex);
			if (accumulator <= maxHitsPerSelector) {
				ingestRecord(rowData, rowIndex);
			} else {
				incrementCounter(rowIndexOverflowCounter, rowIndex);
			}
		}

	}

	private Integer incrementCounter(Map<Integer, Integer> map, int rowIndex) {
		Integer accumulator = map.get(rowIndex);
		if (accumulator == null) {
			accumulator = 1;
		} else {
			accumulator = accumulator + 1;
		}
		map.put(rowIndex, accumulator);
		return accumulator;
	}

	private void ingestRecord(Map<String, Object> recordData, int rowIndex) throws PIRException, InterruptedException {
		// List<Byte> parts = RecordPartitioner.partitionRecord(partitioner, queryInfo, jsonData);

		ByteBuffer encoded = recordEncoding.encode(recordData);
		List<byte[]> dataChunks = partitioner.createPartitions(encoded, queryInfo.getDataChunkSize());
		outputQueue.put(new Record(rowIndex, dataChunks));
		globalRecordsRead.incrementAndGet();
	}

}
