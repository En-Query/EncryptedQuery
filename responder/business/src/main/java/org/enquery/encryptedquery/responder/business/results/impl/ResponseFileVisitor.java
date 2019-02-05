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
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ResponseFileVisitor extends SimpleFileVisitor<Path> {

	private static final Logger log = LoggerFactory.getLogger(ResponseFileVisitor.class);

	private int level = -1;
	private boolean hasUnexpectedContent;
	private boolean hasErrors;
	private final Predicate<ResponseFileInfo> callback;

	private int year, month, day;

	/**
	 * Callback predicate returns false, if the file could not be processed. If a file cannot be
	 * processed correctly, then it will not be deleted, nor its parent directory.
	 * 
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

		log.info("Entering level {} path '{}'.", level, dir);
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		log.info("Found file '{}'.", file);

		if (!file.getFileName().toString().endsWith("xml")) {
			hasUnexpectedContent = true;
			return FileVisitResult.CONTINUE;
		}

		ResponseFileInfo info = new ResponseFileInfo();
		info.day = day;
		info.month = month;
		info.year = year;
		info.path = file;

		boolean success = callback.test(info);
		if (success) {
			safeDelete(file);
		} else {
			hasErrors = true;
		}
		return FileVisitResult.CONTINUE;
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
		log.info("Leaving level {} path '{}'.", level, dir);

		if (canDeleteDir()) {
			log.info("Will delete path '{}'.", dir);
			safeDelete(dir);
		}

		--level;
		return CONTINUE;
	}

	private boolean canDeleteDir() {
		return !hasUnexpectedContent && !hasErrors;
	}

	private void resetFlags() {
		hasUnexpectedContent = false;
		hasErrors = false;
	}
}
