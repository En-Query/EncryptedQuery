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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.entity.json.Decryption;
import org.enquery.encryptedquery.querier.data.entity.json.DecryptionCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DecryptionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DecryptionStatus;
import org.enquery.encryptedquery.querier.data.entity.json.Retrieval;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import com.fasterxml.jackson.core.JsonProcessingException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class FlinkDecryptionsRestServiceIT extends BaseRestServiceWithFlinkRunnerItest {

	@Test
	public void emptyList() throws JsonProcessingException {
		DataSchema jpaDataSchema = dataSchemaRepo.add(sampleData.createDataSchema());
		DataSource jpaDataSource = dataSourceRepo.add(sampleData.createDataSource(jpaDataSchema));
		Query jpaQuery = queryRepo.add(
				sampleData.createQuery(
						querySchemaRepo.add(
								sampleData.createJPAQuerySchema(jpaDataSchema))));

		Schedule jpaSchedule = scheduleRepo.add(sampleData.createSchedule(jpaQuery, jpaDataSource));
		Result jpaResult = resultRepo.add(sampleData.createResult(jpaSchedule));

		int[] counters = new int[] {0, 0};
		forEach(retrieveDataSchemas(), ds -> {
			forEach(retrieveQuerySchemas(retrieveDataSchema(ds.getSelfUri())), qs -> {
				forEach(retrieveQueries(retrieveQuerySchema(qs.getSelfUri())), q -> {
					forEach(retrieveSchedules(retrieveQuery(q.getSelfUri())), sch -> {
						assertEquals(sch.getId(), jpaSchedule.getId().toString());
						forEach(retrieveResults(retrieveSchedule(sch.getSelfUri())), result -> {
							assertEquals(jpaResult.getId().toString(), result.getId());
							++counters[0];
							forEach(retrieveRetrievals(retrieveResult(result.getSelfUri())), retrieval -> {
								++counters[1];
								DecryptionCollectionResponse decryptions = retrieveDecryptions(retrieval.getDecryptionsUri());
								assertTrue(decryptions.getData().isEmpty());
							});
						});
					});
				});
			});
		});

		assertEquals(1, counters[0]);
	}


	@Test
	public void create() throws Exception {
		Retrieval fullRetrieval = submitQueryAndRetrieveResult();
		DecryptionResponse decryptionResponse = createDecryption(fullRetrieval.getDecryptionsUri());
		Decryption created = decryptionResponse.getData();
		assertNotNull(created);
		assertNotNull(created.getId());
		assertNotNull(created.getSelfUri());
		assertNotNull(created.getStatus());

		DecryptionCollectionResponse decryptions = retrieveDecryptions(fullRetrieval.getDecryptionsUri());
		assertFalse(decryptions.getData().isEmpty());
		Decryption listed = decryptions.getData().iterator().next();
		assertEquals(created.getId(), listed.getId());
		assertEquals(created.getSelfUri(), listed.getSelfUri());
		assertEquals(created.getRetrieval().getId(), listed.getRetrieval().getId());
		assertEquals(created.getRetrieval().getSelfUri(), listed.getRetrieval().getSelfUri());
		assertNotNull(created.getStatus());

		tryUntilTrue(60,
				2_000,
				"Timed out waiting for DecryptionStatus.Complete",
				uri -> retrieveDecryption(uri).getData().getStatus() == DecryptionStatus.Complete,
				created.getSelfUri());

		Decryption retrieved = retrieveDecryption(created.getSelfUri()).getData();
		assertEquals(created.getId(), retrieved.getId());
		assertEquals(created.getSelfUri(), retrieved.getSelfUri());
		assertEquals(created.getRetrieval().getId(), retrieved.getRetrieval().getId());
		assertEquals(created.getRetrieval().getSelfUri(), retrieved.getRetrieval().getSelfUri());
		assertEquals(DecryptionStatus.Complete, retrieved.getStatus());

		validateClearTextResponse(retrieved, "title", "A Cup of Java", "author", "Kumar");
	}

}
