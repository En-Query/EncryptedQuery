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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAccessor;
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
			// just the date with no time
			.append(DateTimeFormatter.ISO_LOCAL_DATE)
			.optionalStart().appendLiteral('T').append(DateTimeFormatter.ISO_LOCAL_TIME).optionalEnd()
			// date/time
			// .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
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

	public static Date getDate() {
		return Date.from(Clock.systemUTC().instant());
	}

	public static Instant getInstant(String isoDate) {
		TemporalAccessor dt = inputFormat.parseBest(isoDate, ZonedDateTime::from, LocalDateTime::from, LocalDate::from);
		if (dt instanceof ZonedDateTime) {
			return ((ZonedDateTime) dt).toInstant();
		} else if (dt instanceof LocalDateTime) {
			return ((LocalDateTime) dt).toInstant(ZoneOffset.UTC);
		}
		return ((LocalDate) dt).atStartOfDay(utc.toZoneId()).toInstant();
	}

	public static Date getDate(String isoDate) {
		return Date.from(getInstant(isoDate));
	}

	public static long getLongDate(String isoDate) {
		return getInstant(isoDate).toEpochMilli();
	}

	public static String fromLongDate(long dateLong) {
		return fromInstant(Instant.ofEpochMilli(dateLong));
	}

	public static String fromInstant(Instant instant) {
		ZonedDateTime zonedDateTime = instant.atZone(utc.toZoneId());
		return zonedDateTime.format(DateTimeFormatter.ISO_INSTANT);
	}

}

