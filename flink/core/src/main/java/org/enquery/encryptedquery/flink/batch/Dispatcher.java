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

import java.util.Iterator;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.RichGroupReduceFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import org.enquery.encryptedquery.flink.QueueRecord;
import org.enquery.encryptedquery.flink.batch.Buffer.Column;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Dispatcher extends RichGroupReduceFunction<QueueRecord, Tuple2<Long, Buffer.Column>> {

	private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);
	// private final DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	private static final long serialVersionUID = 1L;

	private long columnCount;

	private final int bufferSize;
	private final int hashBitSize;

	private Buffer buffer;

	public Dispatcher(int bufferSize, int hashBitSize) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		this.bufferSize = bufferSize;
		this.hashBitSize = hashBitSize;
	}

	@Override
	public void open(Configuration parameters) throws Exception {
		buffer = new Buffer(bufferSize, hashBitSize);
	}
	
	@Override
	public void close() throws Exception {
       log.info("Dispatched {} columns", columnCount);
	}

	@Override
	public void reduce(Iterable<QueueRecord> values, Collector<Tuple2<Long, Buffer.Column>> out) throws Exception {
		log.info("Dispatcher Started, Buffer Size {}", bufferSize);
		if (buffer == null) {
			buffer = new Buffer(bufferSize, hashBitSize);
		}

		for (QueueRecord entry : values) {
			try {
                processRecord(entry, out);
			} catch (Exception e) {
				throw new RuntimeException(String.format("Exception processing record %s.", entry), e);
			}
		}
		flushRemaining(out);
	}

	/**
	 * @param record
	 * @throws PIRException
	 * @throws InterruptedException
	 * @throws Exception
	 */
	private void processRecord(QueueRecord record, Collector<Tuple2<Long, Buffer.Column>> out) throws PIRException, InterruptedException {

		final int row = record.getRowIndex();

		Iterator<byte[]> iterator = record.getHitValPartitions().iterator();
		while (iterator.hasNext()) {

			if (!buffer.spaceAvailable(row)) {
				flush(1, out);// Math.min(remainingChunks, W));
			}

			buffer.add(row, iterator.next());
		}

	}

	void flush(final int numColumnsToFlush, Collector<Tuple2<Long, Buffer.Column>> out) throws InterruptedException {
		int pending = numColumnsToFlush;
		while (pending > 0) {
			Column column = buffer.peek();
			Validate.notNull(column);
			out.collect(Tuple2.of(column.getColumnNumber(), column));
			columnCount++;
			buffer.pop();
			--pending;
		}
	}

	/**
	 * @throws InterruptedException
	 * 
	 */
	private void flushRemaining(Collector<Tuple2<Long, Buffer.Column>> out) throws InterruptedException {
		Column column;
		while ((column = buffer.peek()) != null) {
            out.collect(Tuple2.of(column.getColumnNumber(), column));
			buffer.pop();
			++columnCount;
		}
	}
}
