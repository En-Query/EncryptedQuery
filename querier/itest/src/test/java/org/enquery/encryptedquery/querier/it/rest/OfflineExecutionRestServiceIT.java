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
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.karaf.shell.api.console.SessionFactory;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceResponse;
import org.enquery.encryptedquery.querier.data.entity.json.OfflineExecutionExportRequest;
import org.enquery.encryptedquery.querier.data.entity.json.Query;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleResponse;
import org.enquery.encryptedquery.querier.it.util.KarafController;
import org.enquery.encryptedquery.xml.transformation.ExecutionExportReader;
import org.enquery.encryptedquery.xml.transformation.ExecutionReader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class OfflineExecutionRestServiceIT extends BaseRestServiceWithStandaloneRunnerItest {

	@Inject
	private ExecutorService threadPool;
	@Inject
	protected SessionFactory sessionFactory;

	@Override
	@Configuration
	public Option[] configuration() {
		return new Option[] {baseOptions()};
	}

	@Override
	@Before
	public void init() throws Exception {
		super.init();
	}

	@Test
	public void exportExecutionsRest() throws Exception {

		DataSchemaResponse jsonDataSchema = retrieveDataSchemaByName("Simple Data");
		DataSourceResponse jsonDataSource = retrieveDataSourceByName(jsonDataSchema, "Standalone-Simple-Name-Record");

		QuerySchema querySchema = createQuerySchema(jsonDataSchema.getData());
		Query query = createQueryAndWaitForEncryption(querySchema);

		configureOfflineMode(true);
		try {

			// post schedule while offline so it is not sent to Responder
			ScheduleResponse schedule = postSchedule(query.getSchedulesUri(), jsonDataSource.getData().getId(), dataSourceParams());

			Integer scheduleId = Integer.valueOf(schedule.getData().getId());

			OfflineExecutionExportRequest request = new OfflineExecutionExportRequest();
			request.getScheduleIds().add(scheduleId);

			Path outputFile = Paths.get("data/execution-export.xml");
			try (InputStream inputStream = createExecutionExportRest(request);) {
				IOUtils.copy(inputStream, Files.newOutputStream(outputFile));
			}

			// String s = createExecutionExport(request);
			// log.info("Executions Export: {}", s);
			try (InputStream inputStream = Files.newInputStream(outputFile);
					ExecutionExportReader extractor = new ExecutionExportReader(threadPool)) {

				extractor.parse(inputStream);
				assertTrue(extractor.hasNextItem());
				extractor.next();
				assertEquals(jsonDataSchema.getData().getName(), extractor.getDataSchemaName());
				assertEquals(jsonDataSource.getData().getName(), extractor.getDataSourceName());
			}

		} finally {
			configureOfflineMode(false);
		}
	}


	@Test
	public void exportExecutionsCommand() throws Exception {

		DataSchemaResponse jsonDataSchema = retrieveDataSchemaByName("Simple Data");
		DataSourceResponse jsonDataSource = retrieveDataSourceByName(jsonDataSchema, "Standalone-Simple-Name-Record");

		QuerySchema querySchema = createQuerySchema(jsonDataSchema.getData());
		Query query = createQueryAndWaitForEncryption(querySchema);

		configureOfflineMode(true);
		try {

			// post schedule while offline so it is not sent to Responder
			ScheduleResponse schedule1 = postSchedule(query.getSchedulesUri(), jsonDataSource.getData().getId(), dataSourceParams());
			ScheduleResponse schedule2 = postSchedule(query.getSchedulesUri(), jsonDataSource.getData().getId(), dataSourceParams());

			Integer schedule1Id = Integer.valueOf(schedule1.getData().getId());
			Integer schedule2Id = Integer.valueOf(schedule2.getData().getId());

			Path outputFile = Paths.get("data/execution-export.xml");
			runExecutionExportCmd(outputFile, schedule1Id, schedule2Id);

			try (InputStream inputStream = Files.newInputStream(outputFile);
					ExecutionExportReader expReader = new ExecutionExportReader(threadPool)) {

				expReader.parse(inputStream);
				assertTrue(expReader.hasNextItem());

				try (ExecutionReader execReader = expReader.next();
						InputStream queryInputStream = execReader.getQueryInputStream();) {
					assertEquals(jsonDataSchema.getData().getName(), expReader.getDataSchemaName());
					assertEquals(jsonDataSource.getData().getName(), expReader.getDataSourceName());
					assertEquals(schedule1.getData().getUuid(), execReader.getUUId());

					IOUtils.copy(queryInputStream, new NullOutputStream());
				}


				assertTrue(expReader.hasNextItem());
				try (ExecutionReader execReader = expReader.next();
						InputStream queryInputStream = execReader.getQueryInputStream();) {

					assertEquals(jsonDataSchema.getData().getName(), expReader.getDataSchemaName());
					assertEquals(jsonDataSource.getData().getName(), expReader.getDataSourceName());
					assertEquals(schedule2.getData().getUuid(), execReader.getUUId());
					IOUtils.copy(queryInputStream, new NullOutputStream());
				}

			}

		} finally {
			configureOfflineMode(false);
		}
	}

	/**
	 * @param scheduleId
	 * @return
	 * @throws TimeoutException
	 */
	private void runExecutionExportCmd(Path outputFile, int... scheduleIds) throws TimeoutException {
		KarafController karaf = new KarafController(sessionFactory);
		StringBuilder sb = new StringBuilder("schedule:export ");
		sb.append("--output-file ");
		sb.append(outputFile.toString());
		sb.append("  ");
		for (int id : scheduleIds) {
			sb.append(id);
			sb.append(" ");
		}
		log.info("Running command: " + sb.toString());
		karaf.executeCommand(sb.toString());
	}

}
