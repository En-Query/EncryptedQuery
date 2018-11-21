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
package org.enquery.encryptedquery.querier.it.data;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.enquery.encryptedquery.querier.data.service.DataSourceRepository;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.querier.data.service.QuerySchemaRepository;
import org.enquery.encryptedquery.querier.data.service.ResultRepository;
import org.enquery.encryptedquery.querier.data.service.RetrievalRepository;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.enquery.encryptedquery.querier.it.AbstractQuerierItest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RetrievalRepoIT extends AbstractQuerierItest {

	@Inject
	@Filter(timeout = 60_000)
	private RetrievalRepository retrievalRepo;
	@Inject
	@Filter(timeout = 60_000)
	private QueryRepository queryRepo;
	@Inject
	@Filter(timeout = 60_000)
	private DataSchemaRepository dataSchemaRepo;
	@Inject
	@Filter(timeout = 60_000)
	private QuerySchemaRepository querySchemaRepo;
	@Inject
	@Filter(timeout = 60_000)
	private ResultRepository resultRepo;
	@Inject
	@Filter(timeout = 60_000)
	private ScheduleRepository scheduleRepo;
	@Inject
	@Filter(timeout = 60_000)
	private DataSourceRepository dataSourceRepo;

	@Before
	public void initService() throws Exception {
		truncateTables();
	}

	@Configuration
	public Option[] configuration() {
		return new Option[] {baseOptions()};
	}

	@Test
	public void basicOperations() throws Exception {

		DataSchema dataSchema = dataSchemaRepo.add(sampleData.createDataSchema());
		QuerySchema querySchema = querySchemaRepo.add(sampleData.createJPAQuerySchema(dataSchema));
		Query query = queryRepo.add(sampleData.createQuery(querySchema));
		DataSource dataSource = dataSourceRepo.add(sampleData.createDataSource(dataSchema));
		Schedule schedule = scheduleRepo.add(sampleData.createSchedule(query, dataSource));
		Result result = resultRepo.add(sampleData.createResult(schedule));
		Retrieval retrieval = retrievalRepo.add(sampleData.createRetrieval(result));

		InputStream inputStream = new ByteArrayInputStream("test-result-response".getBytes());
		retrievalRepo.updatePayload(retrieval, inputStream);

		try (InputStream payloadInputStream = retrievalRepo.payloadInputStream(retrieval)) {
			assertEquals("test-result-response", new String(IOUtils.toByteArray(payloadInputStream)));
		}

		Collection<Retrieval> list = retrievalRepo.listForResult(result);
		assertEquals(1, list.size());

		Retrieval r = list.iterator().next();
		assertEquals(retrieval.getId(), r.getId());

		retrievalRepo.deleteAll();
		list = retrievalRepo.listForResult(result);
		assertEquals(0, list.size());
	}

}
