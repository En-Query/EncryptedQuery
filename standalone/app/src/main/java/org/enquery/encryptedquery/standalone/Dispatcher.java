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

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.standalone.Buffer.Column;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Dispatcher implements Callable<Long> {

	private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);
	// private final DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");

	// private final Partitioner partitioner;
	private final BlockingQueue<Record> inputQueue;
	private final BlockingQueue<Buffer.Column> outputQueue;
	private final CryptoScheme crypto;
	private final AtomicLong globalColumnsEmitted;
	private final AtomicLong globalRecordsProcessed;

	private final Query query;
	private long columnCount;
	private byte[] handle;

	private final int bufferWidth;

	private Buffer buffer;

	public Dispatcher(BlockingQueue<Record> inputQueue,
			BlockingQueue<Buffer.Column> outputQueue,
			Query query,
			CryptoScheme crypto,
			int bufferWidth,
			AtomicLong globalRecordsProcessed,
			AtomicLong globaColumnsEmitted) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

		Validate.notNull(inputQueue);
		Validate.notNull(query);
		Validate.notNull(crypto);
		Validate.notNull(globalRecordsProcessed);
		Validate.notNull(globaColumnsEmitted);

		this.query = query;
		this.inputQueue = inputQueue;
		this.outputQueue = outputQueue;
		this.bufferWidth = bufferWidth;
		this.crypto = crypto;
		this.globalRecordsProcessed = globalRecordsProcessed;
		this.globalColumnsEmitted = globaColumnsEmitted;
	}

	/**
	 * Reset The response for the next iteration
	 */
	private void setup() {
		buffer = new Buffer(bufferWidth, query.getQueryInfo().getHashBitSize());
	}

	private void tearDown() {
		if (handle != null) {
			crypto.unloadQuery(handle);
			handle = null;
		}
	}


	@Override
	public Long call() throws PIRException, InterruptedException {

		setup();
		try {
			// this Queue blocks until item is available
			Record nextRecord = null;
			while (!((nextRecord = inputQueue.take()) instanceof EofRecord)) {
				try {
					processRecord(nextRecord);
				} catch (Exception e) {
					throw new RuntimeException(String.format("Exception processing record %s.", nextRecord), e);
				}
			}

			flushRemaining();
			log.info("Total Column count ( {} ).", columnCount);
			return columnCount;
		} finally {
			tearDown();
		}
	}

	/**
	 * @param record
	 * @throws PIRException
	 * @throws InterruptedException
	 * @throws Exception
	 */
	private void processRecord(Record record) throws PIRException, InterruptedException {

		final int row = record.getRowIndex();

		Iterator<byte[]> iterator = record.getDataChunks().iterator();
		while (iterator.hasNext()) {

			if (!buffer.spaceAvailable(row)) {
				flush(1);// Math.min(remainingChunks, W));
			}

			buffer.add(row, iterator.next());
		}

		// increment record counters
		globalRecordsProcessed.incrementAndGet();
	}

	void flush(final int numColumnsToFlush) throws InterruptedException {
		int pending = numColumnsToFlush;
		while (pending > 0) {
			Column column = buffer.peek();
			Validate.notNull(column);

			outputQueue.put(column);
			columnCount++;
			buffer.pop();
			globalColumnsEmitted.incrementAndGet();
			--pending;
		}
		// log.info("Buffer full before end of input. Dispatched {} columns.", numColumnsToFlush -
		// pending);
	}

	/**
	 * @throws InterruptedException
	 * 
	 */
	private void flushRemaining() throws InterruptedException {
		// int cnt = 0;
		Column column;
		while ((column = buffer.peek()) != null) {
			outputQueue.put(column);
			buffer.pop();
			globalColumnsEmitted.incrementAndGet();
			++columnCount;
			// ++cnt;
		}
		// log.info("Dispatched remaining {} columns at the end of input.", cnt);
	}
}
