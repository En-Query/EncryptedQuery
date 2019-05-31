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

import java.security.PublicKey;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.RichGroupReduceFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;

public class ColumnReduceFunction extends
		RichGroupReduceFunction<Tuple2<Integer, CipherText>, Tuple2<Integer, CipherText>> {

	private static final long serialVersionUID = 1L;
	private final PublicKey publicKey;
	private final Map<String, String> config;

	// non serializable
	private transient CryptoScheme crypto;

	public ColumnReduceFunction(PublicKey publicKey, Map<String, String> config) {
		Validate.notNull(publicKey);
		Validate.notNull(config);
		this.publicKey = publicKey;
		this.config = config;
	}

	@Override
	public void open(Configuration parameters) throws Exception {
		super.open(parameters);
		crypto = CryptoSchemeFactory.make(config);
	}

	@Override
	public void close() throws Exception {
		crypto.close();
		super.close();
	}

	@Override
	public void reduce(Iterable<Tuple2<Integer, CipherText>> values, Collector<Tuple2<Integer, CipherText>> out) throws Exception {
		CipherText accumulator = crypto.encryptionOfZero(publicKey);
		Integer column = null;
		for (Tuple2<Integer, CipherText> entry : values) {
			// capture the column only once, all will be the same
			if (column == null) {
				column = entry.f0;
			} else {
				Validate.isTrue(entry.f0.equals(column));
			}
			accumulator = crypto.computeCipherAdd(publicKey, accumulator, entry.f1);
		}
		out.collect(Tuple2.of(column, accumulator));
	}
}
