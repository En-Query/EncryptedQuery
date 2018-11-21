package org.enquery.encryptedquery.querier.data.transformation;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestServiceLocator {

	private static final Logger log = LoggerFactory.getLogger(RestServiceLocator.class);

	public static Resource find(Exchange exchange, String name) {
		Validate.notNull(exchange);
		Validate.notNull(name);
		final ResourceUriRegistry registry = resourceUriRegistry(exchange);
		Resource endpoint = registry.findByName(name);
		Validate.notNull(endpoint);
		return endpoint;
	}

	public static ResourceUriRegistry resourceUriRegistry(Exchange exchange) {
		Validate.notNull(exchange);
		final CamelContext context = exchange.getContext();
		Validate.notNull(context);
		ResourceUriRegistry registry = (ResourceUriRegistry) context.getRegistry().lookupByName("restRegistry");
		Validate.notNull(registry);
		log.info("Will use URI registry: " + registry);
		return registry;
	}
}
