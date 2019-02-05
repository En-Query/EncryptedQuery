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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.types.Row;
import org.enquery.encryptedquery.core.FieldTypes;
import org.enquery.encryptedquery.core.Partitioner;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.responder.QueueRecord;
import org.enquery.encryptedquery.responder.RecordPartitioner;
import org.enquery.encryptedquery.utils.KeyedHash;

public final class RowHashMapFunction implements MapFunction<Row, QueueRecord>, FieldTypes {

	private static final long serialVersionUID = 1L;

	private final int selectorFieldIndex;
	private final String selectorFieldType;
	private final RowTypeInfo rowTypeInfo;
	private final QueryInfo queryInfo;
	private final String key;
	private final int hashBitSize;
	private final int dataChunkSize;

	transient private Partitioner partitioner;


	public RowHashMapFunction(int selectorFieldIndex,
			String selectorFieldType,
			RowTypeInfo rowTypeInfo,
			QueryInfo queryInfo) {
		Validate.notNull(selectorFieldType);
		Validate.notNull(rowTypeInfo);
		Validate.notNull(queryInfo);

		this.queryInfo = queryInfo;
		this.selectorFieldIndex = selectorFieldIndex;
		this.selectorFieldType = selectorFieldType;
		this.key = queryInfo.getHashKey();
		this.hashBitSize = queryInfo.getHashBitSize();
		this.rowTypeInfo = rowTypeInfo;
		this.dataChunkSize = queryInfo.getDataChunkSize();
		if (queryInfo.getEmbedSelector()) {
			Validate.notNull(queryInfo.getQuerySchema().getSelectorField());
		}
	}

	@Override
	public QueueRecord map(Row row) throws Exception {

		if (row == null) return new QueueRecord();

		if (partitioner == null) {
			partitioner = new Partitioner();
		}

		// TODO: use asString, or asByteArray?
		final String selectorValue = asString(row.getField(selectorFieldIndex), selectorFieldType);

		final Integer rowIndex = KeyedHash.hash(key, hashBitSize, selectorValue);
		final Map<String, Object> recordData = new HashMap<>();

		queryInfo.getQuerySchema().getElementList()
				.stream()
				.forEach(field -> recordData.put(field.getName(),
						getFieldValue(row, field.getName())));

		List<Byte> recordParts = RecordPartitioner.partitionRecord(partitioner,
				queryInfo,
				recordData);

		QueueRecord record = new QueueRecord(rowIndex, selectorValue, recordParts);
		record.setHitValPartitions(partitioner.createPartitions(recordParts, dataChunkSize));
		return record;
	}

	private Object getFieldValue(Row row, final String fieldName) {
		final int index = rowTypeInfo.getFieldIndex(fieldName);
		Validate.exclusiveBetween(-1, row.getArity(), index, "Field %s not found in row.", fieldName);
		return row.getField(index);
	}
}
