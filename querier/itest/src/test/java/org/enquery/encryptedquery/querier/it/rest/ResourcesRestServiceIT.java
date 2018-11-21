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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpComponent;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.ResourceCollectionResponse;
import org.enquery.encryptedquery.querier.it.AbstractQuerierItest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ResourcesRestServiceIT extends AbstractQuerierItest {

	private DefaultCamelContext itCamelContext;
	private ProducerTemplate testProducer;

	private MockEndpoint listResultMock;

	@Before
	public void initService() throws Exception {
		truncateTables();
		startCamelProducer();
		setupRoutes();
		waitForHealthyStatus();
	}

	private void setupRoutes() throws Exception {
		final JacksonDataFormat listDataFormat = new JacksonDataFormat(ResourceCollectionResponse.class);

		itCamelContext.addRoutes(new RouteBuilder() {
			public void configure() {
				from("direct:list")
						.to("http4://localhost:8182/querier/api/rest/?throwExceptionOnFailure=false")
						.log("${body}")
						.unmarshal(listDataFormat)
						.to("mock:result");
			}
		});
		listResultMock = itCamelContext.getEndpoint("mock:result", MockEndpoint.class);
	}

	@Configuration
	public Option[] configuration() {
		return new Option[] {baseOptions()};
	}


	private void startCamelProducer() throws Exception {
		itCamelContext = new DefaultCamelContext();
		itCamelContext.addComponent("http4", new HttpComponent());
		itCamelContext.setTracing(true);
		itCamelContext.setStreamCaching(true);
		itCamelContext.setName(this.getClass().getSimpleName());
		itCamelContext.start();
		testProducer = itCamelContext.createProducerTemplate();
		testProducer.start();
	}

	@Test
	public void list() throws Exception {
		listResultMock.expectedMessageCount(1);
		listResultMock.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);
		testProducer.sendBody("direct:list", null);
		listResultMock.assertIsSatisfied();
		ResourceCollectionResponse resources = listResultMock.getReceivedExchanges().get(0).getMessage().getBody(ResourceCollectionResponse.class);

		Map<String, String> map = new HashMap<>();
		for (Resource ep : resources.getData()) {
			map.put(ep.getId(), ep.getSelfUri());
		}
		assertEquals("/querier/api/rest/dataschemas", map.get("dataschema"));
		// assertEquals("/querier/api/rest/datasources", map.get("datasource"));
		// assertEquals("/querier/api/rest/queryschemas", map.get("queryschema"));
		// assertEquals("/querier/api/rest/queries", map.get("query"));
	}
}
