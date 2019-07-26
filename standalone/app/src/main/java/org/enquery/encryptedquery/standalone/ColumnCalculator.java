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

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.standalone.Buffer.Column;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ColumnCalculator implements Callable<Long> {

	private static final Logger log = LoggerFactory.getLogger(ColumnCalculator.class);

	private final Query query;
	private long localColumnCount;
	private BlockingQueue<Column> inputQueue;
	private BlockingQueue<ColumnNumberAndCipherText> outputQueue;
	private CryptoScheme crypto;
	private byte[] handle;
	private final AtomicLong globalColumnsProcessed;
	private final AtomicLong globalChunksProcessed;
	private ColumnProcessor columnProcessor;
	private UUID colProcId;

	public ColumnCalculator(BlockingQueue<Buffer.Column> inputQueue,
			BlockingQueue<ColumnNumberAndCipherText> outputQueue,
			Query query,
			CryptoScheme crypto,
			AtomicLong globalColumnsProcessed,
			AtomicLong globalChunkProcessed) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

		Validate.notNull(inputQueue);
		Validate.notNull(outputQueue);
		Validate.notNull(query);
		Validate.notNull(crypto);
		Validate.notNull(globalColumnsProcessed);
		Validate.notNull(globalChunkProcessed);

		this.query = query;
		this.inputQueue = inputQueue;
		this.outputQueue = outputQueue;
		this.crypto = crypto;
		this.globalColumnsProcessed = globalColumnsProcessed;
		this.globalChunksProcessed = globalChunkProcessed;
		this.colProcId = UUID.randomUUID();
	}

	/**
	 * Reset The response for the next iteration
	 */
	private void setup() {
		handle = crypto.loadQuery(query.getQueryInfo(), query.getQueryElements());
		columnProcessor = crypto.makeColumnProcessor(handle);
		localColumnCount = 0;
	}

	private void tearDown() {
		if (handle != null) {
			crypto.unloadQuery(handle);
			handle = null;
		}
		crypto = null;
	}


	@Override
	public Long call() throws PIRException, InterruptedException {

		setup();
		try {
			// this Queue blocks until item is available
			Column column = null;
			while (!((column = inputQueue.take()) instanceof EofColumn)) {
				try {
					processColumn(column);
				} catch (Exception e) {
					throw new RuntimeException(String.format("Exception processing column %s.", column), e);
				}
			}
            if (log.isDebugEnabled()) {
    			log.debug("Column Processor {} Finished processing {} columns.", colProcId, localColumnCount);
            }
            return localColumnCount;
		} finally {
			tearDown();
		}
	}

	private void processColumn(Column column) throws PIRException, InterruptedException {

		int numChunksInColumn = column.getDataCount();

		column.forEachRow((row, data) -> {
			columnProcessor.insert(row, data);
		});

		CipherText cipherText = columnProcessor.computeAndClear();

		ColumnNumberAndCipherText result = //
				new ColumnNumberAndCipherText(column.getColumnNumber(), cipherText);

		outputQueue.put(result);

		localColumnCount++;
		globalColumnsProcessed.incrementAndGet();
		globalChunksProcessed.addAndGet((long)numChunksInColumn);
	}

}
