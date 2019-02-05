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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.xml.bind.JAXBException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpComponent;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Decryption;
import org.enquery.encryptedquery.querier.data.entity.json.DecryptionCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DecryptionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Query;
import org.enquery.encryptedquery.querier.data.entity.json.QueryCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QueryResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.ResourceCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.RestResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ResultCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ResultResponse;
import org.enquery.encryptedquery.querier.data.entity.json.RetrievalCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.RetrievalResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Schedule;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleStatus;
import org.enquery.encryptedquery.querier.it.AbstractQuerierItest;
import org.enquery.encryptedquery.xml.schema.ClearTextResponse;
import org.enquery.encryptedquery.xml.transformation.ClearTextResponseTypeConverter;

public class BaseRestServiceItest extends AbstractQuerierItest {

	private static final String DEFAULT_ACCEPT = "application/vnd.encryptedquery.enclave+json; version=1";

	protected DefaultCamelContext itCamelContext;
	protected ProducerTemplate testProducer;
	private MockEndpoint dataSourcesResultMock;
	private MockEndpoint dataSourceResultMock;
	private MockEndpoint dataSchemaResultMock;
	protected MockEndpoint dataSchemasResultMock;
	protected MockEndpoint resourcesResultMock;

	protected String dataSchemasUri;
	// protected DataSchema bookCatalogDataSchema;
	protected DataSchema phoneRecordDataSchema;
	private MockEndpoint querySchemaResultMock;
	private MockEndpoint querySchemasResultMock;
	private MockEndpoint querySchemaCreateResultMock;
	private MockEndpoint queriesResultMock;
	private MockEndpoint queryResultMock;

	protected int queryCount = 0;

	public void init() throws Exception {
		truncateTables();
		startCamelProducer();
		setupRoutes();
		dataSchemasUri = resolveDataSchemasUri();
		assertNotNull(dataSchemasUri);
	}

