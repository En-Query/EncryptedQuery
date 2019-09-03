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
package org.enquery.encryptedquery.querier.it.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.enquery.encryptedquery.querier.data.entity.json.ResultResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BaseRestServiceWithFlinkRunnerItest extends BaseRestServiceWithBookDataSourceItest {


	private Process flinkProcess;

	@Override
	@Before
	public void init() throws Exception {
		log.info("Initializing");
		super.init();
		configureFlink();
		runFlink();
		log.info("Finished Initializing");
	}

	@After
	public void cleanup() throws IOException, InterruptedException {
		if (flinkProcess == null) return;

		List<String> arguments = new ArrayList<>();
		Path programPath = Paths.get(System.getProperty("flink.install.dir"), "bin", "stop-cluster.sh");
		arguments.add(programPath.toString());

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		processBuilder.directory(new File(System.getProperty("flink.install.dir")));
		processBuilder.redirectErrorStream(true);

		flinkProcess = processBuilder.start();
		flinkProcess.waitFor();
	}

	@SuppressWarnings("unchecked")
	private void configureFlink() throws FileNotFoundException, IOException {

		DumperOptions options = new DumperOptions();
		options.setIndent(2);
		options.setPrettyFlow(true);
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

		Yaml yaml = new Yaml(options);


		Map<String, Object> entries = null;
		Path file = Paths.get(System.getProperty("flink.install.dir"), "conf", "flink-conf.yaml");
		try (InputStream is = new FileInputStream(file.toFile())) {
			entries = (Map<String, Object>) yaml.load(is);
		}

		entries.put("rest.port", 8095);
		entries.put("taskmanager.numberOfTaskSlots", 2);
		entries.put("parallelism.default", 2);
		// entries.put("env.java.opts", "-Djava.library.path=" + "/Users/asoto/Downloads/");//
		// System.getProperty("native.libs.install.dir"));

		try (FileWriter fw = new FileWriter(file.toFile())) {
			fw.write(yaml.dump(entries));
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
		flinkProcess = processBuilder.start();
		flinkProcess.waitFor();
	}

	protected ScheduleResponse postSchedule() throws Exception {
		return postSchedule("Flink-JDBC-Derby-Books");
	}

	protected ResultResponse postScheduleAndWaitForResult() throws Exception {
		return postScheduleAndWaitForResult(null);
	}

	protected ResultResponse postScheduleAndWaitForResult(String filterExp) throws Exception {
		return postScheduleAndWaitForResult("Flink-JDBC-Derby-Books", filterExp);
	}

	protected Retrieval submitQueryAndRetrieveResult() throws Exception {
		return super.submitQueryAndRetrieveResult("Flink-JDBC-Derby-Books");
	}

	@Override
	protected Retrieval submitQueryAndRetrieveResult(String filterExpression) throws Exception {
		return super.submitQueryAndRetrieveResult("Flink-JDBC-Derby-Books", filterExpression);
	}
}
