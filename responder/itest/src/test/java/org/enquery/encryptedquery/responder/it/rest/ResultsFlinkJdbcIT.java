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
package org.enquery.encryptedquery.responder.it.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;

import javax.inject.Inject;
import javax.xml.datatype.DatatypeFactory;

import org.apache.commons.io.IOUtils;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.loader.SchemaLoader;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.responder.it.util.DerbyBookDatabase;
import org.enquery.encryptedquery.responder.it.util.FlinkDriver;
import org.enquery.encryptedquery.xml.schema.DataSchemaResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResources;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.Query;
import org.enquery.encryptedquery.xml.schema.ResultResource;
import org.enquery.encryptedquery.xml.schema.ResultResources;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ResultsFlinkJdbcIT extends BaseRestServiceItest {

	@Inject
	@Filter(timeout = 60_000)
	private EncryptQuery querierFactory;
	@Inject
	private QueryTypeConverter queryConverter;

	private static final String DATA_SOURCE_NAME = "test-name";

	private DataSchemaResource booksDataSchema;
	private DataSourceResource dataSourceResource;
	private FlinkDriver flinkDriver = new FlinkDriver();
	private DerbyBookDatabase derbyDatabase = new DerbyBookDatabase();

	@Configuration
	public Option[] configuration() {
		return combineOptions(super.baseOptions(),
				flinkDriver.configuration(),
				derbyDatabase.configuration());
	}

	@ProbeBuilder
	@Override
	public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
		super.probeConfiguration(probe);
		derbyDatabase.probeConfiguration(probe);
		return probe;
	}

	@Before
	@Override
	public void init() throws Exception {
		super.init();

		installBooksDataSchema();
		booksDataSchema = retrieveDataSchemaByName("Books");
		installFlinkJdbcDataSource(DATA_SOURCE_NAME, booksDataSchema.getDataSchema().getName());
		DataSourceResources dataSources = retrieveDataSources("/responder/api/rest/datasources");
		dataSourceResource = dataSources
				.getDataSourceResource()
				.stream()
				.filter(ds -> DATA_SOURCE_NAME.equals(ds.getDataSource().getName()))
				.findFirst()
				.get();

		flinkDriver.init();
		derbyDatabase.init();
	}

	@After
	public void cleanup() throws IOException, InterruptedException {
		flinkDriver.cleanup();
	}

	@Test
	public void happyPath() throws Exception {
		// Add an execution for current time
		DatatypeFactory dtf = DatatypeFactory.newInstance();
		Execution ex = new Execution();
		GregorianCalendar cal = new GregorianCalendar();
		ex.setScheduledFor(dtf.newXMLGregorianCalendar(cal));
		log.info("Schedule job: " + ex);

		Querier querier = createQuerier();
		Query xmlQuery = queryConverter.toXMLQuery(querier.getQuery());
		ex.setQuery(xmlQuery);

		ExecutionResource execution = createExecution(dataSourceResource.getExecutionsUri(), ex);

		tryUntilTrue(100,
				3_000,
				"Timeout waiting for an execution result.",
				uri -> retrieveResults(uri).getResultResource().size() > 0,
				execution.getResultsUri());


		ResultResources results = retrieveResults(execution.getResultsUri());
		assertEquals(1, results.getResultResource().size());

		ResultResource resource = results.getResultResource().get(0);
		assertNotNull(resource.getId());
		assertNotNull(resource.getSelfUri());

		ResultResource resultWithPayload = retrieveResult(resource.getSelfUri());
		assertEquals(resource.getCreatedOn(), resultWithPayload.getCreatedOn());
		assertNotNull(resultWithPayload.getPayload());
		// TODO: download and decrypt the response
	}


	@Test
	public void fileAlreadyExistsError() throws Exception {
		Querier querier = createQuerier();
		Query xmlQuery = queryConverter.toXMLQuery(querier.getQuery());


		// schedule for next minute so we have time to create the file to force error
		DatatypeFactory dtf = DatatypeFactory.newInstance();
		GregorianCalendar cal = new GregorianCalendar();
		cal.add(GregorianCalendar.MINUTE, 1);

		// Now add an execution and test its retrieval
		Execution ex = new Execution();
		ex.setScheduledFor(dtf.newXMLGregorianCalendar(cal));
		ex.setQuery(xmlQuery);
		ExecutionResource created = createExecution(dataSourceResource.getExecutionsUri(), ex);

		Files.createFile(Paths.get("data", "responses", "response-" + created.getId() + ".xml"));

		tryUntilTrue(100,
				3_000,
				"Timeout waiting for an execution to have error message.",
				uri -> retrieveExecution(created.getSelfUri()).getExecution().getErrorMessage() != null,
				null);
	}

	private static final Integer DATA_CHUNK_SIZE = 1;
	private static final Integer HASH_BIT_SIZE = 9;
	private static final String SELECTOR = "A Cup of Java";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});

	private Querier createQuerier() throws Exception {
		byte[] bytes = IOUtils.resourceToByteArray("/schemas/get-price-query-schema.xml",
				this.getClass().getClassLoader());

		SchemaLoader loader = new SchemaLoader();
		QuerySchema querySchema = loader.loadQuerySchema(bytes);

		return querierFactory.encrypt(querySchema, SELECTORS, true, DATA_CHUNK_SIZE, HASH_BIT_SIZE);
	}
}
