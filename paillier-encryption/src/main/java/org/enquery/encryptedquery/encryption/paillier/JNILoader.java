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
package org.enquery.encryptedquery.encryption.paillier;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Platform;

public class JNILoader {

	private static final Logger log = LoggerFactory.getLogger(JNILoader.class);
	private static final Set<String> loaded = new HashSet<>();

	public static synchronized void load() {
		if (isRunningInOSGiContainer()) {
			loadOSGi();
		} else {
			loadStandAlone();
		}
		if (log.isDebugEnabled()) { log.debug("Finished loading native libraries."); }
	}

	private static void loadOSGi() {
		log.info("Loading native libraries in a OSGi environment.");
		loadOSGI("gmp");
		loadOSGI("responder");
		loadOSGI("querygen");
	}

	private static void loadStandAlone() {
		log.info("Loading native libraries in a non-OSGi environment.");

		String extension = Platform.getOSType() == Platform.MAC ? ".dylib" : ".so";

		Path tmpDir = copyLibs();
		try {
			loadStandAlone(tmpDir, "libgmp" + extension);
			loadStandAlone(tmpDir, "libresponder" + extension);
			loadStandAlone(tmpDir, "libquerygen" + extension);
		} catch (Exception e) {
			if (tmpDir != null) {
				deleteDir(tmpDir);
				tmpDir = null;
			}
			throw new RuntimeException("Error loading native libraries.", e);
		} finally {
			// delete tmp directory if posix
			if (tmpDir != null) {
				if (isPosix()) {
					deleteDir(tmpDir);
				} else {
					tmpDir.toFile().deleteOnExit();
				}
			}
		}
	}

	private static Path copyLibs() {
		Path tmpDir;
		try {
			tmpDir = Files.createTempDirectory("paillier");
			tmpDir.toFile().deleteOnExit();

			copyFromJar("/native/" + Platform.RESOURCE_PREFIX, tmpDir);
			return tmpDir;
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException("Error copying native libraries to a tmp directory.", e);
		}
	}

	private static synchronized void loadStandAlone(Path tmpDir, String library) {
		if (loaded.contains(library)) return;

		if (log.isDebugEnabled()) { log.debug("Loading library '{}'.", library); }
		File tmpFile = tmpDir.resolve(library).toFile();
		System.load(tmpFile.getAbsolutePath());
		if (log.isDebugEnabled()) { log.debug("Loaded native library {}", tmpFile.getAbsolutePath()); }
		loaded.add(library);
	}


	private static void loadOSGI(String library) {
		if (loaded.contains(library)) return;

		if (log.isDebugEnabled()) { log.debug("Loading library '{}' in a OSGi environment.", library); }
		System.loadLibrary(library);
		loaded.add(library);
		if (log.isDebugEnabled()) { log.debug("Loaded library '{}'.", library); }
		return;
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


	private static boolean isRunningInOSGiContainer() {
		try {
			JNILoader.class.getClassLoader().loadClass(org.osgi.framework.FrameworkUtil.class.getName());
		} catch (ClassNotFoundException | java.lang.NoClassDefFoundError e) {
			// if the FrameworkUtil class is not present, then we are not in OSGi environment.
			return false;
		}
		return org.osgi.framework.FrameworkUtil.getBundle(JNILoader.class) != null;
	}


	private static void copyFromJar(String source, final Path target) throws URISyntaxException, IOException {

		if (log.isDebugEnabled()) { log.debug("Copying native libraries from '{}' to '{}'.", source, target); }

		URI uri = JNILoader.class.getResource(source).toURI();
		if (log.isDebugEnabled()) { log.debug("File System URI: {}.", uri); }


		FileSystem fileSystem = null;
		try {
			fileSystem = FileSystems.getFileSystem(uri);
		} catch (FileSystemNotFoundException e) {
			// if not already created, we create it below
		}
		if (fileSystem == null) {
			fileSystem = FileSystems.newFileSystem(
					uri,
					Collections.<String, String>emptyMap());
		}
		Validate.notNull(fileSystem);

		final Path rootJarPath = fileSystem.getPath(source);
		if (log.isDebugEnabled()) { log.debug("Root Jar Path: {}.", rootJarPath); }

		Files.walkFileTree(rootJarPath, new SimpleFileVisitor<Path>() {

			private Path currentTarget;

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Path relative = rootJarPath.relativize(dir);
				if (log.isDebugEnabled()) { log.debug("Directory '{}' , relativized to: '{}'.", dir, relative); }
				currentTarget = Files.createDirectories(target.resolve(relative.toString()));
				if (log.isDebugEnabled()) { log.debug("Created '{}'.", currentTarget); }
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String srcPath = rootJarPath.relativize(file).toString();
				Path destPath = currentTarget.resolve(srcPath);
				if (log.isDebugEnabled()) { log.debug("Copying from '{}' to '{}'.", file, destPath); }
				Files.copy(file, destPath, StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void deleteDir(Path dir) {
		try {
			Files.walk(dir)
					.map(Path::toFile)
					.sorted((o1, o2) -> -o1.compareTo(o2))
					.forEach(File::delete);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
