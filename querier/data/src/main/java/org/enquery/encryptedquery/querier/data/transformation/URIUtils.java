package org.enquery.encryptedquery.querier.data.transformation;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import org.apache.commons.lang3.Validate;

public class URIUtils {

	public static URI concat(String base, Integer id) {
		return concat(base, Integer.toString(id));
	}

	public static URI concat(String base, String rel) {
		Validate.notNull(base);
		Validate.notNull(rel);
		if (!base.endsWith("/")) {
			base = base + "/";
		}
		try {
			URI url = new URI(base);
			return url.resolve(URLEncoder.encode(rel, "UTF-8"));
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new RuntimeException("Error building URI.", e);
		}
	}
}
