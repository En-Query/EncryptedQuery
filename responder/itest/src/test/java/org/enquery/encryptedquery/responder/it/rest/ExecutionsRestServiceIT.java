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

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.inject.Inject;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.IOUtils;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.loader.SchemaLoader;
import org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptionPropertiesBuilder;
import org.enquery.encryptedquery.querier.wideskies.encrypt.Querier;
import org.enquery.encryptedquery.querier.wideskies.encrypt.QuerierFactory;
import org.enquery.encryptedquery.xml.schema.DataSchemaResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResources;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.ExecutionResources;
import org.enquery.encryptedquery.xml.schema.Query;
import org.enquery.encryptedquery.xml.schema.ResultResources;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
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
public class ExecutionsRestServiceIT extends BaseRestServiceItest {

	@Inject
	@Filter(timeout = 60_000)
	private QuerierFactory querierFactory;

	private static final String DATA_SOURCE_NAME = "test-name";

	private DataSchemaResource booksDataSchema;
	private DataSourceResource dataSourceResource;

	@Configuration
	public Option[] configuration() {
		return super.baseOptions();
	}

	@Before
	@Override
	public void init() throws Exception {
		super.init();

		installBooksDataSchema();
		booksDataSchema = retrieveDataSchemaByName("Books");
		installDataSource(DATA_SOURCE_NAME, booksDataSchema.getDataSchema().getName());

		DataSourceResources dataSources = retrieveDataSources(booksDataSchema.getDataSourcesUri());
		assertEquals(1, dataSources.getDataSourceResource().size());
		dataSourceResource = dataSources.getDataSourceResource().get(0);
	}

	@Test
	public void submitInvalid() throws Exception {
		Message message = invoke(
				dataSourceResource.getExecutionsUri(),
				500,
				null,
				itCamelContext.getEndpoint("mock:create-result", MockEndpoint.class),
				"direct:create-raw",
				"invalid execution").getReceivedExchanges()
						.get(0)
						.getMessage();

		// Message message = create(
		// dataSourceResource.getExecutionsUri(),
		// 500,
		// "invalid execution")
		// .getReceivedExchanges()
		// .get(0)
		// .getMessage();

		assertEquals("", message.getBody(String.class));
	}

	@Test
	public void happyPath() throws Exception {
		// First retrieve executions without data, empty list is returned
		ExecutionResources executions = retrieveExecutions(dataSourceResource.getExecutionsUri());
		assertEquals(0, executions.getExecutionResource().size());

		// Now add an execution and test its retrieval
		DatatypeFactory dtf = DatatypeFactory.newInstance();
		Execution ex = new Execution();
		GregorianCalendar cal = new GregorianCalendar();
		cal.add(GregorianCalendar.DAY_OF_YEAR, 1);

		Querier querier = createQuerier();

		ex.setScheduledFor(dtf.newXMLGregorianCalendar(cal));

		Query xmlQuery = queryConverter.toXMLQuery(querier.getQuery());
		ex.setQuery(xmlQuery);

		ExecutionResource created = createExecution(dataSourceResource.getExecutionsUri(), ex);
		validateExecution(ex, created);

		ExecutionResource retrieved = retrieveExecution(created.getSelfUri());
		validateExecution(ex, retrieved);

		executions = retrieveExecutions(dataSourceResource.getExecutionsUri());
		assertEquals(1, executions.getExecutionResource().size());
		retrieved = executions.getExecutionResource().get(0);
		validateExecution(ex, retrieved);

		retrieved = retrieveExecution(retrieved.getSelfUri());
		assertNotNull(retrieved);

		ResultResources results = retrieveResults(retrieved.getResultsUri());
		assertNotNull(results);
		assertEquals(0, results.getResultResource().size());
	}

	private void validateExecution(Execution expected, ExecutionResource actual) {
		assertNotNull(expected);
		assertNotNull(actual);
		assertNotNull(actual.getId());
		assertNotNull(actual.getResultsUri());
		assertNotNull(actual.getDataSourceUri());
		assertNotNull(actual.getSelfUri());

		assertEquals(Integer.toString(actual.getId()), Paths.get(actual.getSelfUri()).getFileName().toString());

		// times are only accurate to the minute
		XMLGregorianCalendar expectedTime = (XMLGregorianCalendar) expected.getScheduledFor().clone();
		expectedTime.setMillisecond(0);
		expectedTime.setSecond(0);

		XMLGregorianCalendar actualTime = (XMLGregorianCalendar) actual.getExecution().getScheduledFor().clone();
		actualTime.setMillisecond(0);
		actualTime.setSecond(0);

		assertEquals(expectedTime, actualTime);
	}

	private static final Integer DATA_PARTITION_BITSIZE = 8;
	private static final Integer HASH_BIT_SIZE = 12;
	public static final int paillierBitSize = 384;
	public static final int certainty = 128;
	private static final String SELECTOR = "A Cup of Java";
	private static final List<String> SELECTORS = Arrays.asList(new String[] {SELECTOR});
	private QueryTypeConverter queryConverter = new QueryTypeConverter();;

	private Querier createQuerier() throws Exception {
		byte[] bytes = IOUtils.resourceToByteArray("/schemas/get-price-query-schema.xml",
				this.getClass().getClassLoader());

		SchemaLoader loader = new SchemaLoader();
		QuerySchema querySchema = loader.loadQuerySchema(bytes);

		Properties baseTestEncryptionProperties = EncryptionPropertiesBuilder
				.newBuilder()
				.dataPartitionBitSize(DATA_PARTITION_BITSIZE)
				.hashBitSize(HASH_BIT_SIZE)
				.paillierBitSize(paillierBitSize)
				.certainty(certainty)
				.embedSelector(true)
				.queryType("Test")
				.build();

		return querierFactory.createQuerier(querySchema,
				UUID.randomUUID(),
				SELECTORS,
				baseTestEncryptionProperties);
	}
}
