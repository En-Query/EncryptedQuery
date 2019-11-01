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
import javax.xml.datatype.DatatypeConfigurationException;
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
import org.enquery.encryptedquery.loader.SchemaLoader;
import org.enquery.encryptedquery.querier.decrypt.DecryptResponse;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.responder.it.util.DerbyBookDatabase;
import org.enquery.encryptedquery.responder.it.util.FlinkDriver;
import org.enquery.encryptedquery.responder.it.util.KarafController;
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
public class FlinkJdbcIT extends BaseRestServiceItest {

	@Inject
	@Filter(timeout = 60_000)
	private EncryptQuery querierFactory;
	@Inject
	private QueryTypeConverter queryConverter;
	@Inject
	protected SessionFactory sessionFactory;
	// @Inject
	// private CryptoSchemeRegistry cryptoSchemeRegistry;
	@Inject
	private DecryptResponse decryptor;
	@Inject
	private ResponseTypeConverter responseConverter;

	private static final String DATA_SOURCE_NAME = "test-name";
	private static final Integer DATA_CHUNK_SIZE = 20;
	private static final Integer HASH_BIT_SIZE = 9;

	private DataSchemaResource booksDataSchema;
	private DataSourceResource dataSourceResource;
	private FlinkDriver flinkDriver = new FlinkDriver();
	private DerbyBookDatabase derbyDatabase = new DerbyBookDatabase();
	private KarafController kafkaController;
	private Querier querier;
	private int resultId;

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

		ExecutionResource execution = runQuery("/schemas/get-price-query-schema.xml",
				null,
				Arrays.asList(new String[] {"A Cup of Java"}));

		ClearTextQueryResponse response = waitAndGetResult(execution);

		validateWithResultCommand(booksDataSchema.getDataSchema().getName(), dataSourceResource.getDataSource().getName(), execution.getId(), resultId);
		validateDecryptedResults(response);
	}

	@Test
	public void filtered() throws Exception {

		// this will return no matched, since author of 'A Cup of Java' is not Kevin Jones, but
		// Kumar
		ExecutionResource execution = runQuery("/schemas/get-price-query-schema.xml",
				"author = 'Kevin Jones'",
				Arrays.asList(new String[] {"A Cup of Java"}));

		ClearTextQueryResponse answer = waitAndGetResult(execution);

		validateWithResultCommand(booksDataSchema.getDataSchema().getName(), dataSourceResource.getDataSource().getName(), execution.getId(), resultId);

		assertEquals(1, answer.selectorCount());
		Selector sel = answer.selectorByName("title");
		assertEquals("title", sel.getName());
		assertEquals(1, sel.hitCount());
		Hits h = sel.hitsBySelectorValue("A Cup of Java");
		assertEquals("A Cup of Java", h.getSelectorValue());
		assertEquals(0, h.recordCount());
	}

	@Test
	public void fileAlreadyExistsError() throws Exception {
		GregorianCalendar cal = new GregorianCalendar();
		cal.add(GregorianCalendar.MINUTE, 1);

		ExecutionResource execution = submitExecution("/schemas/get-price-query-schema.xml",
				null,
				Arrays.asList(new String[] {"A Cup of Java"}),
				cal);

		Files.createFile(Paths.get("data", "responses", "response-" + execution.getId() + ".xml"));

		tryUntilTrue(100,
				3_000,
				"Timeout waiting for an execution to have error message.",
				uri -> retrieveExecution(execution.getSelfUri()).getExecution().getErrorMessage() != null,
				null);
	}

	private ExecutionResource runQuery(String schemaFileName, String filter, List<String> selectors) throws Exception {
		ExecutionResource execution = submitExecution(schemaFileName, filter, selectors);
		assertTrue(executionFinished(execution));
		return execution;
	}

	private ExecutionResource submitExecution(String schemaFileName, String filter, List<String> selectors) throws DatatypeConfigurationException, Exception {
		GregorianCalendar time = new GregorianCalendar();
		return submitExecution(schemaFileName, filter, selectors, time);
	}

	private ExecutionResource submitExecution(String schemaFileName, String filter, List<String> selectors, GregorianCalendar time) throws DatatypeConfigurationException, Exception {
		// Add an execution for current time
		Execution schedule = new Execution();
		schedule.setSchemaVersion(Versions.EXECUTION_BI);
		schedule.setUuid(UUID.randomUUID().toString().replaceAll("-", ""));
		schedule.setScheduledFor(DatatypeFactory.newInstance().newXMLGregorianCalendar(time));

		querier = createQuerier(schemaFileName, filter, selectors);

		Query xmlQuery = queryConverter.toXMLQuery(querier.getQuery());
		schedule.setQuery(xmlQuery);
		ExecutionResource execution = createExecution(dataSourceResource.getExecutionsUri(), schedule);
		log.info("Submitted execution {}", execution);
		return execution;
	}

	private Boolean executionFinished(ExecutionResource execution) {
		try {
			tryUntilTrue(40,
					5_000,
					"Timeout waiting for an execution to finish.",
					uri -> retrieveExecution(uri).getExecution().getCompletedOn() != null,
					execution.getSelfUri());
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private ClearTextQueryResponse waitAndGetResult(ExecutionResource execution) throws Exception {
		tryUntilTrue(20,
				5_000,
				"Timeout waiting for an execution result.",
				uri -> retrieveResults(uri).getResultResource().size() > 0,
				execution.getResultsUri());

		ResultResources results = retrieveResults(execution.getResultsUri());
		assertEquals(1, results.getResultResource().size());
		ResultResource resource = results.getResultResource().get(0);
		resultId = resource.getId();
		assertNotNull(resource.getId());
		assertNotNull(resource.getSelfUri());

		ResultResource resultWithPayload = retrieveResult(resource.getSelfUri());
		assertEquals(resource.getCreatedOn(), resultWithPayload.getCreatedOn());
		assertNotNull(resultWithPayload.getPayload());
		assertNotNull(resultWithPayload.getWindowStart());
		assertNotNull(resultWithPayload.getWindowEnd());

		log.info("Result window.start= {}, window.end={}",
				resultWithPayload.getWindowStart(),
				resultWithPayload.getWindowEnd());


		Response response = responseConverter.toCore(resultWithPayload.getPayload());
		ClearTextQueryResponse answer = decryptor.decrypt(response, querier.getQueryKey());
		log.info("Decrypted: {}.", answer);

		return answer;
	}

	private Querier createQuerier(String schemaFileName, String filterExp, List<String> selectors) throws Exception {
		byte[] bytes = IOUtils.resourceToByteArray(schemaFileName,
				this.getClass().getClassLoader());

		SchemaLoader loader = new SchemaLoader();
		QuerySchema querySchema = loader.loadQuerySchema(bytes);

		return querierFactory.encrypt(querySchema, selectors, DATA_CHUNK_SIZE, HASH_BIT_SIZE, filterExp);
	}


	private void validateDecryptedResults(ClearTextQueryResponse answer) throws Exception {
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
	private void validateWithResultCommand(String dataSchemaName, String dataSourceName, int executionId, int resultId) throws TimeoutException {
		String pipe = "|";
		final String regex = "^" + resultId + "\\s*" + pipe + " " + executionId + "\\s*" + pipe;
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
}
