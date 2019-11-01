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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.enquery.encryptedquery.querier.data.entity.RetrievalStatus;
import org.enquery.encryptedquery.querier.data.entity.ScheduleStatus;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.ResultCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ResultResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.json.RetrievalResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import com.fasterxml.jackson.core.JsonProcessingException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RetrievalsRestServiceIT extends BaseRestServiceWithFlinkRunnerItest {


	@Override
	@Configuration
	public Option[] configuration() {
		return ArrayUtils.add(super.configuration(),
				editConfigurationFilePut("etc/encrypted.query.querier.integration.cfg",
						"retrieve.result.delay.ms", "1000"));
	}

	@Test
	public void list() throws JsonProcessingException {
		DataSchema jpaDataSchema = dataSchemaRepo.add(sampleData.createDataSchema());
		DataSource jpaDataSource = dataSourceRepo.add(sampleData.createDataSource(jpaDataSchema));
		Query jpaQuery = queryRepo.add(
				sampleData.createQuery(
						querySchemaRepo.add(
								sampleData.createJPAQuerySchema(jpaDataSchema))));

		Schedule jpaSchedule = scheduleRepo.add(sampleData.createSchedule(jpaQuery, jpaDataSource));
		Result jpaResult = resultRepo.add(sampleData.createResult(jpaSchedule));

		int[] resultCount = new int[] {0};
		forEach(retrieveDataSchemas(), ds -> {
			forEach(retrieveQuerySchemas(retrieveDataSchema(ds.getSelfUri())), qs -> {
				forEach(retrieveQueries(retrieveQuerySchema(qs.getSelfUri())), q -> {
					forEach(retrieveSchedules(retrieveQuery(q.getSelfUri())), sch -> {
						assertEquals(sch.getId(), jpaSchedule.getId().toString());
						forEach(retrieveResults(retrieveSchedule(sch.getSelfUri())), result -> {
							assertEquals(jpaResult.getId().toString(), result.getId());
							++resultCount[0];
							forEach(retrieveRetrievals(retrieveResult(result.getSelfUri())), retrieval -> {
								fail("no retrieval expected");
							});
						});
					});
				});
			});
		});

		assertEquals(1, resultCount[0]);
	}

	@Test
	public void create() throws Exception {

		ResultResponse resultResponse = postScheduleAndWaitForResult();

		// retrieve multiple times to test the queue and the pending status
		List<Resource> retrievals = new ArrayList<>();
		int RETRIEVAL_COUNT = 10;

		// some retrievals should be in pending state, some in progress
		int pendingCount = 0;
		int inProgressCount = 0;
		for (int i = 0; i < RETRIEVAL_COUNT; ++i) {
			Retrieval retrieval = postRetrieval(resultResponse).getData();
			RetrievalStatus status = retrieval.getStatus();
			if (status.equals(RetrievalStatus.Pending)) {
				++pendingCount;
			} else if (status.equals(RetrievalStatus.InProgress)) {
				++inProgressCount;
			}
			retrievals.add(retrieval);
		}

		log.info("Retrievals Pending {}.", pendingCount);
		log.info("Retrievals In Progress {}.", inProgressCount);

		assertTrue(inProgressCount > 0);
		assertTrue(pendingCount > 0);

		// wait for all to complete
		for (int i = 0; i < RETRIEVAL_COUNT; ++i) {
			Resource retrieval = retrievals.get(i);
			tryUntilTrue(60,
					2_000,
					"Timed out waiting for retrieval completion",
					uri -> retrieveRetrieval(uri).getData().getStatus() == RetrievalStatus.Complete,
					retrieval.getSelfUri());

			Retrieval fullRetrieval = retrieveRetrieval(retrieval.getSelfUri()).getData();
			assertEquals(retrieval.getId(), fullRetrieval.getId());
			assertEquals(retrieval.getSelfUri(), fullRetrieval.getSelfUri());
			assertEquals(resultResponse.getData().getId(), fullRetrieval.getResult().getId());
			assertEquals(resultResponse.getData().getSelfUri(), fullRetrieval.getResult().getSelfUri());
			assertNotNull(fullRetrieval.getDecryptionsUri());
		}
	}

	@Test
	public void createWhenResponderNotOnline() throws Exception {

		ResultResponse resultResponse = postScheduleAndWaitForResult();

		// emulate Responder not reachable
		int oldPort = responderPort();
		configureResponderPort(oldPort + 10);
		try {
			RetrievalResponse r = postRetrieval(resultResponse);
			tryUntilTrue(60,
					2_000,
					"Timed out waiting for retrieval status is Failed",
					uri -> retrieveRetrieval(uri).getData().getStatus() == RetrievalStatus.Failed,
					r.getData().getSelfUri());
		} finally {
			configureResponderPort(oldPort);
		}

		// emulate Responder http error (404 in this case)
		configureResponderPort(8182);
		try {
			RetrievalResponse r = postRetrieval(resultResponse);
			tryUntilTrue(60,
					2_000,
					"Timed out waiting for retrieval status is Failed",
					uri -> retrieveRetrieval(uri).getData().getStatus() == RetrievalStatus.Failed,
					r.getData().getSelfUri());
		} finally {
			configureResponderPort(oldPort);
		}

	}


	@Test
	public void scheduleAndRetrieveResult() throws Exception {
		ScheduleResponse schedule = postSchedule();
		assertNotNull(schedule);
		assertNotNull(schedule.getData());
		assertNotNull(schedule.getData().getId());
		assertNotNull(schedule.getData().getParameters());
		assertNotNull(schedule.getData().getQuery());
		assertNotNull(schedule.getData().getResultsUri());
		assertNotNull(schedule.getData().getSelfUri());
		assertNotNull(schedule.getData().getStartTime());
		assertEquals(ScheduleStatus.Pending, schedule.getData().getStatus());

		tryUntilTrue(60, 3_000, "Timeout waiting for Schedule to Start.", op -> {
			return ScheduleStatus.InProgress.equals(retrieveSchedule(schedule.getData().getSelfUri()).getData().getStatus());
		}, null);

		tryUntilTrue(60, 3_000, "Timeout waiting for Schedule to Complete.", op -> {
			return ScheduleStatus.Complete.equals(retrieveSchedule(schedule.getData().getSelfUri()).getData().getStatus());
		}, null);


		ResultCollectionResponse results = retrieveResults(schedule);
		assertEquals(1, results.getData().size());

		org.enquery.encryptedquery.querier.data.entity.json.Result res =
				retrieveResult(results.getData().iterator().next().getSelfUri()).getData();

		log.info("Retrieved: " + res.toString());
		assertNotNull(res.getId());
		assertNotNull(res.getSelfUri());
		assertNotNull(res.getRetrievalsUri());
		assertEquals(schedule.getData().getId(), res.getSchedule().getId());
		assertEquals(schedule.getData().getSelfUri(), res.getSchedule().getSelfUri());
	}
}
