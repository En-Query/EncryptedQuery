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
package org.enquery.encryptedquery.responder.it.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.entity.Result;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;
import org.enquery.encryptedquery.responder.it.AbstractResponderItest;
import org.enquery.encryptedquery.responder.it.util.FlinkDriver;
import org.enquery.encryptedquery.responder.it.util.SampleData;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ResultRepositoryIT extends AbstractResponderItest {

	@Inject
	private ExecutionRepository executionRepo;
	@Inject
	private ResultRepository resultRepo;
	@Inject
	private DataSchemaService dataSchemaSvc;

	private FlinkDriver flinkDriver = new FlinkDriver();

	@Configuration
	public Option[] configuration() {
		return combineOptions(super.baseOptions(),
				flinkDriver.configuration());
	}

	@Test
	public void addAndList() throws IOException, InterruptedException, Exception {
		SampleData sampleData = new SampleData();
		DataSchema dataSchema = dataSchemaSvc.add(sampleData.createDataSchema());
		DataSource dataSource = installFlinkJdbcDataSource("test", dataSchema.getName());
		Execution execution = executionRepo.add(sampleData.createExecution(dataSchema, dataSource));

		Assert.assertEquals(0, resultRepo.listForExecution(execution).size());
		Result result = sampleData.createResult(execution);

		resultRepo.add(result);

		Assert.assertEquals(1, resultRepo.listForExecution(execution).size());

		resultRepo.delete(result);
		Assert.assertEquals(0, resultRepo.listForExecution(execution).size());
	}

	@Test
	public void addPayload() throws IOException, InterruptedException, Exception {
		SampleData sampleData = new SampleData();
		DataSchema dataSchema = dataSchemaSvc.add(sampleData.createDataSchema());
		DataSource dataSource = installFlinkJdbcDataSource("test", dataSchema.getName());
		Execution execution = executionRepo.add(sampleData.createExecution(dataSchema, dataSource));

		Assert.assertEquals(0, resultRepo.listForExecution(execution).size());

		InputStream inputStream = new ByteArrayInputStream("test payload".getBytes());
		Result result = resultRepo.add(execution, inputStream, null, null);
		assertNotNull(result);
		assertNotNull(result.getPayloadUrl());
		Assert.assertEquals(1, resultRepo.listForExecution(execution).size());

		try (InputStream payloadInputStream = resultRepo.payloadInputStream(result.getId())) {
			String payload = new String(IOUtils.toByteArray(payloadInputStream));
			assertEquals("test payload", payload);
		}
	}

}
