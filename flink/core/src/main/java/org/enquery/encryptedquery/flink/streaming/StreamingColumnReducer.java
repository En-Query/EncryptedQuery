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

import java.security.PublicKey;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.flink.TimestampFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamingColumnReducer extends ProcessWindowFunction<WindowPerRowHashResult, WindowPerColumnResult, Integer, TimeWindow> {

	private static final Logger log = LoggerFactory.getLogger(StreamingColumnReducer.class);

	private static final long serialVersionUID = 1L;
	private final PublicKey publicKey;
	private final Map<String, String> config;

	// non serializable
	private transient boolean initialized = false;
	private transient CryptoScheme crypto;

	public StreamingColumnReducer(PublicKey publicKey,
			Map<String, String> config) {
		Validate.notNull(publicKey);
		Validate.notNull(config);
		this.publicKey = publicKey;
		this.config = config;
	}

	private void initialize() throws Exception {
		crypto = CryptoSchemeFactory.make(config);
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
	@Override
	public void process(Integer column,
			ProcessWindowFunction<WindowPerRowHashResult, WindowPerColumnResult, Integer, TimeWindow>.Context context,
			Iterable<WindowPerRowHashResult> values,
			Collector<WindowPerColumnResult> out) throws Exception {

		if (!initialized) {
			initialize();
		}


		WindowPerRowHashResult first = null;
		int count = 0;
		CipherText accumulator = crypto.encryptionOfZero(publicKey);
		for (WindowPerRowHashResult entry : values) {
			// log.info("Reducing: {}", entry);
			if (first == null) first = entry;
			accumulator = crypto.computeCipherAdd(publicKey, accumulator, entry.cipherText);
			++count;
		}

		WindowPerColumnResult result = new WindowPerColumnResult(//
				first.windowMinTimestamp,
				first.windowMaxTimestamp,
				column,
				accumulator);

		out.collect(result);

		if (log.isDebugEnabled()) {
			log.debug("Reduced column {} of window ending in '{}' with {} elements.",
					column,
					TimestampFormatter.format(first.windowMaxTimestamp),
					count);
		}
	}
}
