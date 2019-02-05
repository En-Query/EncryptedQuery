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
package org.enquery.encryptedquery.responder.data.transformation;

import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.RestServiceRegistry;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.enquery.encryptedquery.xml.schema.ExecutionResources;
import org.enquery.encryptedquery.xml.transformation.XMLFactories;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public class ExecutionTypeConverter {

	private static final Logger log = LoggerFactory.getLogger(ExecutionTypeConverter.class);

	@Converter
	public ExecutionResources toXMLExecutions(Collection<org.enquery.encryptedquery.responder.data.entity.Execution> jpaExecutions,
			Exchange exchange) {

		if (log.isDebugEnabled()) {
			log.debug("Converting {} to XML ExecutionResources.", jpaExecutions);
		}

		final RestServiceRegistry registry = CamelContextBeanLocator.restServiceRegistry(exchange);
		DataSourceRegistry dataSrcRegistry = CamelContextBeanLocator.dataSourceRegistry(exchange);

		ExecutionResources result = new ExecutionResources();
		result.getExecutionResource().addAll(
				jpaExecutions
						.stream()
						.map(ex -> {
							return makeResource(ex, registry, dataSrcRegistry);
						}).collect(Collectors.toList()));
		return result;
	}


	@Converter
	public static ExecutionResource toXMLExecution(org.enquery.encryptedquery.responder.data.entity.Execution jpaExecution,
			Exchange exchange) {

		if (log.isDebugEnabled()) {
			log.debug("Converting {} to XML ExecutionResource.", jpaExecution);
		}

		final RestServiceRegistry registry = CamelContextBeanLocator.restServiceRegistry(exchange);
		DataSourceRegistry dataSrcRegistry = CamelContextBeanLocator.dataSourceRegistry(exchange);

		return makeResource(jpaExecution, registry, dataSrcRegistry);
	}


	private static ExecutionResource makeResource(org.enquery.encryptedquery.responder.data.entity.Execution jpaExecution, final RestServiceRegistry registry, DataSourceRegistry dataSrcRegistry) {

		final org.enquery.encryptedquery.xml.schema.Execution xmlExecution =
				new org.enquery.encryptedquery.xml.schema.Execution();

		xmlExecution.setScheduledFor(XMLFactories.toXMLTime(jpaExecution.getScheduleTime()));
		xmlExecution.setSubmittedOn(XMLFactories.toXMLTime(jpaExecution.getReceivedTime()));
		xmlExecution.setStartedOn(XMLFactories.toXMLTime(jpaExecution.getStartTime()));
		xmlExecution.setCompletedOn(XMLFactories.toXMLTime(jpaExecution.getEndTime()));

		final ExecutionResource resource = new ExecutionResource();
		resource.setExecution(xmlExecution);
		resource.setId(jpaExecution.getId());

		final Integer dataSchemaId = jpaExecution.getDataSchema().getId();
		final DataSource dataSource = dataSrcRegistry.find(jpaExecution.getDataSourceName());
		Validate.notNull(dataSource);

		final Integer dataSourceId = dataSource.getId();

		resource.setSelfUri(registry.executionUri(dataSchemaId, dataSourceId, jpaExecution.getId()));
		resource.setResultsUri(registry.resultsUri(dataSchemaId, dataSourceId, jpaExecution.getId()));
		resource.setDataSourceUri(registry.dataSourceUri(dataSchemaId, dataSourceId));

		return resource;
	}

	@Converter
	public org.enquery.encryptedquery.responder.data.entity.Execution toJPAExecution(Execution ex) throws Exception {

		org.enquery.encryptedquery.responder.data.entity.Execution result = new org.enquery.encryptedquery.responder.data.entity.Execution();
		result.setReceivedTime(XMLFactories.toLocalTime(ex.getSubmittedOn()));
		result.setScheduleTime(XMLFactories.toLocalTime(ex.getScheduledFor()));
		result.setEndTime(XMLFactories.toLocalTime(ex.getCompletedOn()));
		result.setStartTime(XMLFactories.toLocalTime(ex.getStartedOn()));

		return result;
	}
}
