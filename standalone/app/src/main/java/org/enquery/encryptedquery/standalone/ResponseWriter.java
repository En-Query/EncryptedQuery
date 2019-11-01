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

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QueryInfo;

public class ResponseWriter implements Callable<Integer> {

	// private static final Logger log = LoggerFactory.getLogger(ResponseWriter.class);

	private final QueryInfo queryInfo;
	private final BlockingQueue<ColumnNumberAndCipherText> inputQueue;
	private final Path outputFileName;

	/**
	 * 
	 */
	public ResponseWriter(BlockingQueue<ColumnNumberAndCipherText> inputQueue,
			QueryInfo queryInfo,
			Path outputFileName) {

		Validate.notNull(inputQueue);
		Validate.notNull(queryInfo);
		Validate.notNull(outputFileName);

		this.inputQueue = inputQueue;
		this.queryInfo = queryInfo;
		this.outputFileName = outputFileName;
	}

	/**
	 * This method consolidates the responses from the queue into a single response
	 * 
	 * @param queue
	 * @param query
	 * @return
	 */
	@Override
	public Integer call() {

		int count = 0;
		try (OutputStream output = new FileOutputStream(outputFileName.toFile());
				org.enquery.encryptedquery.xml.transformation.ResponseWriter rw = new org.enquery.encryptedquery.xml.transformation.ResponseWriter(output);) {

			rw.writeBeginDocument();
			rw.writeBeginResponse();
			rw.write(queryInfo);
			rw.writeBeginResultSet();

			ColumnNumberAndCipherText data = inputQueue.take();
			while (!endOfQueue(data)) {
				rw.writeResponseItem((int) data.columnNumber, data.cipherText);
				count++;
				data = inputQueue.take();
			}

			rw.writeEndResultSet();
			rw.writeEndResponse();
			rw.writeEndDocument();

		} catch (Exception e) {
			throw new RuntimeException("Exception consolidating response.", e);
		}

		return count;
	}

	/**
	 * @param r
	 * @return
	 */
	private boolean endOfQueue(ColumnNumberAndCipherText r) {
		return r instanceof ColumnNumberAndCipherTextEof;
	}

}
