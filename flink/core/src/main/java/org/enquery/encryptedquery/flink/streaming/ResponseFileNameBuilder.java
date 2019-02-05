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

/**
 * Builds response file names (final and inProgress), including parent directory. In progress
 * variation, used to mark that the window was started but not finished yet.
 */
public class ResponseFileNameBuilder {

	private static final String DEFAULT_FORMAT_STRING = "HHmmss";
	private static final ZoneId zoneId = ZoneId.systemDefault();
	private static final DateTimeFormatter fileNameDateTimeFormatter = DateTimeFormatter.ofPattern(DEFAULT_FORMAT_STRING).withZone(zoneId);
	public static final String IN_PROGRESS_SUFFIX = "inProgress";

	public static Path makeFinalFileName(String responseFilePath, long start, long end) throws IOException {
		return makeFinalFileName(Paths.get(responseFilePath), start, end);
	}

	public static Path makeFinalFileName(Path responseFilePath, long start, long end) throws IOException {
		OffsetDateTime startTime = Instant.ofEpochMilli(start).atOffset(ZoneOffset.UTC);
		OffsetDateTime endTime = Instant.ofEpochMilli(end).atOffset(ZoneOffset.UTC);

		String fname = String.format("%s-%s.xml",
				fileNameDateTimeFormatter.format(startTime),
				fileNameDateTimeFormatter.format(endTime));

		return makeParentDir(responseFilePath, endTime).resolve(fname);
	}

	public static Path makeInProgressFileName(String responseFilePath, long start, long end) throws IOException {
		return makeInProgressFileName(Paths.get(responseFilePath), start, end);
	}

	public static Path makeInProgressFileName(Path responseFilePath, long start, long end) throws IOException {
		OffsetDateTime startTime = Instant.ofEpochMilli(start).atOffset(ZoneOffset.UTC);
		OffsetDateTime endTime = Instant.ofEpochMilli(end).atOffset(ZoneOffset.UTC);

		String fname = String.format("%s-%s.xml.%s",
				fileNameDateTimeFormatter.format(startTime),
				fileNameDateTimeFormatter.format(endTime),
				IN_PROGRESS_SUFFIX);

		Path filePath = makeParentDir(responseFilePath, endTime);
		return filePath.resolve(fname);
	}

	private static Path makeParentDir(Path path, OffsetDateTime endTime) throws IOException {
		Path filePath = path.resolve(Integer.toString(endTime.getYear()))
				.resolve(Integer.toString(endTime.getMonthValue()))
				.resolve(Integer.toString(endTime.getDayOfMonth()));
		Files.createDirectories(filePath);
		return filePath;
	}

	public static Path createEmptyInProgressFile(String responseFilePath, long start, long end) throws IOException {
		Path path = ResponseFileNameBuilder
				.makeInProgressFileName(responseFilePath, start, end);

		Files.createDirectories(path.getParent());
		try {
			Files.createFile(path);
		} catch (FileAlreadyExistsException ignore) {
			// ok to ignore, file was previously created
		}
		return path;
	}
}
