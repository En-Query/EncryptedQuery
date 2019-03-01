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
import static org.ops4j.pax.exam.CoreOptions.propagateSystemProperty;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.apache.commons.io.IOUtils;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.flink.FlinkConfigurationProperties;
import org.enquery.encryptedquery.flink.KafkaConfigurationProperties;
import org.enquery.encryptedquery.loader.SchemaLoader;
import org.enquery.encryptedquery.querier.decrypt.DecryptResponse;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.responder.it.util.FlinkDriver;
import org.enquery.encryptedquery.responder.it.util.KafkaDriver;
import org.enquery.encryptedquery.xml.schema.Configuration.Entry;
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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.Constants;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ResultsFlinkKafkaIT extends BaseRestServiceItest {

	private static final Integer DATA_CHUNK_SIZE = 1;
	private static final Integer HASH_BIT_SIZE = 9;
	private static final String SELECTOR = "A Cup of Java";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});

	@Inject
	@Filter(timeout = 60_000)
	private EncryptQuery querierFactory;
	@Inject
	private QueryTypeConverter queryConverter;
	@Inject
	private CryptoScheme crypto;
	@Inject
	private CryptoSchemeRegistry cryptoSchemeRegistry;

	private DataSchemaResource booksDataSchema;
	private DataSourceResource dataSourceResource;
	private static FlinkDriver flinkDriver = new FlinkDriver();
	private static KafkaDriver kafkaDriver = new KafkaDriver();
	private Querier querier;

	@Configuration
	public Option[] configuration() {
		final String booksJsonFile = Paths.get("target", "test-classes", "books.json").toAbsolutePath().toString();

		return combineOptions(super.baseOptions(),
				CoreOptions.options(
						systemProperty("books.test.data.file").value(booksJsonFile),
						propagateSystemProperty("flink.kafka.app")),
				flinkDriver.configuration(),
				kafkaDriver.configuration());
	}

	@ProbeBuilder
	@Override
	public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
		probe.setHeader(Constants.DYNAMICIMPORT_PACKAGE, "*");
		return probe;
	}

	@Before
	@Override
	public void init() throws Exception {
		super.init();

		installBooksDataSchema();
		booksDataSchema = retrieveDataSchemaByName("Books");
		installFlinkKafkaDataSource("flink-kafka-books", booksDataSchema.getDataSchema().getName());
		DataSourceResources dataSources = retrieveDataSources("/responder/api/rest/datasources");

		dataSourceResource = dataSources.getDataSourceResource().stream()
				.filter(ds -> "flink-kafka-books".equals(ds.getDataSource().getName()))
				.findFirst()
				.orElse(null);

		assertEquals("flink-kafka-books", dataSourceResource.getDataSource().getName());

		querier = createQuerier();
		kafkaDriver.init();
		flinkDriver.init();
	}

	@After
	public void cleanup() throws IOException, InterruptedException {
		flinkDriver.cleanup();
		kafkaDriver.cleanup();
	}

	@Test
	@Ignore
	public void fromLatest() throws Exception {
		kafkaDriver.send(Paths.get(System.getProperty("books.test.data.file")));
		String offset = "fromLatest";
		runQuery(offset);
	}


	@Test
	public void failedJob() throws Exception {
		kafkaDriver.send(Paths.get(System.getProperty("books.test.data.file")));
		// emulate error killing kafka
		kafkaDriver.cleanup();
		ExecutionResource execution = runQuery("fromEarliest");
		execution = retrieveExecution(execution.getSelfUri());
		assertNotNull(execution.getExecution().getErrorMessage());
	}

	@Test
	public void onceFromEarliest() throws Exception {
		kafkaDriver.send(Paths.get(System.getProperty("books.test.data.file")));
		ExecutionResource execution = runQuery("fromEarliest");
		validateSingleResult(execution);
	}

	@Test
	public void twiceFromEarliest() throws Exception {
		kafkaDriver.send(Paths.get(System.getProperty("books.test.data.file")));
		String offset = "fromEarliest";

		ExecutionResource execution = runQuery(offset);
		validateSingleResult(execution);

		execution = runQuery(offset);
		validateSingleResult(execution);
	}

	@Test
	@Ignore("not working")
	public void twiceFromLatestCommit() throws Exception {
		// feed Kafka
		kafkaDriver.send(Paths.get(System.getProperty("books.test.data.file")));
		String offset = "fromLatestCommit";

		// first run produces result
		ExecutionResource execution = runQuery(offset);
		validateSingleResult(execution);

		// second run produces no result
		execution = runQuery(offset);
		validateNoResult(execution);

		// feed Kafka again
		kafkaDriver.send(Paths.get(System.getProperty("books.test.data.file")));

		// thirs run produces result again
		execution = runQuery(offset);
		validateSingleResult(execution);
	}

	/**
	 * @param execution
	 * @throws Exception
	 */
	private void validateNoResult(ExecutionResource execution) throws Exception {
		assertTrue(retrieveResults(execution.getSelfUri()).getResultResource().size() == 0);
	}

	private ExecutionResource runQuery(String offset) throws DatatypeConfigurationException, Exception {
		// Add an execution for current time
		DatatypeFactory dtf = DatatypeFactory.newInstance();
		Execution ex = new Execution();
		GregorianCalendar cal = new GregorianCalendar();
		ex.setScheduledFor(dtf.newXMLGregorianCalendar(cal));

		Query xmlQuery = queryConverter.toXMLQuery(querier.getQuery());
		ex.setQuery(xmlQuery);

		ex.setConfiguration(makeConfiguration(offset));

		ExecutionResource execution = createExecution(dataSourceResource.getExecutionsUri(), ex);
		log.info("Submitted execution {}", execution);

		ExecutorService es = Executors.newCachedThreadPool();

		Future<Boolean> submit1 = es.submit(() -> executionFinished(execution));
		Future<Boolean> submit2 = es.submit(() -> executionFinished(execution));

		assertTrue(submit1.get() && submit2.get());

		return execution;
	}

	private Boolean executionFinished(ExecutionResource execution) {
		try {
			tryUntilTrue(30,
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

	private void validateSingleResult(ExecutionResource execution) throws Exception {
		tryUntilTrue(20,
				5_000,
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
		assertNotNull(resultWithPayload.getWindowStart());
		assertNotNull(resultWithPayload.getWindowEnd());

		log.info("Result window.start= {}, window.end={}",
				resultWithPayload.getWindowStart(),
				resultWithPayload.getWindowEnd());

		DecryptResponse dr = new DecryptResponse();
		ExecutorService es = Executors.newCachedThreadPool();
		dr.setCrypto(crypto);
		dr.setExecutionService(es);
		dr.activate();

		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoSchemeRegistry);
		queryConverter.initialize();

		ResponseTypeConverter responseConverter = new ResponseTypeConverter();
		responseConverter.setQueryConverter(queryConverter);
		responseConverter.setSchemeRegistry(cryptoSchemeRegistry);
		responseConverter.initialize();

		Response response = responseConverter.toCore(resultWithPayload.getPayload());
		ClearTextQueryResponse answer = dr.decrypt(response, querier.getQueryKey());
		answer.forEach(sel -> {
			log.info("Selector {}", sel);
			assertEquals("title", sel.getName());
			sel.forEachHits(h -> {
				assertEquals("A Cup of Java", h.getSelectorValue());
				assertEquals(1, h.recordCount());
				h.forEachRecord(r -> {
					r.forEachField(f -> {
						log.info("Field {}.", f);
						if (f.getName().equalsIgnoreCase("price")) {
							assertEquals(Double.valueOf("44.44"), f.getValue());
						} else if (f.getName().equalsIgnoreCase("release_dt")) {
							assertEquals("2001-01-03T11:18:00.000Z", f.getValue());
						}
					});
				});
			});
		});
	}

	private org.enquery.encryptedquery.xml.schema.Configuration makeConfiguration(String offset) {
		// Run from Earliest
		org.enquery.encryptedquery.xml.schema.Configuration configuration = new org.enquery.encryptedquery.xml.schema.Configuration();
		Entry config = new Entry();
		config.setKey(KafkaConfigurationProperties.OFFSET);
		config.setValue(offset);
		configuration.getEntry().add(config);

		// Runtime duration
		config = new Entry();
		config.setKey(FlinkConfigurationProperties.STREAM_RUNTIME_SECONDS);
		config.setValue("60");
		configuration.getEntry().add(config);

		// Window lenght
		config = new Entry();
		config.setKey(FlinkConfigurationProperties.WINDOW_LENGTH_IN_SECONDS);
		config.setValue("10");
		configuration.getEntry().add(config);
		return configuration;
	}

	private Querier createQuerier() throws Exception {
		byte[] bytes = IOUtils.resourceToByteArray("/schemas/get-price-query-schema.xml",
				this.getClass().getClassLoader());

		SchemaLoader loader = new SchemaLoader();
		QuerySchema querySchema = loader.loadQuerySchema(bytes);

		return querierFactory.encrypt(querySchema, SELECTORS, true, DATA_CHUNK_SIZE, HASH_BIT_SIZE);
	}
}
