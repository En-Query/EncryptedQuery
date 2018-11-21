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
import static org.junit.Assert.fail;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QueryCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ResultCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ResultResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ResultStatus;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleCollectionResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import com.fasterxml.jackson.core.JsonProcessingException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ResultsRestServiceIT extends BaseRestServiceWithFlinkRunnerItest {

	@Test
	public void listEmpty() throws JsonProcessingException {
		DataSchema jpaDataSchema = dataSchemaRepo.add(sampleData.createDataSchema());
		DataSource jpaDataSource = dataSourceRepo.add(sampleData.createDataSource(jpaDataSchema));
		Query jpaQuery = queryRepo.add(
				sampleData.createQuery(
						querySchemaRepo.add(
								sampleData.createJPAQuerySchema(jpaDataSchema))));

		Schedule jpaSchedule = scheduleRepo.add(sampleData.createSchedule(jpaQuery, jpaDataSource));

		int[] scheduleCount = new int[] {0};
		forEach(retrieveDataSchemas(), ds -> {
			forEach(retrieveQuerySchemas(retrieveDataSchema(ds.getSelfUri())), qs -> {
				forEach(retrieveQueries(retrieveQuerySchema(qs.getSelfUri())), q -> {
					forEach(retrieveSchedules(retrieveQuery(q.getSelfUri())), sch -> {
						assertEquals(sch.getId(), jpaSchedule.getId().toString());
						++scheduleCount[0];
						forEach(retrieveResults(retrieveSchedule(sch.getSelfUri())), res -> {
							fail("No result expected.");
						});
					});
				});
			});
		});

		assertEquals(1, scheduleCount[0]);
	}

	@Test
	public void retrieve() throws JsonProcessingException {

		DataSchema jpaDataSchema = dataSchemaRepo.add(sampleData.createDataSchema());
		DataSource jpaDataSource = dataSourceRepo.add(sampleData.createDataSource(jpaDataSchema));
		Query jpaQuery = queryRepo.add(
				sampleData.createQuery(
						querySchemaRepo.add(
								sampleData.createJPAQuerySchema(jpaDataSchema))));

		Schedule jpaSchedule = scheduleRepo.add(sampleData.createSchedule(jpaQuery, jpaDataSource));
		Result stored = resultRepo.add(sampleData.createResult(jpaSchedule));
		log.info("Stored: " + stored.toString());

		DataSchemaCollectionResponse dataSchemas = retrieveDataSchemas();
		// org.enquery.encryptedquery.querier.data.entity.json.DataSchema dataSchema =
		// dataSchemas.getData().iterator().next();

		org.enquery.encryptedquery.querier.data.entity.json.DataSchema ds = retrieveDataSchema(
				dataSchemas.getData()
						.stream()
						.filter(
								d -> d.getName().equals(jpaDataSchema.getName()))
						.findFirst()
						.get()
						.getSelfUri()).getData();

		QuerySchemaCollectionResponse querySchemas = retrieveQuerySchemas(ds.getQuerySchemasUri());
		org.enquery.encryptedquery.querier.data.entity.json.QuerySchema qs = retrieveQuerySchema(querySchemas.getData().iterator().next().getSelfUri()).getData();

		QueryCollectionResponse queries = retrieveQueries(qs.getQueriesUri());
		org.enquery.encryptedquery.querier.data.entity.json.Query q = retrieveQuery(queries.getData().iterator().next().getSelfUri()).getData();

		ScheduleCollectionResponse schedules = retrieveSchedules(q.getSchedulesUri());
		org.enquery.encryptedquery.querier.data.entity.json.Schedule sch = retrieveSchedule(schedules.getData().iterator().next().getSelfUri()).getData();

		validateResult(stored, sch, ResultStatus.Ready);

		// now test Result status transitions
		Retrieval retrieval = new Retrieval();
		retrieval.setResult(stored);
		retrievalRepo.add(retrieval);

		validateResult(stored, sch, ResultStatus.Downloading);

		retrieval.setPayloadUri("some uri");
		retrievalRepo.update(retrieval);

		validateResult(stored, sch, ResultStatus.Downloaded);

		retrieval.setPayloadUri(null);
		retrieval.setErrorMessage("error");
		retrievalRepo.update(retrieval);

		validateResult(stored, sch, ResultStatus.Ready);
	}

	private void validateResult(Result expectedResult,
			org.enquery.encryptedquery.querier.data.entity.json.Schedule sch,
			ResultStatus expectedStatus) {

		ResultCollectionResponse results = retrieveResults(sch.getResultsUri());
		org.enquery.encryptedquery.querier.data.entity.json.Result res = results.getData().iterator().next();

		log.info("Retrieved: " + res.toString());

		assertEquals(expectedResult.getId().toString(), res.getId());
		assertNotNull(res.getSelfUri());
		assertEquals(expectedStatus, res.getStatus());
		assertNull(res.getRetrievalsUri());
		assertNull(res.getSchedule());

		org.enquery.encryptedquery.querier.data.entity.json.Result retrieved =
				retrieveResult(res.getSelfUri()).getData();
		assertNotNull(retrieved);
		assertEquals(res.getSelfUri(), retrieved.getSelfUri());
		assertEquals(expectedResult.getId().toString(), retrieved.getId());
		assertNotNull(retrieved.getRetrievalsUri());
		assertEquals(sch.getSelfUri(), retrieved.getSchedule().getSelfUri());
		assertEquals(expectedStatus, retrieved.getStatus());
	}

	@Test
	public void scheduleAndRetrieveResult() throws Exception {
		/*--
		 * org.enquery.encryptedquery.querier.data.entity.json.DataSchema jsonDataSchema =
				retrieveDataSchema(
						retrieveDataSchemas()
								.getData()
								.stream()
								.filter(ds -> ds.getName().equals("Books"))
								.findFirst()
								.get()
								.getSelfUri()).getData();
		
		org.enquery.encryptedquery.querier.data.entity.json.DataSource jsonDataSource = retrieveDataSources(jsonDataSchema)
				.getData()
				.stream()
				.filter(ds -> ds.getName().equals("Flink-JDBC-Derby-Books"))
				.findFirst()
				.get();
		
		DataSchema jpaDataSchema = dataSchemaRepo.find(Integer.valueOf(jsonDataSchema.getId()));
		QuerySchema jpaQuerySchema =
				querySchemaRepo.add(
						sampleData.createJPAQuerySchema(
								jpaDataSchema));
		
		org.enquery.encryptedquery.querier.data.entity.json.QuerySchema jsonQuerySchema = retrieveQuerySchemas(jsonDataSchema)
				.getData()
				.stream()
				.filter(x -> x.getId().equals(jpaQuerySchema.getId().toString()))
				.findFirst()
				.get();
		
		jsonQuerySchema = retrieveQuerySchema(jsonQuerySchema.getSelfUri()).getData();
		org.enquery.encryptedquery.querier.data.entity.json.Query query =
				createQueryAndWaitForEncryption(jsonQuerySchema).getData();
		
		
		org.enquery.encryptedquery.querier.data.entity.json.Schedule sch = new org.enquery.encryptedquery.querier.data.entity.json.Schedule();
		// schedule it for 30 seconds from now, resolution is minute, so it will run the next minute
		sch.setStartTime(Date.from(Instant.now().plus(Duration.ofSeconds(30))));
		sch.setParameters(sampleData.createScheduleSampleParameters());
		sch.setDataSource(new Resource(jsonDataSource));
		
		ScheduleResponse schedule = createSchedule(query.getSchedulesUri(), sch);
		assertNotNull(schedule);
		assertNotNull(schedule.getData());
		assertNotNull(schedule.getData().getId());
		assertNotNull(schedule.getData().getParameters());
		assertNotNull(schedule.getData().getQuery());
		assertEquals(query.getId(), schedule.getData().getQuery().getId());
		assertEquals(query.getSelfUri(), schedule.getData().getQuery().getSelfUri());
		assertNotNull(schedule.getData().getResultsUri());
		assertNotNull(schedule.getData().getSelfUri());
		assertNotNull(schedule.getData().getStartTime());
		assertEquals(ScheduleStatus.Pending, schedule.getData().getStatus());
		
		tryUntilTrue(60, 3_000, "Timeout waiting for Schedule to Start.", op -> {
			return ScheduleStatus.InProgress.equals(retrieveSchedule(schedule.getData().getSelfUri()).getData().getStatus());
		}, null);
		
		tryUntilTrue(60, 3_000, "Timeout waiting for Schedule to Complete.", op -> {
			return ScheduleStatus.Complete.equals(retrieveSchedule(schedule.getData().getSelfUri()).getData().getStatus());
		}, null);*/

		ResultResponse result = postScheduleAndWaitForResult();

		// ResultCollectionResponse results = retrieveResults(result.getData().getSelfUri());
		// assertEquals(1, results.getData().size());

		org.enquery.encryptedquery.querier.data.entity.json.Result res = retrieveResult(result.getData().getSelfUri()).getData();

		log.info("Retrieved: " + res.toString());
		assertNotNull(res.getId());
		assertNotNull(res.getSelfUri());
		assertNotNull(res.getRetrievalsUri());
		// assertEquals(schedule.getData().getId(), res.getSchedule().getId());
		// assertEquals(schedule.getData().getSelfUri(), res.getSchedule().getSelfUri());
	}
}
