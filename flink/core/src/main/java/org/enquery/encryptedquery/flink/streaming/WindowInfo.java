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

import java.text.MessageFormat;

import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.enquery.encryptedquery.flink.TimestampFormatter;

/**
 *
 */
public class WindowInfo {
	public static String windowInfo(TimeWindow window) {
		return windowInfo(window.getStart(), window.maxTimestamp());
	}

	public static String windowInfo(final long start, final long end) {
		final String windowInfo = MessageFormat.format(
				"[from {0} to {1}]",
				TimestampFormatter.format(start),
				TimestampFormatter.format(end));
		return windowInfo;
	}
}
