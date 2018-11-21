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
package org.enquery.encryptedquery.querier.it.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponderController {

	private static final Logger log = LoggerFactory.getLogger(ResponderController.class);

	private String responderInstallDir;

	public ResponderController(String responderInstallDir) {
		this.responderInstallDir = responderInstallDir;
	}

	public void start() throws IOException, InterruptedException {
		runCommand("./start");
	}

	public void stop() throws IOException, InterruptedException {
		runCommand("./stop");
	}

	public List<String> client(String... extraArgs) throws IOException, InterruptedException {
		return runCommand("./client", extraArgs);
	}

	public List<String> runCommand(String command, String... extraArgs) throws IOException, InterruptedException {
		List<String> arguments = new ArrayList<>();
		arguments.add(command);
		if (extraArgs != null) {
			for (String arg : extraArgs) {
				arguments.add(arg);
			}
		}
		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.redirectErrorStream(true);
		File binDir = new File(responderInstallDir, "bin");
		processBuilder.directory(binDir);

		log.info("Running Responder in dir {} with command {} ", binDir, arguments);
		Process proc = processBuilder.start();

		BufferedReader stdOutReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		BufferedReader stdErrReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
		int exitCode = proc.waitFor();

		String line;
		List<String> result = new ArrayList<>();
		while ((line = stdOutReader.readLine()) != null) {
			result.add(line.trim());
		}

		while ((line = stdErrReader.readLine()) != null) {
			result.add(line.trim());
		}

		log.info(result.toString());

		if (exitCode != 0) {
			throw new RuntimeException(
					"Failed to run Responder with commnad line arguments: '" + command + "'");
		}

		return result;
	}



}
