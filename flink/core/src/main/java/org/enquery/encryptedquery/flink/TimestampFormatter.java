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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/**
 *
 */
public class TimestampFormatter {
	static final String DEFAULT_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss";
	static final ZoneId zoneId = ZoneId.systemDefault();
	static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DEFAULT_FORMAT_STRING).withZone(zoneId);

	public static String format(TemporalAccessor temporal) {
		return dateTimeFormatter.format(temporal);
	}

	public static String format(long temporal) {
		return dateTimeFormatter.format(Instant.ofEpochMilli(temporal));
	}
}
