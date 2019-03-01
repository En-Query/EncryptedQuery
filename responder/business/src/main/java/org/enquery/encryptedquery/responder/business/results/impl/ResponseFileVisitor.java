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
package org.enquery.encryptedquery.responder.business.results.impl;

import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ResponseFileVisitor extends SimpleFileVisitor<Path> {

	private static final Logger log = LoggerFactory.getLogger(ResponseFileVisitor.class);

	private static final long MINIMUM_FILE_AGE_IN_SECONDS = 5;

	private final ZoneId zoneId = ZoneId.of("UTC");
	private int level = -1;
	private boolean hasUnexpectedContent;
	private boolean hasErrors;
	private boolean hasTooYoungFiles;
	private final Predicate<ResponseFileInfo> callback;

	private int year, month, day;

	/**
	 * Callback predicate returns false, if the file could not be processed. If a file cannot be
	 * processed correctly, then it will not be deleted, nor its parent directory.
	 */
	public ResponseFileVisitor(Predicate<ResponseFileInfo> callback) {
		this.callback = callback;
	}

	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
		resetFlags();

		if (level >= 3) {
			log.warn("Too many levels, skipping subtree '{}'", dir);
			hasUnexpectedContent = true;
			return FileVisitResult.SKIP_SUBTREE;
		}

		if (level >= 0) {
			Integer dirNameAsInt = null;
			try {
				dirNameAsInt = Integer.valueOf(dir.getFileName().toString());
			} catch (NumberFormatException e) {
				log.warn("Directory name is not valid. Skipping '{}'", dir);
				hasUnexpectedContent = true;
				return FileVisitResult.SKIP_SUBTREE;
			}
			if (level == 0)
				year = dirNameAsInt;
			else if (level == 1)
				month = dirNameAsInt;
			else if (level == 2) day = dirNameAsInt;
		}
		++level;

		if (log.isDebugEnabled()) log.debug("Entering level {} path '{}'.", level, dir);
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		final boolean debugging = log.isDebugEnabled();
		if (debugging) log.debug("Found file '{}'.", file);

		final String fileName = file.getFileName().toString();
		if (!fileName.endsWith(".xml")) {
			if (debugging) log.debug("File '{}' is not XML.", file.getFileName());
			hasUnexpectedContent = true;
			return FileVisitResult.CONTINUE;
		}

		if (fileName.length() != "HHmmss-HHmmss.xml".length()) {
			if (debugging) log.debug("File '{}' is not valid name.", file.getFileName());
			hasUnexpectedContent = true;
			return FileVisitResult.CONTINUE;
		}

		final FileTime lastModifiedTime = Files.getLastModifiedTime(file);
		long age = Math.abs(ChronoUnit.SECONDS.between(lastModifiedTime.toInstant(), Instant.now()));
		if (age < MINIMUM_FILE_AGE_IN_SECONDS) {
			if (debugging) log.debug("File '{}' is too new.", file.getFileName());
			hasTooYoungFiles = true;
			return FileVisitResult.CONTINUE;
		}

		ResponseFileInfo info = new ResponseFileInfo();
		info.day = day;
		info.month = month;
		info.year = year;
		info.path = file;

		parseFileStartAndEndTimeStamps(info, file);


		boolean success = callback.test(info);
		if (success) {
			safeDelete(file);
		} else {
			hasErrors = true;
		}
		return FileVisitResult.CONTINUE;
	}

	/**
	 * @param info
	 * @param file
	 */
	private void parseFileStartAndEndTimeStamps(ResponseFileInfo info, Path file) {
		final String name = file.getFileName().toString();

		int[] index = {0};

		Instant start = parseTimeStamp(info.year, info.month, info.day, name, index);

		// skip the hyphen
		++index[0];

		Instant end = parseTimeStamp(info.year, info.month, info.day, name, index);

		// if the end timestamp is earlier, that means it ended the next day
		if (end.isBefore(start)) {
			end = end.plus(Duration.ofDays(1));
		}

		info.startTime = start;
		info.endTime = end;

		if (log.isDebugEnabled()) {
			log.debug("File '{}' start='{}', end='{}'.",
					name,
					start,
					end);
		}
	}

	private Instant parseTimeStamp(int year, int month, int day, String name, int[] index) {
		int hour = Integer.parseUnsignedInt(name.substring(index[0], index[0] + 2));
		index[0] += 2;
		int minute = Integer.parseUnsignedInt(name.substring(index[0], index[0] + 2));
		index[0] += 2;
		int sec = Integer.parseUnsignedInt(name.substring(index[0], index[0] + 2));
		index[0] += 2;

		return ZonedDateTime.of(year,
				month,
				day,
				hour,
				minute,
				sec,
				0,
				zoneId).toInstant();
	}

	private void safeDelete(Path file) throws IOException {
		try {
			Files.delete(file);
		} catch (NoSuchFileException | DirectoryNotEmptyException ignore) {
			// ok if the file not there
		}
	}

	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
		log.warn("Error '{}' accessing file '{}'. Will ignore.", exc.getMessage(), file);
		hasErrors = true;
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
		if (log.isDebugEnabled()) log.debug("Leaving level {} path '{}'.", level, dir);

		if (canDeleteDir()) {
			log.info("Will delete path '{}'.", dir);
			safeDelete(dir);
		}

		--level;
		return CONTINUE;
	}

	private boolean canDeleteDir() {
		return !hasUnexpectedContent && !hasErrors && !hasTooYoungFiles;
	}

	private void resetFlags() {
		hasUnexpectedContent = false;
		hasErrors = false;
		hasTooYoungFiles = false;
	}
}
