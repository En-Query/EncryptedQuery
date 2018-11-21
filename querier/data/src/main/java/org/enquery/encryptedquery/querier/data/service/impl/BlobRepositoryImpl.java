package org.enquery.encryptedquery.querier.data.service.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.enquery.encryptedquery.querier.data.service.BlobRepository;
import org.osgi.service.component.annotations.Component;

@Component
public class BlobRepositoryImpl implements BlobRepository {

	@Override
	public void save(byte[] data, URL url) {
		save(new ByteArrayInputStream(data), url);
	}

	@Override
	public byte[] load(URL url) {
		try (InputStream in = url.openStream()) {
			return IOUtils.toByteArray(in);
		} catch (IOException e) {
			throw new RuntimeException("Error loading data from URL: " + url, e);
		}
	}

	@Override
	public void save(InputStream data, URL url) {
		try {
			switch (url.toURI().getScheme()) {
				case "file": {
					File file = Paths.get(url.toURI()).toFile();
					file.getParentFile().mkdirs();
					try (OutputStream os = new FileOutputStream(file)) {
						IOUtils.copy(data, os);
					}
					break;
				}
				default: {
					URLConnection c = url.openConnection();
					c.setDoOutput(true);
					try (OutputStream os = c.getOutputStream()) {
						IOUtils.copy(data, os);
					}
				}
			}
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException("Error saving data to URL: " + url, e);
		}
	}

	@Override
	public InputStream inputStream(URL url) {
		try {
			return url.openStream();
		} catch (IOException e) {
			throw new RuntimeException("Error reading data from URL: " + url, e);
		}
	}

	@Override
	public void delete(URL url) {
		try {
			switch (url.toURI().getScheme()) {
				case "file": {
					File file = Paths.get(url.toURI()).toFile();
					FileUtils.deleteQuietly(file);
					break;
				}
				default: {
					throw new RuntimeException("Don't know how to delete resource: " + url);
				}
			}
		} catch (URISyntaxException e) {
			throw new RuntimeException("Error deleting resource URL: " + url, e);
		}
	}

}
