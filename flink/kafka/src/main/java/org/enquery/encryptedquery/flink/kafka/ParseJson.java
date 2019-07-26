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

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.MapFunction;
import org.enquery.encryptedquery.data.DataSchema;
import org.enquery.encryptedquery.flink.streaming.InputRecord;
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseJson implements MapFunction<InputRecord, InputRecord> {
	private static final long serialVersionUID = 1L;

	static final Logger log = LoggerFactory.getLogger(ParseJson.class);

	private final DataSchema dataSchema;
	private transient JSONStringConverter jsonConverter;

	/**
	 * @param rowTypeInfo
	 */
	public ParseJson(DataSchema dataSchema) {
		Validate.notNull(dataSchema);
		this.dataSchema = dataSchema;
	}

	@Override
	public InputRecord map(InputRecord value) throws Exception {
		if (value.eof) return value;

		final boolean debugging = log.isDebugEnabled();

		if (jsonConverter == null) {
			jsonConverter = new JSONStringConverter(dataSchema);
		}

		if (debugging) log.debug("Parsing JSON string: {}", value);
		final Map<String, Object> map = jsonConverter.toStringObjectFlatMap((String) value.rawData);
		if (debugging) log.debug("Converted to map: {}", map.toString());
		value.data = map;
		return value;
	}
}
