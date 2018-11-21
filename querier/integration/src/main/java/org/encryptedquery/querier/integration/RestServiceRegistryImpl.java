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
package org.encryptedquery.querier.integration;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistryImpl;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * Registry of the main rest services URIs need to be maintained in sync with Camel rest services
 */
@Component(configurationPid = "encrypted.query.querier.rest", property = "type=rest-service")
public class RestServiceRegistryImpl extends ResourceUriRegistryImpl implements ResourceUriRegistry {

	@Activate
	void activate(Map<String, String> config) throws UnsupportedEncodingException, URISyntaxException, MalformedURLException {
		Validate.notNull(config);

		String contextPath = config.getOrDefault("context.path", "/querier");
		String apiRootPath = config.getOrDefault("api.root.path", "/api/rest");

		setBaseUri(contextPath + apiRootPath);
	}

}
