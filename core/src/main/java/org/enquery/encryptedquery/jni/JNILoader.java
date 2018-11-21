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
package org.enquery.encryptedquery.jni;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JNILoader {

	private static final Logger log = LoggerFactory.getLogger(JNILoader.class);
	private static final Set<String> loaded = new HashSet<>();

	public static synchronized void loadLibrary(String library) {
		Validate.notBlank(library, "'library' cannot be blank");
		File tmpFile = null;
		try {
			log.info("Loading native library {}", library);
			if (loaded.contains(library)) return;

			Path source = Paths.get(library);
			Path temp = Files.createTempFile(source.getFileName().toString(), ".so");

			tmpFile = temp.toFile();
			tmpFile.deleteOnExit();

			try (OutputStream out = new FileOutputStream(tmpFile)) {
				Files.copy(source, out);
			}

			log.info("Copied {} to {}", library, tmpFile);
			System.load(tmpFile.getAbsolutePath());
			log.info("Loaded native library {}", tmpFile.getAbsolutePath());

			loaded.add(library);
		} catch (Exception e) {
			if (tmpFile != null) {
				tmpFile.delete();
				tmpFile = null;
			}
			throw new RuntimeException("Error loading native library: " + library, e);
		} finally {
			// delete tmp file
			if (tmpFile != null) {
				if (isPosix()) {
					tmpFile.delete();
				} else {
					tmpFile.deleteOnExit();
				}
			}
		}
	}

	public static synchronized void loadLibraries(String libs) {
		Validate.notBlank(libs);
		libs = libs.trim();
		String[] parts = libs.split(",");
		for (String p : parts) {
			loadLibrary(p);
		}
	}

	private static boolean isPosix() {
		try {
			return FileSystems.getDefault()
					.supportedFileAttributeViews()
					.contains("posix");
		} catch (FileSystemNotFoundException
				| ProviderNotFoundException
				| SecurityException e) {
			return false;
		}
	}

}
