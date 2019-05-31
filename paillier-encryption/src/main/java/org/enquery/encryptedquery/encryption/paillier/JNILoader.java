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
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Platform;

public class JNILoader {

	private static final String SOURCE = "/native/" + Platform.RESOURCE_PREFIX;
	private static final String LIBRARY_FILE_EXT = Platform.getOSType() == Platform.MAC ? ".dylib" : ".so";

	private static final Logger log = LoggerFactory.getLogger(JNILoader.class);
	private static final Set<String> loaded = new HashSet<>();

	public static synchronized void load(List<String> neededLibs) {
		Validate.notNull(neededLibs);
		if (neededLibs.isEmpty()) return;

		if (isRunningInOSGiContainer()) {
			loadOSGi(neededLibs);
		} else {
			loadStandAlone(neededLibs);
		}
		if (log.isDebugEnabled()) {
			log.debug("Finished loading native libraries.");
		}
	}

	private static void loadOSGi(List<String> neededLibs) {
		log.info("Loading native libraries in a OSGi environment.");
		for (String lib : neededLibs) {
			loadOSGI(lib);
		}
	}

	private static void loadStandAlone(List<String> neededLibs) {
		log.info("Loading native libraries in a non-OSGi environment.");

		Path tmpDir = copyLibs();
		try {
			for (String lib : neededLibs) {
				loadStandAlone(tmpDir, lib);
			}
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

			copyFromJar(tmpDir);
			return tmpDir;
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException("Error copying native libraries to a tmp directory.", e);
		}
	}

	private static synchronized void loadStandAlone(Path tmpDir, String libname) {
		if (loaded.contains(libname)) return;

		if (log.isDebugEnabled()) {
			log.debug("Loading library '{}'.", libname);
		}

		final String resourceName = String.format("lib%s%s",
				libname,
				LIBRARY_FILE_EXT);

		File tmpFile = tmpDir.resolve(resourceName).toFile();
		System.load(tmpFile.getAbsolutePath());
		if (log.isDebugEnabled()) {
			log.debug("Loaded native library {}", tmpFile.getAbsolutePath());
		}
		loaded.add(libname);
	}


	private static void loadOSGI(String library) {
		if (loaded.contains(library)) return;

		if (log.isDebugEnabled()) {
			log.debug("Loading library '{}' in a OSGi environment.", library);
		}
		System.loadLibrary(library);
		loaded.add(library);
		if (log.isDebugEnabled()) {
			log.debug("Loaded library '{}'.", library);
		}
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


	private static void copyFromJar(final Path target) throws URISyntaxException, IOException {

		if (log.isDebugEnabled()) {
			log.debug("Copying native libraries to '{}'.", target);
		}

		URI uri = JNILoader.class.getResource(SOURCE).toURI();
		if (log.isDebugEnabled()) {
			log.debug("URI: {}.", uri);
			log.debug("Scheme: {}.", uri.getScheme());
			log.debug("Path: {}.", uri.getPath());
		}

		FileSystem fileSystem = null;
		try {
			Path rootJarPath = null;
			try {
				rootJarPath = Paths.get(uri);
			} catch (FileSystemNotFoundException e) {
				// if not already created, we create it below
				final Map<String, ?> env = Collections.emptyMap();
				fileSystem = FileSystems.newFileSystem(uri, env);
				rootJarPath = fileSystem.provider().getPath(uri);
			}

			if (log.isDebugEnabled()) {
				log.debug("Root Jar Path: {}.", rootJarPath);
			}

			final Path path = rootJarPath;
			Files.walkFileTree(rootJarPath, new SimpleFileVisitor<Path>() {

				private Path currentTarget;

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Path relative = path.relativize(dir);
					if (log.isDebugEnabled()) {
						log.debug("Directory '{}' , relativized to: '{}'.", dir, relative);
					}
					currentTarget = Files.createDirectories(target.resolve(relative.toString()));
					if (log.isDebugEnabled()) {
						log.debug("Created '{}'.", currentTarget);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Path destPath = currentTarget.resolve(file.getFileName().toString());
					if (log.isDebugEnabled()) {
						log.debug("Copying from '{}' to '{}'.", file, destPath);
					}
					Files.copy(file, destPath, StandardCopyOption.REPLACE_EXISTING);
					return FileVisitResult.CONTINUE;
				}
			});
		} finally {
			if (fileSystem != null) fileSystem.close();
		}
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
