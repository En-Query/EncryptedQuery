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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.data.service.RestServiceRegistry;
import org.enquery.encryptedquery.responder.data.service.ResultRepository;

public class CamelContextBeanLocator {

	public static RestServiceRegistry restServiceRegistry(Exchange exchange) {
		Validate.notNull(exchange);
		final CamelContext context = exchange.getContext();
		Validate.notNull(context);
		final RestServiceRegistry registry = context.getRegistry().findByType(RestServiceRegistry.class).iterator().next();
		Validate.notNull(registry);
		return registry;
	}

	public static DataSchemaService dataSchemaRepository(Exchange exchange) {
		Validate.notNull(exchange);
		final CamelContext context = exchange.getContext();
		Validate.notNull(context);
		final DataSchemaService result = context.getRegistry().findByType(DataSchemaService.class).iterator().next();
		Validate.notNull(result);
		return result;
	}

	public static DataSourceRegistry dataSourceRegistry(Exchange exchange) {
		Validate.notNull(exchange);
		final CamelContext context = exchange.getContext();
		Validate.notNull(context);
		final DataSourceRegistry result = context.getRegistry().findByType(DataSourceRegistry.class).iterator().next();
		Validate.notNull(result);
		return result;
	}

	public static ResultRepository resultRepository(Exchange exchange) {
		Validate.notNull(exchange);
		final CamelContext context = exchange.getContext();
		Validate.notNull(context);
		final ResultRepository result = context.getRegistry().findByType(ResultRepository.class).iterator().next();
		Validate.notNull(result);
		return result;
	}
}
