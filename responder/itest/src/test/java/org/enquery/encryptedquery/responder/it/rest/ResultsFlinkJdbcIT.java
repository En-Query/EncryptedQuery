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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.xml.datatype.DatatypeFactory;

import org.apache.commons.io.IOUtils;
import org.apache.karaf.shell.api.console.SessionFactory;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Field;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Hits;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Record;
import org.enquery.encryptedquery.data.ClearTextQueryResponse.Selector;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.loader.SchemaLoader;
import org.enquery.encryptedquery.querier.decrypt.DecryptResponse;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.responder.it.util.DerbyBookDatabase;
import org.enquery.encryptedquery.responder.it.util.FlinkDriver;
import org.enquery.encryptedquery.responder.it.util.KarafController;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.xml.Versions;
import org.enquery.encryptedquery.xml.schema.DataSchemaResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResources;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.Query;
import org.enquery.encryptedquery.xml.schema.ResultResource;
import org.enquery.encryptedquery.xml.schema.ResultResources;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
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
	@Inject
	protected SessionFactory sessionFactory;
	@Inject
	private CryptoSchemeRegistry cryptoSchemeRegistry;
	@Inject
	private DecryptResponse decryptor;

	private static final String DATA_SOURCE_NAME = "test-name";
	private static final Integer DATA_CHUNK_SIZE = 1;
	private static final Integer HASH_BIT_SIZE = 9;
	private static final String SELECTOR = "A Cup of Java";

	private DataSchemaResource booksDataSchema;
	private DataSourceResource dataSourceResource;
	private FlinkDriver flinkDriver = new FlinkDriver();
	private DerbyBookDatabase derbyDatabase = new DerbyBookDatabase();
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});
	private KarafController kafkaController;

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
		kafkaController = new KarafController(sessionFactory);
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
		ex.setSchemaVersion(Versions.EXECUTION_BI);
		GregorianCalendar cal = new GregorianCalendar();
		ex.setScheduledFor(dtf.newXMLGregorianCalendar(cal));
		ex.setUuid(UUID.randomUUID().toString().replaceAll("-", ""));
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

		validateWithResultCommand(booksDataSchema.getDataSchema().getName(), dataSourceResource.getDataSource().getName(), execution.getId(), resource);
		validateDecryptedResults(querier, resultWithPayload.getPayload());
	}


	private void validateDecryptedResults(Querier querier, org.enquery.encryptedquery.xml.schema.Response xmlResponse) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InterruptedException, PIRException {
		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoSchemeRegistry);
		queryConverter.initialize();

		ResponseTypeConverter responseConverter = new ResponseTypeConverter();
		responseConverter.setQueryConverter(queryConverter);
		responseConverter.setSchemeRegistry(cryptoSchemeRegistry);
		responseConverter.initialize();

		Response response = responseConverter.toCore(xmlResponse);
		ClearTextQueryResponse answer = decryptor.decrypt(response, querier.getQueryKey());
		log.info("Decrypted: {}.", answer);

		assertEquals(1, answer.selectorCount());
		Selector sel = answer.selectorByName("title");
		assertEquals("title", sel.getName());
		assertEquals(1, sel.hitCount());
		Hits h = sel.hitsBySelectorValue("A Cup of Java");
		assertEquals("A Cup of Java", h.getSelectorValue());
		assertEquals(1, h.recordCount());
		Record r = h.recordByIndex(0);
		assertEquals(4, r.fieldCount());
		Field f = r.fieldByName("price");
		assertEquals(Double.valueOf("44.44"), f.getValue());
		f = r.fieldByName("isNew");
		assertEquals(Boolean.TRUE, f.getValue());
		f = r.fieldByName("author");
		assertEquals("Kumar", f.getValue());
		f = r.fieldByName("qty");
		assertEquals(44, f.getValue());
	}

	/**
	 * @param dataSchemaId
	 * @param dataSourceId
	 * @param executionId
	 * @throws TimeoutException
	 */
	private void validateWithResultCommand(String dataSchemaName, String dataSourceName, int executionId, ResultResource result) throws TimeoutException {
		String pipe = "|";
		final String regex = "^" + result.getId() + "\\s*" + pipe + " " + executionId + "\\s*" + pipe;
		final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.UNIX_LINES);

		String output = kafkaController.executeCommand("result:list");
		assertTrue(find(pattern, output));

		output = kafkaController.executeCommand("result:list --dataschema \"" + dataSchemaName + "\"");
		assertTrue(find(pattern, output));

		output = kafkaController.executeCommand("result:list --datasource \"" + dataSourceName + "\"");
		assertTrue(find(pattern, output));

		output = kafkaController.executeCommand("result:list --execution " + executionId);
		assertTrue(find(pattern, output));

		output = kafkaController.executeCommand("result:list --dataschema \"" + dataSchemaName + "\" --datasource \"" + dataSourceName + "\"");
		assertTrue(find(pattern, output));

		output = kafkaController.executeCommand("result:list --dataschema \"" + dataSchemaName + "\" --datasource \"" + dataSourceName + "\" --execution " + executionId);
		assertTrue(find(pattern, output));

		output = kafkaController.executeCommand("result:list --dataschema \"" + dataSchemaName + "\" --execution " + executionId);
		assertTrue(find(pattern, output));

		output = kafkaController.executeCommand("result:list  --datasource \"" + dataSourceName + "\" --execution " + executionId);
		assertTrue(find(pattern, output));
	}

	private boolean find(Pattern pattern, String output) {
		boolean result = pattern.matcher(output).find(0);
		log.info("RegEx: '{}' matched: {}.", pattern, result);
		return result;
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
		ex.setSchemaVersion(Versions.EXECUTION_BI);
		ex.setScheduledFor(dtf.newXMLGregorianCalendar(cal));
		ex.setUuid(UUID.randomUUID().toString().replaceAll("-", ""));
		ex.setQuery(xmlQuery);
		ExecutionResource created = createExecution(dataSourceResource.getExecutionsUri(), ex);

		Files.createFile(Paths.get("data", "responses", "response-" + created.getId() + ".xml"));

		tryUntilTrue(100,
				3_000,
				"Timeout waiting for an execution to have error message.",
				uri -> retrieveExecution(created.getSelfUri()).getExecution().getErrorMessage() != null,
				null);
	}

	private Querier createQuerier() throws Exception {
		byte[] bytes = IOUtils.resourceToByteArray("/schemas/get-price-query-schema.xml",
				this.getClass().getClassLoader());

		SchemaLoader loader = new SchemaLoader();
		QuerySchema querySchema = loader.loadQuerySchema(bytes);

		return querierFactory.encrypt(querySchema, SELECTORS, DATA_CHUNK_SIZE, HASH_BIT_SIZE);
	}
}
