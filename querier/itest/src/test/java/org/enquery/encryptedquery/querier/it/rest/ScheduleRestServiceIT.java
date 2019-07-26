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

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.ScheduleStatus;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QueryResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ScheduleRestServiceIT extends BaseRestServiceWithFlinkRunnerItest {

	@Test
	public void listEmpty() throws Exception {
		// Add a data schema, query schema, and query, no schedules
		queryRepo.add(sampleData.createQuery(
				querySchemaRepo.add(
						sampleData.createJPAQuerySchema(
								dataSchemaRepo.add(
										sampleData.createDataSchema())))));

		retrieveDataSchemas()
				.getData()
				.stream()
				.forEach(ds -> {
					retrieveQuerySchemas(retrieveDataSchema(ds.getSelfUri()))
							.getData()
							.stream()
							.forEach(qs -> {
								retrieveQueries(retrieveQuerySchema(qs.getSelfUri()))
										.getData()
										.stream()
										.forEach(q -> {
											QueryResponse query = retrieveQuery(q.getSelfUri());
											ScheduleCollectionResponse list = retrieveSchedules(query.getData().getSchedulesUri());
											assertEquals(0, list.getData().size());
										});
							});
				});
	}

	@Test
	public void list() throws Exception {

		DataSchema jpaDataSchema = dataSchemaRepo.add(sampleData.createDataSchema());
		DataSource jpaDataSource = dataSourceRepo.add(sampleData.createDataSource(jpaDataSchema));
		Query jpaQuery = queryRepo.add(
				sampleData.createQuery(
						querySchemaRepo.add(
								sampleData.createJPAQuerySchema(
										jpaDataSchema))));

		Schedule jpaSchedule = scheduleRepo.add(sampleData.createSchedule(jpaQuery, jpaDataSource));

		retrieveDataSchemas()
				.getData()
				.stream()
				.forEach(ds -> {
					org.enquery.encryptedquery.querier.data.entity.json.DataSchema dataSchema =
							retrieveDataSchema(ds.getSelfUri()).getData();

					retrieveQuerySchemas(dataSchema)
							.getData()
							.stream()
							.forEach(qs -> {
								retrieveQueries(retrieveQuerySchema(qs.getSelfUri()))
										.getData()
										.stream()
										.forEach(q -> {
											QueryResponse query = retrieveQuery(q.getSelfUri());
											ScheduleCollectionResponse list = retrieveSchedules(query.getData().getSchedulesUri());
											assertEquals(1, list.getData().size());
											list.getData().stream()
													.forEach(sch -> {
														assertEquals(jpaSchedule.getId().toString(), sch.getId());
														assertEquals(ScheduleStatus.Pending, sch.getStatus());
														assertNotNull(sch.getSelfUri());
														assertNull(sch.getParameters());
														assertNull(sch.getResultsUri());
														assertNull(sch.getQuery());
														org.enquery.encryptedquery.querier.data.entity.json.ScheduleResponse retrieved = retrieveSchedule(sch.getSelfUri());
														assertNotNull(retrieved);
														assertNotNull(retrieved.getData());
														assertNotNull(retrieved.getData().getParameters());
														assertNotNull(retrieved.getData().getResultsUri());
														assertNotNull(retrieved.getData().getQuery());
													});
										});
							});
				});

	}

	@Test
	public void create() throws Exception {
		ScheduleResponse returned = postSchedule();
		assertNotNull(returned);
		assertNotNull(returned.getData());
		assertNotNull(returned.getData().getId());
		assertNotNull(returned.getData().getParameters());
		assertNotNull(returned.getData().getQuery());
		assertNotNull(returned.getData().getResultsUri());
		assertNotNull(returned.getData().getSelfUri());
		assertNotNull(returned.getData().getStartTime());
		assertEquals(ScheduleStatus.Pending, returned.getData().getStatus());

		assertEquals("Scheduled", includedQueryStatus(returned));

		tryUntilTrue(60, 3_000, "Timeout waiting for Schedule to Start.", op -> {
			return ScheduleStatus.InProgress.equals(retrieveSchedule(returned.getData().getSelfUri()).getData().getStatus());
		}, null);

		tryUntilTrue(60, 3_000, "Timeout waiting for Schedule to Complete.", op -> {
			return ScheduleStatus.Complete.equals(retrieveSchedule(returned.getData().getSelfUri()).getData().getStatus());
		}, null);
	}

	@Test
	public void createWhileResponderNotOnline() throws Exception {

		// populate from responder first, so there is a data source and data schems in the Querier
		// local DB
		DataSchemaResponse jsonDataSchema = retrieveDataSchemaByName("Books");
		Validate.notNull(jsonDataSchema);
		DataSourceResponse jsonDataSource = retrieveDataSourceByName(jsonDataSchema, "Flink-JDBC-Derby-Books");
		Validate.notNull(jsonDataSource);

		// emulate Responder not reachable when submitting a Schedule
		int oldPort = responderPort();
		configureResponderPort(oldPort + 10);
		try {
			ScheduleResponse returned = postSchedule();

			tryUntilTrue(60, 3_000, "Timeout waiting for Schedule to be Failed.", op -> {
				return ScheduleStatus.Failed.equals(retrieveSchedule(returned.getData().getSelfUri()).getData().getStatus());
			}, null);

			assertNotNull(retrieveSchedule(returned.getData().getSelfUri()).getData().getErrorMsg());

		} finally {
			configureResponderPort(oldPort);
		}

		// emulate Responder http error (404 in this case)
		configureResponderPort(8182);
		try {

			ScheduleResponse returned = postSchedule();

			tryUntilTrue(60, 3_000, "Timeout waiting for Schedule to be Failed.", op -> {
				return ScheduleStatus.Failed.equals(retrieveSchedule(returned.getData().getSelfUri()).getData().getStatus());
			}, null);

			assertNotNull(retrieveSchedule(returned.getData().getSelfUri()).getData().getErrorMsg());

		} finally {
			configureResponderPort(oldPort);
		}
	}

	private String includedQueryStatus(ScheduleResponse returned) {
		return returned
				.getIncluded()
				.stream()
				.filter(obj -> "Query".equals(obj.get("type")) &&
						returned.getData().getQuery().getId().equals(obj.get("id")))
				.map(obj -> (String) obj.get("status"))
				.findFirst()
				.orElse(null);
	}
}
