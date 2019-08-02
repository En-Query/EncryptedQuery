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


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import org.enquery.encryptedquery.flink.Buffer;
import org.enquery.encryptedquery.flink.Buffer.Column;
import org.enquery.encryptedquery.flink.QueueRecord;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ColumnBufferMap extends RichFlatMapFunction<QueueRecord, WindowAndColumn> {

	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory.getLogger(ColumnBufferMap.class);

	private final int bufferSize;
	private final int hashBitSize;
	private final Integer maxHitsPerSelector;
	private final String responseFilePath;
	private final Integer maxRecordCount;
	private Map<String, Buffer> buffersPerFile = new HashMap<>();
	private Map<String, QueueRecord> eofSignalsPerFile = new HashMap<>();

	public ColumnBufferMap(int bufferSize,
			int hashBitSize,
			Integer maxHitsPerSelector,
			String responseFilePath,
			Integer maxRecordCount) {

		Validate.notNull(responseFilePath);
		Validate.isTrue(maxRecordCount == null || maxRecordCount > 0);
		Validate.isTrue(bufferSize > 0);
		Validate.isTrue(hashBitSize > 0);

		this.bufferSize = bufferSize;
		this.hashBitSize = hashBitSize;
		this.maxHitsPerSelector = maxHitsPerSelector;
		this.responseFilePath = responseFilePath;
		this.maxRecordCount = maxRecordCount;
	}


	@Override
	public void open(Configuration parameters) throws Exception {
		super.open(parameters);
		log.info("Initialized with bufferSize: {}, responseFilePath: '{}', maxRecordCount: {}",
				bufferSize,
				responseFilePath,
				maxRecordCount);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.api.common.functions.RichFlatMapFunction#flatMap(java.lang.Object,
	 * org.apache.flink.util.Collector)
	 */
	@Override
	public void flatMap(QueueRecord record, Collector<WindowAndColumn> out) throws Exception {
		final String windowInfo = WindowInfo.windowInfo(record.getWindowMinTimestamp(), record.getWindowMaxTimestamp());
		final String file = ResponseFileNameBuilder.makeBaseFileName(record.getWindowMinTimestamp(), record.getWindowMaxTimestamp());

		Buffer buffer = buffersPerFile.get(file);
		if (buffer == null) {
			buffer = new Buffer(bufferSize, hashBitSize);
			buffersPerFile.put(file, buffer);
			log.info("First record of window {} received.", windowInfo);
			ResponseFileNameBuilder.createEmptyInProgressFile(responseFilePath, record.getWindowMinTimestamp(), record.getWindowMaxTimestamp());
		}

		if (record.isEndOfFile()) {
			eofSignalsPerFile.put(file, record);
			if (buffer.getRecordCount() < record.getTotalRecordCount()) {
				log.info("EOF signal received for window {} but only {} records out of {} received.",
						windowInfo,
						buffer.getRecordCount(),
						record.getTotalRecordCount());
			} else {
				flush(record, out, file, buffer, windowInfo);
			}
		} else {
			processRecord(buffer, record, out);
			if (log.isDebugEnabled()) {
				log.debug("Processed {} columns for window {}.", buffer.getNextColumnNumber(), windowInfo);
			}

			QueueRecord eof = eofSignalsPerFile.get(file);
			if (eof != null && buffer.getRecordCount() == eof.getTotalRecordCount()) {
				flush(eof, out, file, buffer, windowInfo);
			}

		}
	}


	private void flush(QueueRecord eofRecord, Collector<WindowAndColumn> out, final String file, Buffer buffer, final String windowInfo) {
		Validate.isTrue(eofRecord.isEndOfFile());

		flushRemaining(buffer, out, eofRecord);

		log.info("Final processed total of {} columns, total {} records out of {} for window {}.",
				buffer.getNextColumnNumber(),
				buffer.getRecordCount(),
				eofRecord.getTotalRecordCount(),
				windowInfo);

		// send Eof signal to indicate this window is now complete
		WindowAndColumn result = new WindowAndColumn(//
				eofRecord.getWindowMinTimestamp(),
				eofRecord.getWindowMaxTimestamp(),
				buffer.getNextColumnNumber(),
				null);

		result.isEof = true;
		out.collect(result);

		eofSignalsPerFile.remove(file);
		buffersPerFile.remove(file);
	}

	/**
	 * @param buffer
	 * @param out
	 * @param record
	 */
	private void flushRemaining(Buffer buffer, Collector<WindowAndColumn> out, QueueRecord record) {
		Column column;
		while ((column = buffer.peek()) != null) {
			flushColumn(buffer, record, out, column);
		}
	}


	private void processRecord(Buffer buffer, QueueRecord record, Collector<WindowAndColumn> out) throws PIRException, InterruptedException {

		long count = buffer.incrementAndGetRecordCount();
		if (maxRecordCount != null && count > maxRecordCount) {
			return;
		}

		final int row = record.getRowIndex();
		// too many records with the same row hash
		if (maxHitsPerSelector != null &&
				buffer.incrementAndGetHitsPerSelector(row) > maxHitsPerSelector) {
			return;
		}

		Iterator<byte[]> iterator = record.getHitValPartitions().iterator();
		while (iterator.hasNext()) {
			if (!buffer.spaceAvailable(row)) {
				flushColumn(buffer, record, out, buffer.peek());
			}
			buffer.add(row, iterator.next());
		}
	}


	private void flushColumn(Buffer buffer, QueueRecord record, Collector<WindowAndColumn> out, Column column) {
		Validate.notNull(column);
		out.collect(new WindowAndColumn(record.getWindowMinTimestamp(),
				record.getWindowMaxTimestamp(),
				column.getColumnNumber(),
				column));
		buffer.pop();
	}
}
