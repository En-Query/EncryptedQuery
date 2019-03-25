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
package org.enquery.encryptedquery.utils;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Date;
import java.util.TimeZone;
/**
 * Class to parse a date in ISO86091 format
 * 
 */

/**
 * Class to parse a date in ISO86091 format
 * 
 */
public class ISO8601DateParser {
	private static TimeZone utc = TimeZone.getTimeZone("UTC");

	private static final DateTimeFormatter inputFormat = new DateTimeFormatterBuilder()
			// date/time
			.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
			// offset (hh:mm - "+00:00" when it's zero)
			.optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
			// offset (hhmm - "+0000" when it's zero)
			.optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
			// offset (hh - "+00" when it's zero)
			.optionalStart().appendOffset("+HH", "+00").optionalEnd()
			// offset (pattern "X" uses "Z" for zero offset)
			.optionalStart().appendPattern("X").optionalEnd()
			// create formatter
			.toFormatter();

	public static Date getDate(String isoDate) {
		return Date.from(ZonedDateTime.parse(isoDate, inputFormat).toInstant());
	}

	public static long getLongDate(String isoDate) {
		return getDate(isoDate).getTime();
	}

	public static String fromLongDate(long dateLongFormat) {
		Instant instant = Instant.ofEpochMilli(dateLongFormat);
		ZonedDateTime zonedDateTime = instant.atZone(utc.toZoneId());
		return zonedDateTime.format(DateTimeFormatter.ISO_INSTANT);
	}
}

