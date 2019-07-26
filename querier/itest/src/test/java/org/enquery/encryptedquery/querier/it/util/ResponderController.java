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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponderController {

	private static final Logger log = LoggerFactory.getLogger(ResponderController.class);

	private String responderInstallDir;

	static private ExecutorService threadPool = Executors.newCachedThreadPool();

	public ResponderController(String responderInstallDir) {
		this.responderInstallDir = responderInstallDir;
	}

	public void start() throws Exception {
		runCommand("/start");
	}

	public void stop() throws Exception {
		runCommand("/stop");
	}

	public List<String> client(String... extraArgs) throws Exception {
		return runCommand("/client", extraArgs);
	}

	public List<String> runCommand(String command, String... extraArgs) throws Exception {

		File binDir = new File(responderInstallDir, "bin");

		List<String> arguments = new ArrayList<>();
		arguments.add(binDir.getAbsolutePath() + command);
		if (extraArgs != null) {
			for (String arg : extraArgs) {
				arguments.add(arg);
			}
		}

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.redirectErrorStream(true);
		processBuilder.directory(new File(responderInstallDir));

		log.info("Running Responder command {} ", binDir, arguments);
		Process proc = processBuilder.start();

		// capture and log child process output in separate thread
		ChildProcessOutputCapturer capturer = new ChildProcessOutputCapturer(proc.getInputStream(), log);
		Future<?> f = threadPool.submit(capturer);
		int exitCode = proc.waitFor();

		if (exitCode != 0) {
			throw new RuntimeException(
					"Failed to run Responder with commnad line arguments: '" + command + "'");
		}

		f.get();
		return capturer.getLines();
	}

}
