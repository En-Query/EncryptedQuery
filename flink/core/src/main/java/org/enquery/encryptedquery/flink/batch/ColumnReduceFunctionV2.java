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


import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.RichGroupReduceFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeFactory;
import org.enquery.encryptedquery.flink.batch.Buffer.Column;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ColumnReduceFunctionV2 extends RichGroupReduceFunction<Tuple2<Long, Buffer.Column>, Tuple2<Integer, CipherText>> {

	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory.getLogger(ColumnReduceFunctionV2.class);

	private final Query query;
	private final Map<String, String> config;

	// non serializable
	private transient CryptoScheme crypto;
	private transient long columnsProcessed;
	private transient ColumnProcessor columnProcessor;
	private transient byte[] handle;

	public ColumnReduceFunctionV2(Query query,
			Map<String, String> config) {

		Validate.notNull(query, "Query cannot be null!");
		Validate.notNull(config, "Config cannot be null!");

		this.query = query;
		this.config = config;
	}

	@Override
	public void open(Configuration parameters) throws Exception {
		super.open(parameters);
		crypto = CryptoSchemeFactory.make(config);
		Validate.notNull(crypto);

		handle = crypto.loadQuery(query.getQueryInfo(), query.getQueryElements());
		Validate.notNull(handle);

		columnProcessor = crypto.makeColumnProcessor(handle);
		Validate.notNull(columnProcessor);

		columnsProcessed = 0L;
		log.info("Column processor initialized");
	}

	@Override
	public void close() throws Exception {
		log.info("Column processor processed {} columns", columnsProcessed);
		if (handle != null) {
			crypto.unloadQuery(handle);
			handle = null;
		}
		crypto = null;
	}

	@Override
	public void reduce(Iterable<Tuple2<Long, Buffer.Column>> values, Collector<Tuple2<Integer, CipherText>> out) throws Exception {
		for (Tuple2<Long, Buffer.Column> column : values) {
			try {
				processColumn(column.f1, out);
				columnsProcessed++;
			} catch (Exception e) {
				throw new RuntimeException(String.format("Exception processing column %s.", column), e);
			}
		}
	}

	private void processColumn(Column column, Collector<Tuple2<Integer, CipherText>> out) throws PIRException, InterruptedException {
		column.forEachRow((row, data) -> {
			columnProcessor.insert(row, data);
		});
		CipherText cipherText = columnProcessor.computeAndClear();
		ColumnNumberAndCipherText result = new ColumnNumberAndCipherText(column.getColumnNumber(), cipherText);
		out.collect(Tuple2.of((int) result.columnNumber, result.cipherText));
	}

}
