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
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.replaceConfigurationFile;

import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;
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
import org.enquery.encryptedquery.xml.Versions;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class StandaloneIT extends BaseRestServiceItest {

	private static final Integer DATA_CHUNK_SIZE = 1;
	private static final Integer HASH_BIT_SIZE = 9;

	@Inject
	@Filter(timeout = 60_000)
	private EncryptQuery querierFactory;
	@Inject
	private QueryTypeConverter queryConverter;
	@Inject
	private DecryptResponse decryptor;
	@Inject
	private CryptoSchemeRegistry cryptoSchemeRegistry;

	private DataSourceResource dataSourceResource;
	private Querier querier;
	private ResponseTypeConverter responseConverter;

	@Configuration
	public Option[] configuration() {
		return combineOptions(super.baseOptions(),
				CoreOptions.options(
						replaceConfigurationFile("etc/org.enquery.encryptedquery.responder.standalone.runner.StandaloneQueryRunner-Books.cfg",
								getResourceAsFile("/etc/org.enquery.encryptedquery.responder.standalone.runner.StandaloneQueryRunner-Books.cfg")),

						replaceConfigurationFile("data/books.json", getResourceAsFile("/books.json"))));
	}

	@Before
	@Override
	public void init() throws Exception {
		super.init();

		installBooksDataSchema();

		DataSourceResources dataSources = retrieveDataSources("/responder/api/rest/datasources");

		dataSourceResource = dataSources.getDataSourceResource().stream()
				.filter(ds -> "Standalone-Books-JSON".equals(ds.getDataSource().getName()))
				.findFirst()
				.orElse(null);

		assertNotNull(dataSourceResource);
		assertEquals("Standalone-Books-JSON", dataSourceResource.getDataSource().getName());

		queryConverter = new QueryTypeConverter();
		queryConverter.setCryptoRegistry(cryptoSchemeRegistry);
		queryConverter.initialize();

		responseConverter = new ResponseTypeConverter();
		responseConverter.setQueryConverter(queryConverter);
		responseConverter.setSchemeRegistry(cryptoSchemeRegistry);
		responseConverter.initialize();
	}

	@Test
	public void noFilter() throws Exception {
		ExecutionResource execution = runQuery(
				"/schemas/get-price-query-schema.xml",
				null,
				Arrays.asList(new String[] {"A Cup of Java"}));

		validateSingleResult(execution);
	}


	@Test
	public void withFilter() throws Exception {

		ExecutionResource execution = runQuery("/schemas/find-book-by-author-query-schema.xml",
				"\"release date\" > DATE '2018-01-01'",
				Arrays.asList(new String[] {"Kevin Jones"}));

		ClearTextQueryResponse response = waitAndGetResult(execution);
		assertEquals(1, response.selectorCount());
		Selector selector = response.selectorByName("author");
		assertNotNull(selector);
		assertEquals(1, selector.hitCount());
		Hits kevinJonesHits = selector.hitsBySelectorValue("Kevin Jones");
		assertEquals(1, kevinJonesHits.recordCount());
		Record record = kevinJonesHits.recordByIndex(0);
		assertNotNull(record);
		Field titleField = record.fieldByName("title");
		assertNotNull(titleField);
		assertEquals("A Teaspoon of Java 1.8", titleField.getValue());
	}

	private ExecutionResource runQuery(String schemaFileName, String filter, List<String> selectors) throws Exception {
		// Add an execution for current time
		DatatypeFactory dtf = DatatypeFactory.newInstance();
		Execution ex = new Execution();
		ex.setSchemaVersion(Versions.EXECUTION_BI);
		ex.setUuid(UUID.randomUUID().toString().replaceAll("-", ""));

		GregorianCalendar cal = new GregorianCalendar();
		ex.setScheduledFor(dtf.newXMLGregorianCalendar(cal));

		querier = createQuerier(schemaFileName, filter, selectors);

		Query xmlQuery = queryConverter.toXMLQuery(querier.getQuery());
		ex.setQuery(xmlQuery);

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

	private void validateSingleResult(ExecutionResource execution) throws Exception {
		ClearTextQueryResponse response = waitAndGetResult(execution);

		assertEquals(1, response.selectorCount());
		Selector sel = response.selectorByName("title");

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

	private ClearTextQueryResponse waitAndGetResult(ExecutionResource execution) throws Exception {
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
}
