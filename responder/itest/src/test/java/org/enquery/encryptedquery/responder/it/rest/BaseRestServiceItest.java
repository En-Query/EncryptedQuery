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
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.core.osgi.OsgiClassResolver;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.lang3.ArrayUtils;
import org.enquery.encryptedquery.responder.it.AbstractResponderItest;
import org.enquery.encryptedquery.xml.schema.DataSchema;
import org.enquery.encryptedquery.xml.schema.DataSchemaResource;
import org.enquery.encryptedquery.xml.schema.DataSchemaResources;
import org.enquery.encryptedquery.xml.schema.DataSourceResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResources;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.ExecutionResources;
import org.enquery.encryptedquery.xml.schema.ObjectFactory;
import org.enquery.encryptedquery.xml.schema.ResultResource;
import org.enquery.encryptedquery.xml.schema.ResultResources;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BaseRestServiceItest extends AbstractResponderItest {

	protected static final String DEFAULT_ACCEPT = "application/vnd.encryptedquery.responder+xml; version=1";

	protected DefaultCamelContext itCamelContext;
	protected ProducerTemplate testProducer;

	// protected String dataSchemasUri;
	protected DataSchema bookCatalogDataSchema;
	protected DataSchema phoneRecordDataSchema;

	protected JAXBContext jaxbContext;
	private JaxbDataFormat dataFormat;

	@Inject
	protected BundleContext bundleContext;

	@Before
	@Override
	public void init() throws Exception {
		super.init();

		jaxbContext = JAXBContext.newInstance(
				ObjectFactory.class.getPackage().getName(),
				ObjectFactory.class.getClassLoader());

		dataFormat = new JaxbDataFormat(jaxbContext);
		dataFormat.setObjectFactory(true);

		dataFormat.setSchema(
				"classpath:/org/enquery/encryptedquery/xml/schema/data-schema-resources.xsd,"
						+ "classpath:/org/enquery/encryptedquery/xml/schema/data-source-resources.xsd,"
						+ "classpath:/org/enquery/encryptedquery/xml/schema/execution-resources.xsd,"
						+ "classpath:/org/enquery/encryptedquery/xml/schema/result-resources.xsd");

		startCamelProducer();
		setupRoutes();
	}

	@Override
	@ProbeBuilder
	public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
		super.probeConfiguration(probe);
		probe.setHeader(Constants.IMPORT_PACKAGE,
				"*,org.enquery.encryptedquery.xml.schema");
		return probe;
	}

	@Override
	// @Configuration
	public Option[] baseOptions() {
		MavenArtifactUrlReference camelOsgi = maven()
				.groupId("org.apache.camel")
				.artifactId("camel-core-osgi")
				.versionAsInProject();

		return ArrayUtils.addAll(super.baseOptions(), CoreOptions.options(mavenBundle(camelOsgi)));
	}

	protected void setupRoutes() throws Exception {
		itCamelContext.addRoutes(new RouteBuilder() {
			@Override
			public void configure() {

				from("direct:retrieve")
						.to("http4://localhost:8181?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 200"))
						.unmarshal(dataFormat)
						.end()
						.to("mock:retrieve-result");

				from("direct:create")
						.marshal(dataFormat)
						.log("${body}")
						.to("http4://localhost:8181?throwExceptionOnFailure=false")
						.log("${body}")
						.filter(simple("${header.CamelHttpResponseCode} == 201"))
						.unmarshal(dataFormat)
						.end()
						.to("mock:create-result");

				from("direct:create-raw")
						.log("${body}")
						.to("http4://localhost:8181?throwExceptionOnFailure=false")
						.log("${body}")
						.to("mock:create-result");
			}
		});
	}

	protected void startCamelProducer() throws Exception {
		itCamelContext = new DefaultCamelContext();
		itCamelContext.addComponent("http4", new HttpComponent());
		itCamelContext.setTracing(true);
		itCamelContext.setStreamCaching(true);
		itCamelContext.setName(this.getClass().getSimpleName());
		itCamelContext.setClassResolver(new OsgiClassResolver(itCamelContext, bundleContext));
		itCamelContext.start();
		testProducer = itCamelContext.createProducerTemplate();
		testProducer.start();
	}

	protected ExecutionResources retrieveExecutions(String executionsUri) {
		assertNotNull(executionsUri);
		return retrieve(executionsUri, 200, DEFAULT_ACCEPT)
				.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(ExecutionResources.class);
	}

	protected ExecutionResource retrieveExecution(String uri) {
		assertNotNull(uri);

		Map<String, Object> headers = new HashMap<>();
		headers.put(Exchange.HTTP_PATH, uri);
		headers.put("Accept", DEFAULT_ACCEPT);

		return testProducer.requestBodyAndHeaders("direct:retrieve", null, headers, ExecutionResource.class);
		// return retrieve(uri, 200, DEFAULT_ACCEPT)
		// .getReceivedExchanges()
		// .get(0)
		// .getMessage()
		// .getBody(ExecutionResource.class);
	}

	protected ResultResource retrieveResult(String uri) {
		assertNotNull(uri);
		return retrieve(uri, 200, DEFAULT_ACCEPT)
				.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(ResultResource.class);
	}

	protected ResultResources retrieveResults(String uri) {
		assertNotNull(uri);
		return retrieve(uri, 200, DEFAULT_ACCEPT)
				.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(ResultResources.class);
	}

	protected ExecutionResource createExecution(String uri, Object execution) {
		Message message = create(uri, 201, execution)
				.getReceivedExchanges()
				.get(0)
				.getMessage();

		ExecutionResource result = message.getBody(ExecutionResource.class);

		String location = message.getHeader("Location", String.class);
		log.info("Location is: " + location);

		assertEquals(result.getSelfUri(), location);
		return result;
	}

	protected DataSourceResources retrieveDataSources(String uri) throws InterruptedException {
		assertNotNull(uri);
		return retrieve(uri, 200, DEFAULT_ACCEPT)
				.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(DataSourceResources.class);
	}

	protected DataSourceResource retrieveDataSource(String uri) throws InterruptedException {
		assertNotNull(uri);
		return retrieve(uri, 200, DEFAULT_ACCEPT)
				.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(DataSourceResource.class);
	}

	protected DataSchemaResource retrieveDataSchemaByName(String name) throws InterruptedException {
		DataSchemaResources resources = retrieveDataSchemas("/responder/api/rest/dataschemas");
		return resources
				.getDataSchemaResource()
				.stream()
				.filter(ds -> ds.getDataSchema().getName().equals(name))
				.findFirst()
				.orElseGet(null);
	}

	protected DataSchemaResource retrieveDataSchema(String uri) {
		assertNotNull(uri);
		try {
			return retrieveDataSchema(uri, 200);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	protected DataSchemaResource retrieveDataSchema(String dataSchemaUri, int resultCode) throws InterruptedException {
		return retrieve(dataSchemaUri, resultCode, DEFAULT_ACCEPT)
				.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(DataSchemaResource.class);
	}

	protected DataSchemaResources retrieveDataSchemas(String uri) throws InterruptedException {
		return retrieveDataSchemas(uri, 200);
	}

	protected DataSchemaResources retrieveDataSchemas(String uri, int resultCode) throws InterruptedException {
		return retrieve(uri, resultCode, DEFAULT_ACCEPT)
				.getReceivedExchanges()
				.get(0)
				.getMessage()
				.getBody(DataSchemaResources.class);
	}

	private MockEndpoint retrieve(String remoteUri, int expectedResponseCode, String acceptValue) {
		return invoke(remoteUri,
				expectedResponseCode,
				acceptValue,
				itCamelContext.getEndpoint("mock:retrieve-result", MockEndpoint.class),
				"direct:retrieve",
				null);
	}

	private MockEndpoint create(String remoteUri, int expectedResponseCode, Object payload) {
		return invoke(remoteUri,
				expectedResponseCode,
				null,
				itCamelContext.getEndpoint("mock:create-result", MockEndpoint.class),
				"direct:create",
				payload);
	}

	protected MockEndpoint invoke(String remoteUri, int expectedResponseCode, String acceptValue, MockEndpoint mock, String localUri, Object payload) {
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

		testProducer.requestBodyAndHeaders(localUri, payload, headers);
		try {
			mock.assertIsSatisfied();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return mock;
	}

}
