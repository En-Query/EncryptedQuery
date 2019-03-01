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
package org.enquery.encryptedquery.responder.flink.kafka.runner;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpComponent;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.core.osgi.OsgiClassResolver;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.json.JSONStringConverter;
import org.enquery.encryptedquery.responder.data.entity.ExecutionStatus;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class JobStatusQuerier {

	private static final Logger log = LoggerFactory.getLogger(JobStatusQuerier.class);
	private ProducerTemplate producer;
	private DefaultCamelContext camelContext;

	/**
	 * @throws Exception
	 * 
	 */
	public JobStatusQuerier(BundleContext bundleContext, String historyServerUri) throws Exception {

		camelContext = new DefaultCamelContext();
		camelContext.addComponent("http4", new HttpComponent());
		camelContext.setTracing(true);
		camelContext.setStreamCaching(false);
		camelContext.setName(this.getClass().getSimpleName());
		camelContext.setClassResolver(new OsgiClassResolver(camelContext, bundleContext));

		Properties props = new Properties();
		props.setProperty("history.server.uri", historyServerUri);

		SimpleRegistry registry = new SimpleRegistry();
		registry.put("historyServerProps", props);
		camelContext.setRegistry(registry);

		PropertiesComponent propertiesComponent = new PropertiesComponent();
		propertiesComponent.setLocation("ref:historyServerProps");
		camelContext.addComponent("properties", propertiesComponent);
		camelContext.start();

		producer = camelContext.createProducerTemplate();
		producer.start();

		camelContext.addRoutes(new RouteBuilder() {
			@Override
			public void configure() {
				from("direct:retrieve")
						.to("http4://{{history.server.uri}}?throwExceptionOnFailure=false");
			}
		});
	}

	public void stop() throws Exception {
		if (camelContext != null) camelContext.stop();
	}

	public ExecutionStatus status(String jobId) {
		Validate.notNull(jobId);
		String uri = String.format("jobs/%s", jobId);

		Map<String, Object> headers = new HashMap<>();
		headers.put(Exchange.HTTP_PATH, uri);

		String json = producer.requestBodyAndHeaders("direct:retrieve", null, headers, String.class);
		log.info("History Server returned: {}", json);

		Map<String, Object> map = JSONStringConverter.toStringObjectMap(json);
		if (!jobId.equals(map.getOrDefault("jid", ""))) {
			return null;
		}

		String error = null;
		boolean canceled = false;
		Date endTime = null;
		String state = (String) map.get("state");

		if ("RUNNING".equals(state)) {
			return new ExecutionStatus(null, null, false);
		}

		if ("CANCELED".equals(state)) {
			canceled = true;
		} else if ("FAILED".equals(state)) {
			error = String.format("Flink job id '%s' failed.", jobId);
		}

		Object endTimeObj = map.get("end-time");
		if (endTimeObj != null || (endTimeObj instanceof Long)) {
			endTime = new Date((Long) endTimeObj);
		}

		return new ExecutionStatus(endTime, error, canceled);
	}
}
