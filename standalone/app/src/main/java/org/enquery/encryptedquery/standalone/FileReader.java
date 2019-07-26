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

import java.io.IOException;
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
	private final AtomicLong recordsRead;
	private AtomicLong lineNumber = new AtomicLong(0);

	// keeps track of how many hits a given
	// selector has
	private HashMap<Integer, Integer> rowIndexCounter = new HashMap<>();
	private final Partitioner partitioner;

	// Log how many records exceeded the maxHitsPerSelector limit
	private HashMap<Integer, Integer> rowIndexOverflowCounter = new HashMap<>();
	private final QueryInfo queryInfo;
	private final List<BlockingQueue<QueueRecord>> queues;
	private final RecordEncoding recordEncoding;
	private final JSONStringConverter jsonConverter;

	/**
	 * 
	 */
	public FileReader(Path inputDataFile,
			QueryInfo queryInfo,
			Integer maxHitsPerSelector,
			List<BlockingQueue<QueueRecord>> queues,
			Partitioner partitioner,
			AtomicLong recordsReadCounter) {
		this.inputDataFile = inputDataFile;
		this.queryInfo = queryInfo;
		this.maxHitsPerSelector = maxHitsPerSelector;
		this.queues = queues;
		this.partitioner = partitioner;
		this.recordsRead = recordsReadCounter;

		recordEncoding = new RecordEncoding(queryInfo);
		jsonConverter = new JSONStringConverter(queryInfo.getQuerySchema().getDataSchema());
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
		recordsRead.set(0);

		try (Stream<String> lines = Files.lines(inputDataFile)) {
			lines.forEach(line -> processLine(line));
			sendEof();
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException("Error processing file: " + inputDataFile, e);
		}


		if (log.isWarnEnabled()) {
			if (selectorNullCount > 0) {
				log.warn("{} Records had a null selector from source", selectorNullCount);
			}
		}

		if (log.isInfoEnabled()) {
			log.info("Imported {} records for processing", numFormat.format(lineNumber.get()));
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

		return recordsRead.get();
	}


	/**
	 * @throws InterruptedException
	 * 
	 */
	private void sendEof() throws InterruptedException {
		// All data has been submitted to the queue so send an EOF marker
		QueueRecord eof = new QueueRecord();
		eof.setIsEndOfFile(true);
		for (BlockingQueue<QueueRecord> q : queues) {
			q.put(eof);
		}
	}

	private void processLine(String line) {
		Map<String, Object> jsonData = null;
		try {
			jsonData = jsonConverter.toStringObjectFlatMap(line);
		} catch (Exception e) {
			log.warn("Failed to parse input record. Skipping. Line number: {}, Error: {}", lineNumber.get(), e.getMessage());
			return;
		}

		if (jsonData != null && jsonData.size() > 0) {
			processRow(jsonData);
			lineNumber.incrementAndGet();
		} else if (jsonData.size() < 1) {
			log.warn("jsonData has no data, input line: {}", line);
			return;
		} else {
			log.warn("jsonData is null, input line: {}", line);
			return;
		}
	}

	private void processRow(Map<String, Object> rowData) {

		final String selector = recordEncoding.getSelectorStringValue(rowData);
		// log.info("Selector Value {}", selector);
		if (selector == null || selector.trim().length() <= 0) {
			selectorNullCount++;
			return;
		}

		try {
			final int rowIndex = KeyedHash.hash(queryInfo.getHashKey(), queryInfo.getHashBitSize(), selector);
			// logger.info("Selector {} / Hash {}", selector, rowIndex);

			if (maxHitsPerSelector == null) {
				ingestRecord(rowData, selector, rowIndex);
			} else {
				// Track how many "hits" there are for each selector (Converted into
				// rowIndex)
				if (rowIndexCounter.containsKey(rowIndex)) {
					rowIndexCounter.put(rowIndex, (rowIndexCounter.get(rowIndex) + 1));
				} else {
					rowIndexCounter.put(rowIndex, 1);
				}

				// If we are not over the max hits value add the record to the
				// appropriate queue
				if (rowIndexCounter.get(rowIndex) <= maxHitsPerSelector) {
					ingestRecord(rowData, selector, rowIndex);
				} else {
					if (rowIndexOverflowCounter.containsKey(rowIndex)) {
						rowIndexOverflowCounter.put(rowIndex, (rowIndexOverflowCounter.get(rowIndex) + 1));
					} else {
						rowIndexOverflowCounter.put(rowIndex, 1);
					}
				}
			}
		} catch (Exception e) {
			log.error("Exception adding record selector {} / line number {}, Exception: {}", selector, lineNumber.get(), e.getMessage(), e);
		}
	}

	private void ingestRecord(Map<String, Object> rowData, final String selector, int rowIndex) throws PIRException, InterruptedException {

		ByteBuffer encoded = recordEncoding.encode(rowData);
		List<byte[]> dataChunks = partitioner.createPartitions(encoded, queryInfo.getDataChunkSize());
		QueueRecord record = new QueueRecord(rowIndex, selector, dataChunks);

		// List<Byte> parts = RecordPartitioner.partitionRecord(partitioner, queryInfo, rowData);
		// QueueRecord qr = new QueueRecord(rowIndex, selector, parts);
		int whichQueue = rowIndex % queues.size();
		// log.info("Hash {} going to queue {} with {} bytes", rowIndex, whichQueue,
		// parts.size());

		// insert element in queue, waits until space is available
		queues.get(whichQueue).put(record);
		recordsRead.incrementAndGet();
	}

}
