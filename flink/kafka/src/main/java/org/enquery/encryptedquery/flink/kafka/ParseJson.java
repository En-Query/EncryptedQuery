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
package org.enquery.encryptedquery.flink.kafka;

import java.util.Map;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.types.Row;
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseJson implements MapFunction<String, Row> {
	private static final long serialVersionUID = 1L;

	static final Logger log = LoggerFactory.getLogger(ParseJson.class);

	private final RowTypeInfo rowTypeInfo;

	/**
	 * @param rowTypeInfo
	 */
	public ParseJson(RowTypeInfo rowTypeInfo) {
		this.rowTypeInfo = rowTypeInfo;
	}

	@Override
	public Row map(String value) throws Exception {
		final boolean debugging = log.isDebugEnabled();

		if (debugging) log.debug("Parsing JSON string: {}", value);
		final Map<String, Object> map = JSONStringConverter.toStringObjectFlatMap(value);

		if (debugging) log.debug("Converted to map: {}", map.toString());

		final String[] fieldNames = rowTypeInfo.getFieldNames();
		final int arity = rowTypeInfo.getArity();
		final Row result = new Row(arity);
		for (int i = 0; i < arity; ++i) {
			final String fieldName = fieldNames[i];
			final Object fieldValue = map.get(fieldName);

			if (debugging) {
				log.debug("responder ParseJson - Index:{}, name: '{}', value: {}, value type: {}",
						i,
						fieldName,
						fieldValue,
						(fieldValue != null) ? fieldValue.getClass() : "null value");
			}

			result.setField(i, fieldValue);
		}

		if (debugging) log.debug("Parsed to Row: {}", result);
		return result;
	}
}
