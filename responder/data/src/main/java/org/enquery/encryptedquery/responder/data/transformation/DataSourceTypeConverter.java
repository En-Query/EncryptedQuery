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
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.service.RestServiceRegistry;
import org.enquery.encryptedquery.xml.schema.DataSourceResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public class DataSourceTypeConverter {

	private static final Logger log = LoggerFactory.getLogger(DataSourceTypeConverter.class);

	@Converter
	public static DataSourceResources toXMLDataSources(Collection<DataSource> javaDataSources,
			Exchange exchange) {

		if (log.isDebugEnabled()) {
			log.debug("Converting {} to XML DataSourceResources.", javaDataSources);
		}

		final RestServiceRegistry registry = CamelContextBeanLocator.restServiceRegistry(exchange);

		DataSourceResources result = new DataSourceResources();
		result.getDataSourceResource().addAll(
				javaDataSources
						.stream()
						.map(javaDataSource -> {
							org.enquery.encryptedquery.xml.schema.DataSource xmlDataSource =
									new org.enquery.encryptedquery.xml.schema.DataSource();

							xmlDataSource.setName(javaDataSource.getName());
							xmlDataSource.setDescription(javaDataSource.getDescription());
							xmlDataSource.setDataSchemaName(javaDataSource.getDataSchemaName());
							xmlDataSource.setType(javaDataSource.getType().toString());

							DataSourceResource resource = new DataSourceResource();
							resource.setDataSource(xmlDataSource);
							resource.setId(javaDataSource.getId());

							final Integer dataSchemaId = javaDataSource.getDataSchema().getId();

							resource.setSelfUri(registry.dataSourceUri(dataSchemaId, javaDataSource.getId()));
							resource.setExecutionsUri(registry.executionsUri(dataSchemaId, javaDataSource.getId()));
							resource.setDataSchemaUri(registry.dataSchemaUri(dataSchemaId));

							return resource;
						})
						.collect(Collectors.toList()));
		return result;
	}

	@Converter
	public static DataSourceResource toXMLDataSources(DataSource javaDataSource, Exchange exchange) {

		if (log.isDebugEnabled()) {
			log.debug("Converting {} to XML DataSourceResource.", javaDataSource);
		}

		final RestServiceRegistry registry = CamelContextBeanLocator.restServiceRegistry(exchange);

		org.enquery.encryptedquery.xml.schema.DataSource xmlDataSource =
				new org.enquery.encryptedquery.xml.schema.DataSource();

		xmlDataSource.setName(javaDataSource.getName());
		xmlDataSource.setDescription(javaDataSource.getDescription());
		xmlDataSource.setDataSchemaName(javaDataSource.getDataSchemaName());
		xmlDataSource.setType(javaDataSource.getType().toString());

		DataSourceResource resource = new DataSourceResource();
		resource.setDataSource(xmlDataSource);
		resource.setId(javaDataSource.getId());

		final Integer dataSchemaId = javaDataSource.getDataSchema().getId();

		resource.setSelfUri(registry.dataSourceUri(dataSchemaId, javaDataSource.getId()));
		resource.setExecutionsUri(registry.executionsUri(dataSchemaId, javaDataSource.getId()));
		resource.setDataSchemaUri(registry.dataSchemaUri(dataSchemaId));

		return resource;
	}
}
