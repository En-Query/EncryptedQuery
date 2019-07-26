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
package org.enquery.encryptedquery.flink;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.MapFunction;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.RecordEncoding;
import org.enquery.encryptedquery.utils.KeyedHash;

public final class RowHashMapFunction implements MapFunction<Map<String, Object>, QueueRecord> {

	private static final long serialVersionUID = 1L;

	// private final int selectorFieldIndex;
	// private final RowTypeInfo rowTypeInfo;
	private final QueryInfo queryInfo;
	private final String key;
	private final int hashBitSize;

	transient private Partitioner partitioner;
	transient private RecordEncoding recordEncoding;


	public RowHashMapFunction(QueryInfo queryInfo) {

		Validate.notNull(queryInfo);

		this.queryInfo = queryInfo;
		this.key = queryInfo.getHashKey();
		this.hashBitSize = queryInfo.getHashBitSize();
		Validate.notNull(queryInfo.getQuerySchema().getSelectorField());
	}

	@Override
	public QueueRecord map(Map<String, Object> recordData) throws Exception {

		if (recordData == null) return new QueueRecord();

		if (partitioner == null) {
			partitioner = new Partitioner();
		}
		if (recordEncoding == null) {
			recordEncoding = new RecordEncoding(queryInfo);
		}

		// TODO: use asString, or asByteArray?
		final String selectorValue = recordEncoding.getSelectorStringValue(recordData);

		final Integer rowIndex = KeyedHash.hash(key, hashBitSize, selectorValue);

		final ByteBuffer encoded = recordEncoding.encode(recordData);
		final List<byte[]> dataChunks = partitioner.createPartitions(encoded, queryInfo.getDataChunkSize());
		QueueRecord record = new QueueRecord();
		record.setRowIndex(rowIndex);
		record.setSelector(selectorValue);
		record.setDataChunks(dataChunks);
		return record;
	}

}
