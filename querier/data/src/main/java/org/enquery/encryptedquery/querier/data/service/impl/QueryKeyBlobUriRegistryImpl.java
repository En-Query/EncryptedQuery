package org.enquery.encryptedquery.querier.data.service.impl;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistryImpl;
import org.enquery.encryptedquery.querier.data.service.ResourceUriRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;


/**
 * Makes various URLs to blob data stored externally
 *
 */
@Component(configurationPid = "encrypted.query.querier.data", property = "type=query-key")
public class QueryKeyBlobUriRegistryImpl extends ResourceUriRegistryImpl implements ResourceUriRegistry {

	private String rootUrl;

	@Activate
	void activate(Map<String, String> config) throws UnsupportedEncodingException, URISyntaxException, MalformedURLException {
		Validate.notNull(config);

		rootUrl = config.get("query.key.storage.root.url");
		if (rootUrl == null) {
			rootUrl = defaultDataDir();
		}

		setBaseUri(rootUrl);
	}

	private String defaultDataDir() throws MalformedURLException {
		File f = new File("data/query-key-storage");
		f.mkdirs();
		return f.toURI().toURL().toString();
	}
}

