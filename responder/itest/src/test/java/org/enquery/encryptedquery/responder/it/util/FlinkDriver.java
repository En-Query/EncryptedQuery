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

import static org.ops4j.pax.exam.CoreOptions.propagateSystemProperty;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class FlinkDriver {

	public static final Logger log = LoggerFactory.getLogger(FlinkDriver.class);

	public void init() throws Exception {
		configureFlink();
		runHistoryServer();
		runFlink();
	}


	public Option[] configuration() {
		return CoreOptions.options(
				// propagate system properties
				propagateSystemProperty("flink.install.dir"),
				propagateSystemProperty("flink.jdbc.app"));
	}

	public void cleanup() throws IOException, InterruptedException {
		stopFlink();
		stopHistoryServer();
	}

	private void stopFlink() throws IOException, InterruptedException {
		List<String> arguments = new ArrayList<>();
		Path programPath = Paths.get(System.getProperty("flink.install.dir"), "bin", "stop-cluster.sh");
		arguments.add(programPath.toString());

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.directory(new File(System.getProperty("flink.install.dir")));
		processBuilder.redirectErrorStream(true);

		Process p = processBuilder.start();
		p.waitFor();
	}

	@SuppressWarnings("unchecked")
	private void configureFlink() throws FileNotFoundException, IOException {

		DumperOptions options = new DumperOptions();
		options.setIndent(2);
		options.setPrettyFlow(true);
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

		Yaml yaml = new Yaml(options);


		Map<String, Object> entries = null;
		String installDir = System.getProperty("flink.install.dir");

		Path configFile = Paths.get(installDir, "conf", "flink-conf.yaml");
		try (InputStream is = new FileInputStream(configFile.toFile())) {
			entries = (Map<String, Object>) yaml.load(is);
		}

		// web dashboard, disable
		entries.put("rest.port", 8095);

		// Job History
		Path dataDir = Paths.get(installDir, "data");
		Files.createDirectories(dataDir);

		entries.put("jobmanager.archive.fs.dir", dataDir.toUri().toString());
		entries.put("historyserver.web.address", "localhost");
		entries.put("historyserver.web.port", 8096);
		entries.put("historyserver.archive.fs.dir", dataDir.toUri().toString());
		entries.put("historyserver.archive.fs.refresh-interval", "3000");

		try (FileWriter fw = new FileWriter(configFile.toFile())) {
			fw.write(yaml.dump(entries));
		}

		// copy flink log4j
		Path dest = Paths.get(installDir, "conf", "log4j.properties");
		try (InputStream in = this.getClass().getResourceAsStream("/flink/log4j.properties")) {
			Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void runFlink() throws IOException, InterruptedException {
		List<String> arguments = new ArrayList<>();
		Path programPath = Paths.get(System.getProperty("flink.install.dir"), "bin", "start-cluster.sh");
		arguments.add(programPath.toString());

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.directory(new File(System.getProperty("flink.install.dir")));
		processBuilder.redirectErrorStream(true);

		log.info("Launch flink with arguments: " + arguments);
		Process p = processBuilder.start();
		p.waitFor();
	}


	/**
	 * @throws IOException
	 * @throws InterruptedException
	 * 
	 */
	private void runHistoryServer() throws IOException, InterruptedException {
		List<String> arguments = new ArrayList<>();
		Path programPath = Paths.get(System.getProperty("flink.install.dir"), "bin", "historyserver.sh");
		arguments.add(programPath.toString());
		arguments.add("start");

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.directory(new File(System.getProperty("flink.install.dir")));

		Process p = processBuilder.start();
		p.waitFor();
	}

	/**
	 * @throws IOException
	 * @throws InterruptedException
	 * 
	 */
	private void stopHistoryServer() throws IOException, InterruptedException {
		List<String> arguments = new ArrayList<>();
		Path programPath = Paths.get(System.getProperty("flink.install.dir"), "bin", "historyserver.sh");
		arguments.add(programPath.toString());
		arguments.add("stop");

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.directory(new File(System.getProperty("flink.install.dir")));

		Process p = processBuilder.start();
		p.waitFor();
	}

}
