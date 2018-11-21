package org.enquery.encryptedquery.querier.data.transformation;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import org.apache.camel.Exchange;
import org.apache.commons.lang3.Validate;

public class ResourceUriBuilder {

	public static String uri(Exchange exchange, String name, Integer id) throws UnsupportedEncodingException, URISyntaxException {
		Validate.notNull(exchange);
		Validate.notNull(name);
		return URIUtils.concat(
				RestServiceLocator.find(exchange, name).getSelfUri(),
				id).toString();
	}
}
