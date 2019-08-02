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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.karaf.shell.api.console.SessionFactory;
import org.enquery.encryptedquery.querier.data.entity.ScheduleStatus;
import org.enquery.encryptedquery.querier.data.entity.json.OfflineExecutionExportRequest;
import org.enquery.encryptedquery.querier.data.entity.json.Result;
import org.enquery.encryptedquery.querier.data.entity.json.ResultCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleResponse;
import org.enquery.encryptedquery.querier.it.util.KarafController;
import org.enquery.encryptedquery.xml.schema.ExecutionResources;
import org.enquery.encryptedquery.xml.transformation.ExecutionTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResultExportReader;
import org.enquery.encryptedquery.xml.transformation.ResultReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;


@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class OfflineResultsImportRestServiceIT extends BaseRestServiceWithStandaloneRunnerItest {

	/**
	 * 
	 */
	private static final String SSH_PREFIX = "ssh:ssh -l karaf -P karaf -p 8102 -q localhost ";
	@Inject
	protected SessionFactory sessionFactory;
	private KarafController karaf;
	private ScheduleResponse schedule;
	private ScheduleResponse schedule2;

	@Override
	@Before
	public void init() throws Exception {
		super.init();
		karaf = new KarafController(sessionFactory);
		File home = new File(System.getProperty("user.home"));
		FileUtils.deleteDirectory(new File(home, ".sshkaraf"));
		configureOfflineMode(true);
	}

	@After
	public void cleanup() throws Exception {
		configureOfflineMode(false);
	}


	@Test
	public void uploadMultipart() throws Exception {
		Path resultExportFile = createResultExportXML();

		// Encode the file as a multipart entity…
		MultipartEntityBuilder entity =
				MultipartEntityBuilder.create().setMode(HttpMultipartMode.STRICT);
		entity.addBinaryBody("file", resultExportFile.toFile(), ContentType.create("application/xml"),
				resultExportFile.getFileName().toString());

		upload("/querier/api/rest/offline/results", entity.build());
		validate(resultExportFile);
	}

	@Test
	public void uploadViaCommand() throws Exception {
		Path resultExportFile = createResultExportXML();

		// Encode the file as a multipart entity…
		MultipartEntityBuilder entity =
				MultipartEntityBuilder.create().setMode(HttpMultipartMode.STRICT);
		entity.addBinaryBody("file", resultExportFile.toFile(), ContentType.create("application/xml"),
				resultExportFile.getFileName().toString());

		karaf.executeCommand("result:import -i " + resultExportFile.toAbsolutePath());


		validate(resultExportFile);
	}


	private void validate(Path resultExportFile) throws IOException, XMLStreamException {

		schedule = retrieveSchedule(schedule.getData().getSelfUri());

		Date windowStart = null;
		Date windowEnd = null;
		// String executionUuid = null;

		ExecutorService threadPool = Executors.newCachedThreadPool();
		try (ResultExportReader reader = new ResultExportReader(threadPool);
				InputStream inputStream = Files.newInputStream(resultExportFile)) {

			reader.parse(inputStream);
			assertTrue(reader.hasNextItem());
			reader.nextItem();
			assertTrue(reader.hasNextResult());

			try (ResultReader result = reader.nextResult();
					InputStream responseInputStream = result.getResponseInputStream();) {
				windowStart = result.getWindowStart();
				windowEnd = result.getWindowEnd();
				// disregard the response
				IOUtils.copy(responseInputStream, new NullOutputStream());
			}
		}

		ResultCollectionResponse results = retrieveResults(schedule);
		assertEquals(1, results.getData().size());
		Result result = results.getData().iterator().next();
		assertNotNull(result);
		assertEquals(windowStart, result.getWindowStartTime());
		assertEquals(windowEnd, result.getWindowEndTime());
		// assertEquals(schedule.getData().getUuid(), executionUuid);

		assertEquals(ScheduleStatus.Complete, schedule.getData().getStatus());
		assertNull(schedule.getData().getErrorMsg());
	}

	private Path createResultExportXML() throws Exception {

		schedule = createSchedule();
		schedule2 = createSchedule();

		// now export the schedule as executions for import in Responder
		OfflineExecutionExportRequest request = new OfflineExecutionExportRequest();
		request.getScheduleIds().add(Integer.valueOf(schedule.getData().getId()));
		request.getScheduleIds().add(Integer.valueOf(schedule2.getData().getId()));

		Path executionsExportFile = Paths.get("data/execution-export.xml");
		try (InputStream inputStream = createExecutionExportRest(request);) {
			IOUtils.copy(inputStream, Files.newOutputStream(executionsExportFile));
		}

		// import in Responder
		Path addResultPath = Paths.get("data/execution-add-result.xml");
		Files.deleteIfExists(addResultPath);
		String addCommand = "execution:import -i " + executionsExportFile.toAbsolutePath() + " -o " + addResultPath.toAbsolutePath();
		karaf.executeCommand(SSH_PREFIX + addCommand);
		assertTrue(Files.exists(addResultPath));

		Path executionResultPath = Paths.get("data/execution-result.xml");
		tryUntilTrue(60, 5_000, "job did not finish", n -> isJobComplete(addResultPath, executionResultPath), null);


		Path resultResultPath = Paths.get("data/result-export.xml");
		Files.deleteIfExists(resultResultPath);
		String resultExportCommand = "result:export -i " + executionResultPath.toAbsolutePath() + " -o " + resultResultPath.toAbsolutePath();
		karaf.executeCommand(SSH_PREFIX + resultExportCommand);
		assertTrue(Files.exists(resultResultPath));

		return resultResultPath;
	}

	private ScheduleResponse createSchedule() throws Exception {
		ScheduleResponse result = postSchedule("Standalone-Simple-Name-Record");
		result = retrieveSchedule(result.getData().getSelfUri());
		assertEquals(ScheduleStatus.Pending, result.getData().getStatus());

		String uuid = result.getData().getUuid();
		assertNotNull(uuid);
		assertNotNull(scheduleRepo.findByUUID(uuid));

		return result;
	}


	private boolean isJobComplete(Path addResultPath, Path executionResultPath) throws JAXBException, IOException, TimeoutException {
		Files.deleteIfExists(executionResultPath);
		String executionResultCommand = "execution:export -i " + addResultPath.toAbsolutePath() + " -o " + executionResultPath.toAbsolutePath();
		karaf.executeCommand(SSH_PREFIX + executionResultCommand);
		assertTrue(Files.exists(executionResultPath));
		ExecutionTypeConverter converter = new ExecutionTypeConverter();
		try (InputStream fis = Files.newInputStream(executionResultPath)) {
			ExecutionResources resources = converter.unmarshalExecutionResources(fis);
			return resources.getExecutionResource().get(0).getExecution().getCompletedOn() != null;
		}
	}
}
