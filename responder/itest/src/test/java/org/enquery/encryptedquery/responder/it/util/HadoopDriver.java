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
package org.enquery.encryptedquery.responder.it.util;

import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.CoreOptions.propagateSystemProperty;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HadoopDriver {

	public static final Logger log = LoggerFactory.getLogger(HadoopDriver.class);
	private static final String SOURCE = "/hadoop";
	private ExecutorService threadPool = Executors.newCachedThreadPool();

	public void init() throws Exception {

		FileUtils.deleteQuietly(Paths.get(System.getProperty("hadoop.install.dir"), "tmp").toFile());

		run("bin/hdfs", "namenode", "-format", "-force", "-nonInteractive");
		run("sbin/start-dfs.sh");
		run("bin/hdfs", "dfs", "-mkdir", "-p", "/user/enquery/data");
		// run("bin/hdfs", "dfs", "-chown", "-R", "hadoop", "/user/enquery");
		run("sbin/start-yarn.sh");
	}

	public void cleanup() throws IOException, InterruptedException {
		run("sbin/stop-yarn.sh");
		run("sbin/stop-dfs.sh");
	}

	public Option[] configuration() throws URISyntaxException, IOException {
		// copy the hadoop configuration files
		copyFromJar(Paths.get(System.getProperty("hadoop.install.dir")));
		return CoreOptions.options(
				// propagate system properties
				propagateSystemProperty("hadoop.install.dir"),
				propagateSystemProperty("hadoop.mr.app"));
	}


	private static void copyFromJar(final Path target) throws URISyntaxException, IOException {
		Validate.notNull(target);

		if (log.isDebugEnabled()) {
			log.debug("Copying resources to '{}'.", target);
		}

		URL resource = HadoopDriver.class.getResource(SOURCE);
		Validate.notNull(resource);

		URI uri = resource.toURI();
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

			final Path rootSrcPath = rootJarPath;
			Files.walkFileTree(rootJarPath, new SimpleFileVisitor<Path>() {

				private Path currentTarget;

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Path relative = rootSrcPath.relativize(dir);
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
					Path destPath = currentTarget.resolve(file.getFileName());
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


	private void run(String program, String... args) throws InterruptedException, IOException {
		List<String> arguments = new ArrayList<>();
		String hadoopDir = System.getProperty("hadoop.install.dir");
		Path programPath = Paths.get(hadoopDir, program);
		arguments.add(programPath.toString());
		for (String a : args) {
			arguments.add(a);
		}

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.directory(new File(hadoopDir));
		processBuilder.redirectErrorStream(true);

		log.info("Launch with arguments: " + arguments);
		Process p = processBuilder.start();
		// capture and log child process output in separate thread
		threadPool.submit(new ChildProcessLogger(p.getInputStream(), log));
		p.waitFor(5, TimeUnit.MINUTES);
		assertEquals(0, p.exitValue());
	}

	/**
	 * @param property
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void copyLocalFileToHDFS(String sourceFileName, String destDir) throws InterruptedException, IOException {
		run("bin/hdfs", "dfs", "-put", sourceFileName, destDir);
	}


}
