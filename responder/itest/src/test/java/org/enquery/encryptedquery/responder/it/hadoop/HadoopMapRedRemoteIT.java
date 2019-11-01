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
package org.enquery.encryptedquery.responder.it.hadoop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.replaceConfigurationFile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.apache.commons.io.IOUtils;
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
import org.enquery.encryptedquery.responder.it.rest.BaseRestServiceItest;
import org.enquery.encryptedquery.responder.it.util.HadoopDriver;
import org.enquery.encryptedquery.responder.it.util.HadoopJsonRunnerConfigurator;
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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;

/**
 * This is to test MapReduce against a remote server. You'll need to edit the file
 * `remote-hadoop-conf.xml` in the `src/test/resources` directory to specify the remote server
 * address. In this file, replace `localhost` by the actual host.
 * 
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
@Ignore
public class HadoopMapRedRemoteIT extends BaseRestServiceItest {

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
	private CryptoSchemeRegistry cryptoSchemeRegistry;
	@Inject
	private DecryptResponse decryptor;

	private DataSchemaResource booksDataSchema;
	private DataSourceResource dataSourceResource;
	private static HadoopDriver hadoopDriver = new HadoopDriver();
	private Querier querier;

	@Configuration
	public Option[] configuration() throws URISyntaxException, IOException {
		final String booksJsonFile = Paths.get("target", "test-classes", "books.json").toAbsolutePath().toString();

		return combineOptions(super.baseOptions(),
				CoreOptions.options(
						systemProperty("books.test.data.file").value(booksJsonFile),
						replaceConfigurationFile("etc/remote-hadoop-conf.xml", getResourceAsFile("/remote-hadoop-conf.xml"))),
				hadoopDriver.configuration());
	}

	@Before
	@Override
	public void init() throws Exception {
		super.init();

		installBooksDataSchema();
		booksDataSchema = retrieveDataSchemaByName("Books");

		querier = createQuerier();
	}

	@Test
	public void happyPath() throws Exception {
		installHadoopDataSource();
		ExecutionResource execution = runQuery();
		validateSingleResult(execution);
	}


	/**
	 * @param execution
	 * @throws Exception
	 */
	@SuppressWarnings("unused")
	private void validateNoResult(ExecutionResource execution) throws Exception {
		assertTrue(retrieveResults(execution.getSelfUri()).getResultResource().size() == 0);
	}

	private ExecutionResource runQuery() throws DatatypeConfigurationException, Exception {
		// Add an execution for current time
		DatatypeFactory dtf = DatatypeFactory.newInstance();
		Execution ex = new Execution();
		ex.setSchemaVersion(Versions.EXECUTION_BI);
		ex.setUuid(UUID.randomUUID().toString().replaceAll("-", ""));

		GregorianCalendar cal = new GregorianCalendar();
		ex.setScheduledFor(dtf.newXMLGregorianCalendar(cal));

		Query xmlQuery = queryConverter.toXMLQuery(querier.getQuery());
		ex.setQuery(xmlQuery);

		ExecutionResource execution = createExecution(dataSourceResource.getExecutionsUri(), ex);
		log.info("Submitted execution {}", execution);

		tryUntilTrue(60,
				5_000,
				"Timeout waiting for an execution to finish.",
				uri -> retrieveExecution(uri).getExecution().getCompletedOn() != null,
				execution.getSelfUri());

		return execution;
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


		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoSchemeRegistry);
		queryConverter.initialize();

		ResponseTypeConverter responseConverter = new ResponseTypeConverter();
		responseConverter.setQueryConverter(queryConverter);
		responseConverter.setSchemeRegistry(cryptoSchemeRegistry);
		responseConverter.initialize();

		Response response = responseConverter.toCore(resultWithPayload.getPayload());
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


	private Querier createQuerier() throws Exception {
		byte[] bytes = IOUtils.resourceToByteArray("/schemas/get-price-query-schema.xml",
				this.getClass().getClassLoader());

		SchemaLoader loader = new SchemaLoader();
		QuerySchema querySchema = loader.loadQuerySchema(bytes);

		return querierFactory.encrypt(querySchema, SELECTORS, DATA_CHUNK_SIZE, HASH_BIT_SIZE);
	}


	private void installHadoopDataSource() throws Exception {
		final String dataSchemaName = booksDataSchema.getDataSchema().getName();
		final String dataSourceName = "hadoop-books-json";// + ((useVersion1) ? "-v1" : "v2");

		// Add a QueryRunner
		HadoopJsonRunnerConfigurator runnerConfigurator = new HadoopJsonRunnerConfigurator(confAdmin);
		runnerConfigurator.create(dataSourceName,
				dataSchemaName,
				Paths.get("etc/remote-hadoop-conf.xml").toAbsolutePath().toString(),
				"enquery");

		// give enough time for the QueryRunner to be registered
		waitUntilQueryRunnerRegistered(dataSourceName);

		DataSourceResources dataSources = retrieveDataSources("/responder/api/rest/datasources");
		dataSourceResource =
				dataSources.getDataSourceResource().stream()
						.filter(ds -> dataSourceName.equals(ds.getDataSource().getName()))
						.findFirst()
						.orElse(null);
	}

}
