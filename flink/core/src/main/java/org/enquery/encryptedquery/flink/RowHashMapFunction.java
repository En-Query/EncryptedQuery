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
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.responder.wideskies.common.QueueRecord;
import org.enquery.encryptedquery.responder.wideskies.common.RecordPartitioner;
import org.enquery.encryptedquery.utils.KeyedHash;

public final class RowHashMapFunction implements MapFunction<Row, QueueRecord>, FieldTypes {

	private static final long serialVersionUID = 1L;

	// private static final Logger log = LoggerFactory.getLogger(RowHashMapFunction.class);
	// private final DecimalFormat numFormat = new DecimalFormat("###,###,###,###,###,###");
	// private static final boolean debugLogOn = log.isDebugEnabled();
	// private static final long serialVersionUID = 1L;
	private final Integer selectorFieldIndex;
	private final String selectorFieldType;
	private final RowTypeInfo rowTypeInfo;
	private final boolean embedSelector;
	private final QuerySchema qschema;
	private final String key;
	private int hashBitSize;
	private List<Byte> recordParts;

	private final int dataChunkSize;

	transient private Partitioner partitioner;


	public RowHashMapFunction(Integer selectorFieldIndex,
			String selectorFieldType,
			RowTypeInfo rowTypeInfo,
			boolean embedSelector,
			QuerySchema qschema,
			String key,
			int hashBitSize,
			int dataChunkSize) {
		this.selectorFieldIndex = selectorFieldIndex;
		this.selectorFieldType = selectorFieldType;
		this.key = key;
		this.hashBitSize = hashBitSize;
		this.embedSelector = embedSelector;
		this.qschema = qschema;
		this.rowTypeInfo = rowTypeInfo;
		this.dataChunkSize = dataChunkSize;

		Validate.notNull(rowTypeInfo);
		if (embedSelector) {
			Validate.notNull(qschema.getSelectorField());
		}
	}

	@Override
	public QueueRecord map(Row row) throws Exception {

		if (partitioner == null) {
			partitioner = new Partitioner();
		}

		final Object field = row.getField(selectorFieldIndex);
		// TODO: use asString, or asByteArray?
		final String selectorValue = asString(field, selectorFieldType);
		final Integer rowIndex = KeyedHash.hash(key, hashBitSize, selectorValue);

		Map<String, Object> recordData = new HashMap<>();

		qschema.getElementList()
				.stream()
				.forEach(qse -> recordData.put(qse.getName(), getFieldValue(row, qse.getName())));

		recordParts = RecordPartitioner.partitionRecord(partitioner, qschema, recordData, embedSelector);
		QueueRecord record = new QueueRecord(rowIndex, selectorValue, recordParts);
		record.setHitValPartitions(partitioner.createPartitions(recordParts, dataChunkSize));
		return record;
	}

	private Object getFieldValue(Row row, final String fieldName) {
		final int index = rowTypeInfo.getFieldIndex(fieldName);
		// log.debug("Field {} has index {}", fieldName, index);
		return row.getField(index);
	}
}
