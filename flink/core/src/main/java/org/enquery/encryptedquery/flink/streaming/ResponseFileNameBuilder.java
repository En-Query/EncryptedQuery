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

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds response file names (final and inProgress), including parent directory. In progress
 * variation, used to mark that the window was started but not finished yet.
 */
public class ResponseFileNameBuilder {

	private static final Logger log = LoggerFactory.getLogger(ResponseFileNameBuilder.class);

	private static final String DEFAULT_FORMAT_STRING = "HHmmss";
	private static final ZoneId zoneId = ZoneId.of("UTC");
	private static final DateTimeFormatter fileNameDateTimeFormatter = DateTimeFormatter.ofPattern(DEFAULT_FORMAT_STRING).withZone(zoneId);
	public static final String IN_PROGRESS_SUFFIX = "xml.inProgress";
	public static final String WINDOW_COMPLETE_SUFFIX = "xml.windowComplete";
	public static final String FINAL_SUFFIX = "xml";

	public static Path makeFinalFileName(String responseFilePath, long start, long end) throws IOException {
		return makeFinalFileName(Paths.get(responseFilePath), start, end);
	}

	public static Path makeFinalFileName(Path responseFilePath, long start, long end) throws IOException {
		return makeFileName(responseFilePath, start, end, FINAL_SUFFIX);
	}

	public static Path makeInProgressFileName(String responseFilePath, long start, long end) throws IOException {
		return makeInProgressFileName(Paths.get(responseFilePath), start, end);
	}

	public static Path makeInProgressFileName(Path responseFilePath, long start, long end) throws IOException {
		return makeFileName(responseFilePath, start, end, IN_PROGRESS_SUFFIX);
	}

	public static boolean createEmptyInProgressFile(String responseFilePath, long start, long end) throws IOException {
		return createFile(makeInProgressFileName(responseFilePath, start, end));
	}

	private static boolean createFile(Path file) throws IOException {
		boolean result = true;
		try {
			Files.createFile(file);
		} catch (FileAlreadyExistsException ignore) {
			// ok to ignore, file was previously created
			result = false;
		}

		if (log.isDebugEnabled() && result) {
			log.debug("Created file: {}", file);
		}
		return result;
	}


	private static Path makeFileName(Path responseFilePath, long start, long end, String suffix) throws IOException {
		final OffsetDateTime startTime = Instant.ofEpochMilli(start).atOffset(ZoneOffset.UTC);
		final OffsetDateTime endTime = Instant.ofEpochMilli(end).atOffset(ZoneOffset.UTC);

		final String fname = String.format("%s-%s.%s",
				fileNameDateTimeFormatter.format(startTime),
				fileNameDateTimeFormatter.format(endTime),
				suffix);

		return makeParentDir(responseFilePath, endTime).resolve(fname);
	}

	private static Path makeParentDir(Path path, OffsetDateTime endTime) throws IOException {
		Path filePath = path.resolve(Integer.toString(endTime.getYear()))
				.resolve(Integer.toString(endTime.getMonthValue()))
				.resolve(Integer.toString(endTime.getDayOfMonth()));
		Files.createDirectories(filePath);
		return filePath;
	}

	/**
	 * @param responseFilePath
	 * @param start
	 * @param maxTimestamp
	 * @return
	 * @throws IOException
	 */
	// public static boolean createWindowCompleteFile(Path responseFilePath, long start, long end)
	// throws IOException {
	// return createFile(makeWindowCompleteFileName(responseFilePath, start, end));
	// }

	/**
	 * @param responseFilePath
	 * @param start
	 * @param end
	 * @return
	 * @throws IOException
	 */
	// public static Path makeWindowCompleteFileName(Path responseFilePath, long start, long end)
	// throws IOException {
	// return makeFileName(responseFilePath, start, end, WINDOW_COMPLETE_SUFFIX);
	// }

	public static String makeBaseFileName(long start, long end) {
		final OffsetDateTime startTime = Instant.ofEpochMilli(start).atOffset(ZoneOffset.UTC);
		final OffsetDateTime endTime = Instant.ofEpochMilli(end).atOffset(ZoneOffset.UTC);

		return String.format("%s-%s",
				fileNameDateTimeFormatter.format(startTime),
				fileNameDateTimeFormatter.format(endTime));
	}
}
