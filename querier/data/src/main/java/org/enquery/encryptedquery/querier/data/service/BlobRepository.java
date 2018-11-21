package org.enquery.encryptedquery.querier.data.service;

import java.io.InputStream;
import java.net.URL;

/**
 * Encapsulates external blob storage IO operations in a agnostic way. For now we only support FILE
 * URLs, but we could add support for FTP or others
 *
 */
public interface BlobRepository {

	void save(InputStream data, URL url);

	void save(byte[] data, URL url);

	byte[] load(URL url);

	InputStream inputStream(URL url);

	void delete(URL url);

}
