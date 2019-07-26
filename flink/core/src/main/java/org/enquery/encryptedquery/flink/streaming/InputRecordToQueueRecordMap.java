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

import java.nio.ByteBuffer;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.MapFunction;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.RecordEncoding;
import org.enquery.encryptedquery.flink.QueueRecord;
import org.enquery.encryptedquery.utils.KeyedHash;

public final class InputRecordToQueueRecordMap implements MapFunction<InputRecord, QueueRecord> {

	private static final long serialVersionUID = 1L;

	// private final int selectorFieldIndex;
	// private final RowTypeInfo rowTypeInfo;
	private final QueryInfo queryInfo;
	private final String key;
	private final int hashBitSize;

	transient private Partitioner partitioner;
	transient private RecordEncoding recordEncoding;


	public InputRecordToQueueRecordMap(QueryInfo queryInfo) {

		// Validate.notNull(rowTypeInfo);
		Validate.notNull(queryInfo);

		this.queryInfo = queryInfo;
		// this.selectorFieldIndex = selectorFieldIndex;
		this.key = queryInfo.getHashKey();
		this.hashBitSize = queryInfo.getHashBitSize();
		// this.rowTypeInfo = rowTypeInfo;
		Validate.notNull(queryInfo.getQuerySchema().getSelectorField());
	}

	@Override
	public QueueRecord map(InputRecord recordData) throws Exception {

		if (recordData.eof) {
			QueueRecord result = new QueueRecord();
			result.setWindowMinTimestamp(recordData.windowMinTimestamp);
			result.setWindowMaxTimestamp(recordData.windowMaxTimestamp);
			result.setIsEndOfFile(true);
			result.setTotalRecordCount(recordData.windowSize);
			return result;
		}

		if (partitioner == null) {
			partitioner = new Partitioner();
		}
		if (recordEncoding == null) {
			recordEncoding = new RecordEncoding(queryInfo);
		}

		// TODO: use asString, or asByteArray?
		final String selectorValue = recordEncoding.getSelectorStringValue(recordData.data);
		// row.getField(selectorFieldIndex));
		// asString(row.getField(selectorFieldIndex),
		// selectorFieldType);

		final Integer rowIndex = KeyedHash.hash(key, hashBitSize, selectorValue);

		// Load Row into a Map to pass to the partitioner
		// final Map<String, Object> recordData = new HashMap<>();
		// queryInfo.getQuerySchema().getElementList()
		// .stream()
		// .forEach(field -> {
		// Object value = getFieldValue(row, field.getName());
		// if (value != null) {
		// recordData.put(field.getName(), value);
		// }
		// });

		final ByteBuffer encoded = recordEncoding.encode(recordData.data);
		final List<byte[]> dataChunks = partitioner.createPartitions(encoded, queryInfo.getDataChunkSize());

		final QueueRecord record = new QueueRecord();
		record.setRowIndex(rowIndex);
		record.setSelector(selectorValue);
		record.setDataChunks(dataChunks);
		record.setWindowMinTimestamp(recordData.windowMinTimestamp);
		record.setWindowMaxTimestamp(recordData.windowMaxTimestamp);
		return record;
	}

	/*--
	private Object getFieldValue(Row row, final String fieldName) {
		final int index = rowTypeInfo.getFieldIndex(fieldName);
		Validate.exclusiveBetween(-1, row.getArity(), index, "Field %s not found in row.", fieldName);
		return row.getField(index);
	}*/

}