	protected void setupRoutes() throws Exception {
		final JacksonDataFormat dataSourcesFormat = new JacksonDataFormat(DataSourceCollectionResponse.class);
		final JacksonDataFormat dataSourceFormat = new JacksonDataFormat(DataSourceResponse.class);
		final JacksonDataFormat dataSchemaFormat = new JacksonDataFormat(DataSchemaResponse.class);
		final JacksonDataFormat dataSchemasFormat = new JacksonDataFormat(DataSchemaCollectionResponse.class);
		final JacksonDataFormat resourcesFormat = new JacksonDataFormat(ResourceCollectionResponse.class);
		final JacksonDataFormat querySchemaFormat = new JacksonDataFormat(QuerySchemaResponse.class);
		final JacksonDataFormat querySchemasFormat = new JacksonDataFormat(QuerySchemaCollectionResponse.class);
		final JacksonDataFormat queryFormat = new JacksonDataFormat(QueryResponse.class);
		final JacksonDataFormat queriesFormat = new JacksonDataFormat(QueryCollectionResponse.class);

		itCamelContext.addRoutes(new RouteBuilder() {
			@Override
			public void configure() {

				from("direct:retrieve-resources")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(resourcesFormat)
						.end()
						.to("mock:resources-result");

				from("direct:datasources")
						.to("http4://localhost:8182/?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(dataSourcesFormat)
						.end()
						.to("mock:datasources-result");

				from("direct:datasource")
						.to("http4://localhost:8182/?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(dataSourceFormat)
						.end()
						.to("mock:datasource-result");

				from("direct:retrieve-dataschema")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(dataSchemaFormat)
						.end()
						.to("mock:dataschema-result");

				from("direct:retrieve-dataschemas")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(dataSchemasFormat)
						.end()
						.to("mock:dataschemas-result");

				from("direct:retrieve-queryschema")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(querySchemaFormat)
						.end()
						.to("mock:queryschema-result");

				from("direct:create-queryschema")
						.marshal(querySchemaFormat)
						.log("${body}")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 201"))
						.unmarshal(querySchemaFormat)
						.end()
						.to("mock:queryschema-create-result");

				from("direct:create-invalid-queryschema")
						.log("${body}")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.to("mock:queryschema-create-result");

				from("direct:retrieve-queryschemas")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(querySchemasFormat)
						.end()
						.to("mock:queryschemas-result");

				from("direct:retrieve-query")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(queryFormat)
						.end()
						.to("mock:query-result");

				from("direct:retrieve-queries")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(queriesFormat)
						.end()
						.to("mock:queries-result");

				from("direct:create-query")
						.marshal(queryFormat)
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 201"))
						.unmarshal(queryFormat)
						.end()
						.to("mock:query-create-result");

				from("direct:retrieve-schedules")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(new JacksonDataFormat(ScheduleCollectionResponse.class))
						.end()
						.to("mock:schedules-result");

				JacksonDataFormat scheduleFormat = new JacksonDataFormat(ScheduleResponse.class);
				from("direct:retrieve-schedule")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(scheduleFormat)
						.end()
						.to("mock:schedule-result");

				from("direct:create-schedule")
						.marshal(scheduleFormat)
						.log("${body}")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 201"))
						.unmarshal(scheduleFormat)
						.end()
						.to("mock:schedule-create-result");

				from("direct:retrieve-results")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(new JacksonDataFormat(ResultCollectionResponse.class))
						.end()
						.to("mock:results-result");

				from("direct:retrieve-result")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(new JacksonDataFormat(ResultResponse.class))
						.end()
						.to("mock:result-result");

				from("direct:retrieve-retrievals")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(new JacksonDataFormat(RetrievalCollectionResponse.class))
						.end()
						.to("mock:retrievals-result");

				from("direct:retrieve-retrieval")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(new JacksonDataFormat(RetrievalResponse.class))
						.end()
						.to("mock:retrieval-result");

				from("direct:create-retrieval")
						.setHeader(Exchange.HTTP_METHOD, constant("POST"))
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 201"))
						.unmarshal(new JacksonDataFormat(RetrievalResponse.class))
						.end()
						.to("mock:create-retrieval-result");

				from("direct:retrieve-decryptions")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(new JacksonDataFormat(DecryptionCollectionResponse.class))
						.end()
						.to("mock:decryptions-result");

				from("direct:retrieve-decryption")
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(new JacksonDataFormat(DecryptionResponse.class))
						.end()
						.to("mock:decryption-result");

				from("direct:create-decryption")
						.setHeader(Exchange.HTTP_METHOD, constant("POST"))
						.to("http4://localhost:8182?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 201"))
						.unmarshal(new JacksonDataFormat(DecryptionResponse.class))
						.end()
						.to("mock:create-decryption-result");

			}
		});
		dataSourcesResultMock = itCamelContext.getEndpoint("mock:datasources-result", MockEndpoint.class);
		dataSourceResultMock = itCamelContext.getEndpoint("mock:datasource-result", MockEndpoint.class);
		dataSchemaResultMock = itCamelContext.getEndpoint("mock:dataschema-result", MockEndpoint.class);
		dataSchemasResultMock = itCamelContext.getEndpoint("mock:dataschemas-result", MockEndpoint.class);
		resourcesResultMock = itCamelContext.getEndpoint("mock:resources-result", MockEndpoint.class);
		querySchemaResultMock = itCamelContext.getEndpoint("mock:queryschema-result", MockEndpoint.class);
		querySchemasResultMock = itCamelContext.getEndpoint("mock:queryschemas-result", MockEndpoint.class);
		querySchemaCreateResultMock = itCamelContext.getEndpoint("mock:queryschema-create-result", MockEndpoint.class);

		queriesResultMock = itCamelContext.getEndpoint("mock:queries-result", MockEndpoint.class);
		queryResultMock = itCamelContext.getEndpoint("mock:query-result", MockEndpoint.class);
	}

	protected void startCamelProducer() throws Exception {
		itCamelContext = new DefaultCamelContext();
		itCamelContext.addComponent("http4", new HttpComponent());
		itCamelContext.setTracing(true);
		itCamelContext.setStreamCaching(true);
		itCamelContext.setName(this.getClass().getSimpleName());
		itCamelContext.start();
		testProducer = itCamelContext.createProducerTemplate();
		testProducer.start();
	}

	protected QueryCollectionResponse retrieveQueries(QuerySchemaResponse querySchemaResponse) {
		assertNotNull(querySchemaResponse);
		assertNotNull(querySchemaResponse.getData());
		assertNotNull(querySchemaResponse.getData().getQueriesUri());
		String queriesUri = querySchemaResponse.getData().getQueriesUri();
		assertNotNull(queriesUri);
		return retrieveQueries(queriesUri);
	}

	protected QueryCollectionResponse retrieveQueries(String queriesUri) {
		assertNotNull(queriesUri);

		return invoke(queriesUri,
				200,
				DEFAULT_ACCEPT,
				queriesResultMock,
				"direct:retrieve-queries")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(QueryCollectionResponse.class);
	}

	protected ScheduleCollectionResponse retrieveSchedules(QueryResponse queryResponse) {
		return retrieveSchedules(queryResponse.getData().getSchedulesUri());
	}

	protected ScheduleCollectionResponse retrieveSchedules(String schedulesUri) {
		assertNotNull(schedulesUri);
		return invoke(schedulesUri,
				200,
				DEFAULT_ACCEPT,
				itCamelContext.getEndpoint("mock:schedules-result", MockEndpoint.class),
				"direct:retrieve-schedules")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(ScheduleCollectionResponse.class);
	}

	protected ScheduleResponse retrieveSchedule(String uri) {
		assertNotNull(uri);
		return invoke(uri,
				200,
				DEFAULT_ACCEPT,
				itCamelContext.getEndpoint("mock:schedule-result", MockEndpoint.class),
				"direct:retrieve-schedule")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(ScheduleResponse.class);
	}

	protected ScheduleResponse createSchedule(String uri, Schedule sch) {
		Message message = invoke(uri,
				201,
				null,
				itCamelContext.getEndpoint("mock:schedule-create-result", MockEndpoint.class),
				"direct:create-schedule", sch)
						.getReceivedExchanges()
						.get(0)
						.getMessage();

		ScheduleResponse result = message.getBody(ScheduleResponse.class);

		String location = message.getHeader("Location", String.class);
		log.info("Location is: " + location);

		assertEquals(result.getData().getSelfUri(), location);
		return result;
	}

	protected QueryResponse retrieveQuery(String uri) {
		assertNotNull(uri);
		return invoke(uri,
				200,
				DEFAULT_ACCEPT,
				queryResultMock,
				"direct:retrieve-query")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(QueryResponse.class);
	}

	protected Message createInvalidQuerySchema(String uri, String qs) {
		return invoke(uri,
				500,
				null,
				querySchemaCreateResultMock,
				"direct:create-invalid-queryschema", qs)
						.getReceivedExchanges()
						.get(0)
						.getMessage();

	}

	protected QuerySchemaResponse createQuerySchema(String uri, QuerySchema qs) {
		Message message = invoke(uri,
				201,
				null,
				querySchemaCreateResultMock,
				"direct:create-queryschema", qs)
						.getReceivedExchanges()
						.get(0)
						.getMessage();

		QuerySchemaResponse result = message.getBody(QuerySchemaResponse.class);

		String location = message.getHeader("Location", String.class);
		log.info("Location is: " + location);

		assertEquals(result.getData().getSelfUri(), location);
		return result;
	}

	protected QueryResponse postQuery(String uri, Query q) {

		Message message = invoke(uri,
				201,
				null,
				itCamelContext.getEndpoint("mock:query-create-result", MockEndpoint.class),
				"direct:create-query", q)
						.getReceivedExchanges()
						.get(0)
						.getMessage();

		QueryResponse result = message.getBody(QueryResponse.class);

		String location = message.getHeader("Location", String.class);
		log.info("Location is: " + location);

		assertEquals(result.getData().getSelfUri(), location);
		return result;
	}

	protected QuerySchemaCollectionResponse retrieveQuerySchemas(DataSchemaResponse dataSchema) {
		assertNotNull(dataSchema);
		return retrieveQuerySchemas(dataSchema.getData());
	}

	protected QuerySchemaCollectionResponse retrieveQuerySchemas(DataSchema dataSchema) {
		assertNotNull(dataSchema);
		assertNotNull(dataSchema.getQuerySchemasUri());
		String querySchemasUri = dataSchema.getQuerySchemasUri();
		return retrieveQuerySchemas(querySchemasUri);
	}


	protected QuerySchemaCollectionResponse retrieveQuerySchemas(String querySchemasUri) {
		try {
			return invokeQuerySchemas(querySchemasUri, 200)
					.getReceivedExchanges()
					.get(0)
					.getMessage()
					.getBody(QuerySchemaCollectionResponse.class);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	protected MockEndpoint invokeQuerySchemas(String querySchemasUri, final int expectedResponseCode) throws InterruptedException {

		return invokeQuerySchemas(querySchemasUri, expectedResponseCode, DEFAULT_ACCEPT);
	}

	protected MockEndpoint invokeQuerySchemas(String querySchemasUri, final int expectedResponseCode, final String acceptValue) throws InterruptedException {
		return invoke(querySchemasUri,
				expectedResponseCode,
				acceptValue,
				querySchemasResultMock,
				"direct:retrieve-queryschemas");
	}

	protected QuerySchemaResponse retrieveQuerySchema(String uri) {
		assertNotNull(uri);
		return invokeQuerySchema(uri, 200)
				.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(QuerySchemaResponse.class);
	}

	protected MockEndpoint invokeQuerySchema(String uri, final int expectedResponseCode) {
		return invokeQuerySchema(uri, expectedResponseCode, DEFAULT_ACCEPT);
	}

	protected MockEndpoint invokeQuerySchema(String uri, final int expectedResponseCode, final String acceptValue) {
		return invoke(uri, expectedResponseCode, acceptValue, querySchemaResultMock, "direct:retrieve-queryschema");
	}

	protected DataSourceCollectionResponse retrieveDataSources(DataSchemaResponse dataSchema) {
		return retrieveDataSources(dataSchema.getData());
	}

	protected DataSourceCollectionResponse retrieveDataSources(DataSchema dataSchema) {
		assertNotNull(dataSchema);
		assertNotNull(dataSchema.getDataSourcesUri());

		try {
			return invokeDataSources(dataSchema, 200)
					.getReceivedExchanges()
					.get(0)
					.getMessage()
					.getBody(DataSourceCollectionResponse.class);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	protected DataSourceResponse retrieveDataSource(String uri) throws InterruptedException {
		assertNotNull(uri);

		return invokeDataSource(uri, 200)
				.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(DataSourceResponse.class);
	}

	protected MockEndpoint invokeDataSource(String uri, final int expectedResponseCode) throws InterruptedException {
		return invokeDataSource(uri, expectedResponseCode, DEFAULT_ACCEPT);
	}

	protected MockEndpoint invokeDataSources(DataSchema dataSchema, final int expectedResponseCode) throws InterruptedException {
		return invokeDataSources(dataSchema, expectedResponseCode, DEFAULT_ACCEPT);
	}


	protected MockEndpoint invokeDataSources(DataSchema dataSchema, final int expectedResponseCode, final String acceptValue) throws InterruptedException {
		return invoke(dataSchema.getDataSourcesUri(),
				expectedResponseCode,
				acceptValue,
				dataSourcesResultMock,
				"direct:datasources");
	}

	protected MockEndpoint invokeDataSource(String uri, final int expectedResponseCode, final String acceptValue) {
		return invoke(uri, expectedResponseCode, acceptValue, dataSourceResultMock, "direct:datasource");
	}

	protected DataSchemaResponse retrieveDataSchemaByName(String dataSchemaName) {
		return retrieveDataSchema(
				retrieveDataSchemas()
						.getData()
						.stream()
						.filter(ds -> {
							return ds.getName().equals(dataSchemaName);
						})
						.findFirst()
						.get()
						.getSelfUri());
	}

	protected DataSchemaResponse retrieveDataSchema(String uri) {
		assertNotNull(uri);

		DataSchemaResponse retrievedDataSchema;
		try {
			retrievedDataSchema = invokeDataSchema(uri, 200)
					.getReceivedExchanges()
					.get(0)
					.getMessage()
					.getBody(DataSchemaResponse.class);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		return retrievedDataSchema;
	}

	protected MockEndpoint invokeDataSchema(String dataSchemaUri, int resultCode) throws InterruptedException {
		return invokeDataSchema(dataSchemaUri, resultCode, DEFAULT_ACCEPT);
	}

	protected MockEndpoint invokeDataSchema(String uri, int expectedResponseCode, String acceptValue) throws InterruptedException {
		return invoke(uri, expectedResponseCode, acceptValue, dataSchemaResultMock, "direct:retrieve-dataschema");
	}

	protected DataSchemaCollectionResponse retrieveDataSchemas() {
		return invokeDataSchemas(200)
				.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(DataSchemaCollectionResponse.class);
	}

	protected MockEndpoint invokeDataSchemas(int resultCode) {
		return invokeDataSchemas(resultCode, DEFAULT_ACCEPT);
	}

	protected MockEndpoint invokeDataSchemas(int resultCode, String acceptValue) {
		return invoke(dataSchemasUri, resultCode, acceptValue, dataSchemasResultMock, "direct:retrieve-dataschemas");
	}

	protected String resolveDataSchemasUri() throws InterruptedException {
		ResourceCollectionResponse resources = invoke("/querier/api/rest/",
				200,
				DEFAULT_ACCEPT,
				resourcesResultMock,
				"direct:retrieve-resources")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(ResourceCollectionResponse.class);
		assertNotNull(resources);

		return resources.getData().stream()
				.filter(r -> "dataschema".equals(r.getId()))
				.findFirst()
				.get()
				.getSelfUri();
	}

	private MockEndpoint invoke(String remoteUri, int expectedResponseCode, String acceptValue, MockEndpoint mock, String localUri) {
		return invoke(remoteUri, expectedResponseCode, acceptValue, mock, localUri, null);
	}

	private MockEndpoint invoke(String remoteUri, int expectedResponseCode, String acceptValue, MockEndpoint mock, String localUri, Object payload) {
		Map<String, Object> headers = new HashMap<>();
		headers.put(Exchange.HTTP_PATH, remoteUri);
		if (acceptValue != null) {
			headers.put("Accept", acceptValue);
		}
		mock.reset();
		mock.expectedMessageCount(1);
		mock.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, expectedResponseCode);
		if (expectedResponseCode == 200 || expectedResponseCode == 201) {
			mock.expectedHeaderReceived("Content-Type", DEFAULT_ACCEPT);
		}

		testProducer.sendBodyAndHeaders(localUri, payload, headers);
		try {
			mock.assertIsSatisfied();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return mock;
	}

	protected QueryResponse createQueryAndWaitForEncryption(Query query) throws Exception {
		org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema ds = sampleData.createDataSchema();
		dataSchemaRepo.add(ds);

		org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema qs1 = sampleData.createJPAQuerySchema(ds);
		querySchemaRepo.add(qs1);

		org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse dataSchema =
				retrieveDataSchemaForName(ds.getName());

		org.enquery.encryptedquery.querier.data.entity.json.QuerySchema querySchema =
				retrieveQuerySchemaForName(qs1.getName(), dataSchema).getData();

		return createQueryAndWaitForEncryption(querySchema.getQueriesUri(), query);
	}

	protected QueryResponse createQueryAndWaitForEncryption(String uri, Query q) throws Exception {
		QueryResponse queryResponse = postQuery(uri, q);
		assertEquals(q.getName(), queryResponse.getData().getName());
		assertNotNull(queryResponse.getData().getId());

		QueryResponse retrieved = retrieveQuery(queryResponse.getData().getSelfUri());
		assertEquals(queryResponse.getData().getId(), retrieved.getData().getId());
		assertEquals(queryResponse.getData().getName(), retrieved.getData().getName());

		tryUntilTrue(120, 2_000, "Timeout waiting for Query Encryption.",
				op -> {
					return "Encrypted".equals(
							retrieveQuery(
									queryResponse.getData().getSelfUri())
											.getData()
											.getStatus()
											.toString());
				},
				null);

		return retrieveQuery(queryResponse.getData().getSelfUri());
	}

	protected org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaResponse retrieveQuerySchemaForName(String querySchemaName, org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse dataSchema) {
		return retrieveQuerySchema(
				retrieveQuerySchemas(dataSchema)
						.getData()
						.stream()
						.filter(qs -> querySchemaName.equals(qs.getName()))
						.findFirst()
						.get()
						.getSelfUri());
	}

	protected org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse retrieveDataSchemaForName(String dataSchemaName) {
		String dataSchemaUri = retrieveDataSchemas()
				.getData()
				.stream()
				.filter(ds -> dataSchemaName.equals(ds.getName()))
				.findFirst()
				.get()
				.getSelfUri();


		org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse dataSchema =
				retrieveDataSchema(dataSchemaUri);

		assertEquals(dataSchemaName, dataSchema.getData().getName());
		assertNotNull(dataSchema);
		return dataSchema;
	}

	protected ResultCollectionResponse retrieveResults(ScheduleResponse schedule) {
		return retrieveResults(schedule.getData().getResultsUri());
	}

	protected ResultCollectionResponse retrieveResults(String resultsUri) {
		assertNotNull(resultsUri);
		return invoke(resultsUri,
				200,
				DEFAULT_ACCEPT,
				itCamelContext.getEndpoint("mock:results-result", MockEndpoint.class),
				"direct:retrieve-results")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(ResultCollectionResponse.class);
	}

	protected ResultResponse retrieveResult(String uri) {
		assertNotNull(uri);
		return invoke(uri,
				200,
				DEFAULT_ACCEPT,
				itCamelContext.getEndpoint("mock:result-result", MockEndpoint.class),
				"direct:retrieve-result")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(ResultResponse.class);
	}

	protected RetrievalCollectionResponse retrieveRetrievals(ResultResponse resultResponse) {
		return retrieveRetrievals(resultResponse.getData().getRetrievalsUri());
	}

	protected RetrievalCollectionResponse retrieveRetrievals(String resultsUri) {
		assertNotNull(resultsUri);
		return invoke(resultsUri,
				200,
				DEFAULT_ACCEPT,
				itCamelContext.getEndpoint("mock:retrievals-result", MockEndpoint.class),
				"direct:retrieve-retrievals")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(RetrievalCollectionResponse.class);
	}


	protected RetrievalResponse retrieveRetrieval(String uri) {
		assertNotNull(uri);
		return invoke(uri,
				200,
				DEFAULT_ACCEPT,
				itCamelContext.getEndpoint("mock:retrieval-result", MockEndpoint.class),
				"direct:retrieve-retrieval")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(RetrievalResponse.class);
	}

	protected RetrievalResponse createRetrieval(String uri) {
		Message message = invoke(uri,
				201,
				null,
				itCamelContext.getEndpoint("mock:create-retrieval-result", MockEndpoint.class),
				"direct:create-retrieval")
						.getReceivedExchanges()
						.get(0)
						.getMessage();

		RetrievalResponse result = message.getBody(RetrievalResponse.class);

		String location = message.getHeader("Location", String.class);
		log.info("Location is: " + location);

		assertEquals(result.getData().getSelfUri(), location);
		return result;
	}

	protected <T> void forEach(RestResponse<Collection<T>> c, Consumer<? super T> action) {
		c.getData().stream().forEach(action);
	}

	// protected RetrievalResponse scheduleQueryAndRetrieval() throws Exception {
	// ResultResponse resultResponse = postScheduleAndWaitForResult();
	// return postRetrieval(resultResponse);
	// }

	protected ResultResponse waitForScheduleResult(ScheduleResponse schedule) throws Exception {
		tryUntilTrue(60, 3_000, "Timeout waiting for Schedule to Complete.", op -> {
			return ScheduleStatus.Complete.equals(retrieveSchedule(schedule.getData().getSelfUri()).getData().getStatus());
		}, null);

		tryUntilTrue(60,
				3_000,
				"Timeout waiting for Results to become available.",
				op -> retrieveResults(schedule).getData().size() > 0,
				null);

		return retrieveResult(
				retrieveResults(schedule)
						.getData()
						.iterator()
						.next()
						.getSelfUri());
	}

	protected RetrievalResponse postRetrieval(ResultResponse resultResponse) throws Exception {
		return createRetrieval(resultResponse.getData().getRetrievalsUri());
	}

	protected ScheduleResponse postSchedule(
			String schedulesUri,
			// String dataSchemaName,
			// String dataSourceName,
			// QuerySchema querySchema,
			String dataSoureId,
			Map<String, String> dataSourceParameters) throws Exception {

		// DataSchemaResponse jsonDataSchema = retrieveDataSchemaByName(dataSchemaName);
		// DataSourceResponse jsonDataSource = retrieveDataSourceByName(jsonDataSchema,
		// dataSourceName);
		// org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema jpaDataSchema =
		// dataSchemaRepo.find(Integer.valueOf(jsonDataSchema.getData().getId()));
		// org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema jpaQuerySchema =
		// querySchemaRepo.add(sampleData.createJPAQuerySchema(jpaDataSchema));
		// org.enquery.encryptedquery.querier.data.entity.json.QuerySchema jsonQuerySchema =
		// retrieveQuerySchemas(jsonDataSchema)
		// .getData()
		// .stream()
		// .filter(x -> x.getId().equals(jpaQuerySchema.getId().toString()))
		// .findFirst()
		// .get();
		//
		// jsonQuerySchema = retrieveQuerySchema(jsonQuerySchema.getSelfUri()).getData();

		// submit the Query and wait for its encryption
		// String schedulesUri = createQueryAndWaitForEncryption(
		// querySchema.getQueriesUri(), query)
		// .getData()
		// .getSchedulesUri();


		org.enquery.encryptedquery.querier.data.entity.json.Schedule sch = new org.enquery.encryptedquery.querier.data.entity.json.Schedule();
		// schedule it for 30 seconds from now, resolution is minute, so it will run the next minute
		sch.setStartTime(Date.from(Instant.now().plus(Duration.ofSeconds(30))));
		sch.setParameters(dataSourceParameters);
		Resource ds = new Resource();
		ds.setId(dataSoureId);
		sch.setDataSource(ds);
		sch.setType(null);
		sch.getDataSource().setSelfUri(null);
		sch.getDataSource().setType(null);

		ScheduleResponse schedule = createSchedule(schedulesUri, sch);
		return schedule;
	}

	protected DataSourceResponse retrieveDataSourceByName(DataSchemaResponse jsonDataSchema, String dataSourceName) throws InterruptedException {
		return retrieveDataSource(retrieveDataSources(jsonDataSchema)
				.getData()
				.stream()
				.filter(ds -> {
					return ds.getName().equals(dataSourceName);
				})
				.findFirst()
				.get()
				.getSelfUri());
	}

	protected DecryptionCollectionResponse retrieveDecryptions(String decryptionsUri) {
		assertNotNull(decryptionsUri);
		return invoke(decryptionsUri,
				200,
				DEFAULT_ACCEPT,
				itCamelContext.getEndpoint("mock:decryptions-result", MockEndpoint.class),
				"direct:retrieve-decryptions")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(DecryptionCollectionResponse.class);
	}

	protected DecryptionResponse createDecryption(String decryptionsUri) {
		Message message = invoke(decryptionsUri,
				201,
				null,
				itCamelContext.getEndpoint("mock:create-decryption-result", MockEndpoint.class),
				"direct:create-decryption", null)
						.getReceivedExchanges()
						.get(0)
						.getMessage();

		DecryptionResponse result = message.getBody(DecryptionResponse.class);

		String location = message.getHeader("Location", String.class);
		log.info("Location is: " + location);

		assertEquals(result.getData().getSelfUri(), location);
		return result;
	}

	protected DecryptionResponse retrieveDecryption(String decryptionUri) {
		assertNotNull(decryptionUri);
		return invoke(decryptionUri,
				200,
				DEFAULT_ACCEPT,
				itCamelContext.getEndpoint("mock:decryption-result", MockEndpoint.class),
				"direct:retrieve-decryption")
						.getReceivedExchanges()
						.get(0)
						.getMessage()
						.getBody(DecryptionResponse.class);
	}


	protected void validateClearTextResponse(Decryption retrieved,
			String selectorName,
			String selectorValue,
			String fieldName,
			String fieldValue) throws JAXBException, IOException {
		org.enquery.encryptedquery.querier.data.entity.jpa.Decryption d = decryptionRepo.find(Integer.valueOf(retrieved.getId()));

		ClearTextResponseTypeConverter converter = new ClearTextResponseTypeConverter();

		ClearTextResponse response = null;
		try (InputStream is = decryptionRepo.payloadInputStream(d)) {
			response = converter.unmarshal(is);
		}
		assertNotNull(response);
		assertNotNull(response.getSelector());
		assertTrue(response.getSelector().size() > 0);
		assertTrue(response.getSelector()
				.stream()
				.filter(s -> s.getSelectorName().equals(selectorName))
				.findFirst()
				.get()
				.getHits()
				.stream()
				.filter(h -> selectorValue.equals(h.getSelectorValue()))
				.anyMatch(hits -> hits.getHit()
						.stream()
						.anyMatch(record -> record.getField()
								.stream()
								.anyMatch(field -> field.getName().equals(fieldName) &&
										fieldValue.equals(field.getValue())))));
	}
}
