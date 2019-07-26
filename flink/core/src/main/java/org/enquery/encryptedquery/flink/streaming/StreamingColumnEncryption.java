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


import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class StreamingColumnEncryption extends RichMapFunction<WindowAndColumn, CipherTextAndColumnNumber> {

	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory.getLogger(StreamingColumnEncryption.class);

	private final Query query;

	private long columnsProcessed;
	private transient CryptoScheme crypto;

	private Map<String, String> config;
	private byte[] handle;
	private ColumnProcessor columnProcessor;

	public StreamingColumnEncryption(Query query,
			Map<String, String> config) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

		Validate.notNull(query, "Query cannot be null!");
		Validate.notNull(config, "Config cannot be null!");

		this.query = query;
		this.config = config;
	}

	@Override
	public void open(Configuration parameters) throws Exception {
		super.open(parameters);
		crypto = CryptoSchemeFactory.make(config);
		handle = crypto.loadQuery(query.getQueryInfo(), query.getQueryElements());
		columnProcessor = crypto.makeColumnProcessor(handle);
		columnsProcessed = 0L;
		log.info("Column processor initialized");
	}

	@Override
	public void close() throws Exception {
		if (handle != null) {
			crypto.unloadQuery(handle);
			handle = null;
		}
		crypto = null;
		log.info("Column processor processed {} columns", columnsProcessed);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.flink.api.common.functions.RichMapFunction#map(java.lang.Object)
	 */
	@Override
	public CipherTextAndColumnNumber map(WindowAndColumn value) throws Exception {
		final long starTime = System.currentTimeMillis();
		final boolean debugging = log.isDebugEnabled();

		CipherTextAndColumnNumber result = new CipherTextAndColumnNumber();
		result.windowMinTimestamp = value.windowMinTimestamp;
		result.windowMaxTimestamp = value.windowMaxTimestamp;
		result.col = value.colNum;

		if (value.isEof) {
			if (log.isInfoEnabled()) {
				log.info("Finished encrypting all {} columns of window {}. Sending EOF signal.",
						value.colNum,
						WindowInfo.windowInfo(value.windowMinTimestamp, value.windowMaxTimestamp));
			}
			result.isEof = true;
		} else {
			Validate.notNull(value.column);

			value.column.forEachRow((row, data) -> {
				columnProcessor.insert(row, data);
			});

			CipherText cipherText = columnProcessor.computeAndClear();
			++columnsProcessed;

			if (debugging) {
				final long elapsedTime = System.currentTimeMillis() - starTime;
				log.debug("Finished encrypting column {} of window {} in {}ms",
						value.colNum,
						WindowInfo.windowInfo(value.windowMinTimestamp, value.windowMaxTimestamp),
						elapsedTime);
			}
			result.cipherText = cipherText;
		}

		return result;
	}

}
