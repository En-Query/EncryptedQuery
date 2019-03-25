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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 *
 */
public class ISO8601DateParserTest {


	private ZoneId utcZone = ZoneId.of("UTC");

	@Test(expected = DateTimeParseException.class)
	public void testInValid() {
		// missing time zone
		ISO8601DateParser.getDate("2018-01-01T15:56:31");
	}

	@Test
	public void testValid() {
		Date date = ISO8601DateParser.getDate("2018-01-01T15:56:31.410Z");
		assertNotNull(date);
		Date expected = Date.from(ZonedDateTime.of(2018, 1, 1, 15, 56, 31, (int) TimeUnit.MILLISECONDS.toNanos(410), utcZone).toInstant());
		assertEquals(expected.getTime(), date.getTime());
		assertEquals("2018-01-01T15:56:31.410Z", ISO8601DateParser.fromLongDate(date.getTime()));

		date = ISO8601DateParser.getDate("2018-01-01T15:56:31.41Z");
		assertNotNull(date);
		expected = Date.from(ZonedDateTime.of(2018, 1, 1, 15, 56, 31, (int) TimeUnit.MILLISECONDS.toNanos(410), utcZone).toInstant());
		assertEquals(expected.getTime(), date.getTime());
		assertEquals("2018-01-01T15:56:31.410Z", ISO8601DateParser.fromLongDate(date.getTime()));

		date = ISO8601DateParser.getDate("2018-01-01T15:56:31Z");
		assertNotNull(date);
		expected = Date.from(ZonedDateTime.of(2018, 1, 1, 15, 56, 31, 0, utcZone).toInstant());
		assertEquals(expected.getTime(), date.getTime());
		assertEquals("2018-01-01T15:56:31Z", ISO8601DateParser.fromLongDate(date.getTime()));


		date = ISO8601DateParser.getDate("2018-01-01T15:56Z");
		assertNotNull(date);
		expected = Date.from(ZonedDateTime.of(2018, 1, 1, 15, 56, 0, 0, utcZone).toInstant());
		assertEquals(expected.getTime(), date.getTime());
		assertEquals("2018-01-01T15:56:00Z", ISO8601DateParser.fromLongDate(date.getTime()));

		date = ISO8601DateParser.getDate("2018-01-01T15:56:31+00:00");
		assertNotNull(date);
		expected = Date.from(ZonedDateTime.of(2018, 1, 1, 15, 56, 31, 0, utcZone).toInstant());
		assertEquals(expected.getTime(), date.getTime());
		assertEquals("2018-01-01T15:56:31Z", ISO8601DateParser.fromLongDate(date.getTime()));

		date = ISO8601DateParser.getDate("2018-01-01T15:56:31+0000");
		assertNotNull(date);
		expected = Date.from(ZonedDateTime.of(2018, 1, 1, 15, 56, 31, 0, utcZone).toInstant());
		assertEquals(expected.getTime(), date.getTime());
		assertEquals("2018-01-01T15:56:31Z", ISO8601DateParser.fromLongDate(date.getTime()));

		date = ISO8601DateParser.getDate("2018-01-01T15:56:31+00");
		assertNotNull(date);
		expected = Date.from(ZonedDateTime.of(2018, 1, 1, 15, 56, 31, 0, utcZone).toInstant());
		assertEquals(expected.getTime(), date.getTime());
		assertEquals("2018-01-01T15:56:31Z", ISO8601DateParser.fromLongDate(date.getTime()));

		date = ISO8601DateParser.getDate("2018-01-01T15:56:31+05");
		assertNotNull(date);
		expected = Date.from(ZonedDateTime.of(2018, 1, 1, 10, 56, 31, 0, utcZone).toInstant());
		assertEquals(expected.getTime(), date.getTime());
		assertEquals("2018-01-01T10:56:31Z", ISO8601DateParser.fromLongDate(date.getTime()));

		date = ISO8601DateParser.getDate("2018-01-01T15:56:31-05");
		assertNotNull(date);
		expected = Date.from(ZonedDateTime.of(2018, 1, 1, 20, 56, 31, 0, utcZone).toInstant());
		assertEquals(expected.getTime(), date.getTime());
		assertEquals("2018-01-01T20:56:31Z", ISO8601DateParser.fromLongDate(date.getTime()));

		date = ISO8601DateParser.getDate("2018-01-01T15:00:31+01:30");
		assertNotNull(date);
		expected = Date.from(ZonedDateTime.of(2018, 1, 1, 13, 30, 31, 0, utcZone).toInstant());
		assertEquals(expected.getTime(), date.getTime());
		assertEquals("2018-01-01T13:30:31Z", ISO8601DateParser.fromLongDate(date.getTime()));
	}

}
