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
package org.enquery.encryptedquery.responder.data.service;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;


@Component(configurationPid = "encrypted.query.responder.data",
		service = BlobLocationRegistry.class)
public class BlobLocationRegistry extends RestServiceRegistry {

	@Activate
	void activate(Map<String, String> config) throws UnsupportedEncodingException, URISyntaxException, MalformedURLException {
		Validate.notNull(config);

		String rootUrl = config.get("blob.storage.root.url");
		if (rootUrl == null) {
			rootUrl = defaultDataDir();
		}

		setBaseUri(rootUrl);
	}

	private String defaultDataDir() throws MalformedURLException {
		File f = new File("data/blob-storage");
		f.mkdirs();
		return f.toURI().toURL().toString();
	}
}
