package org.encryptedquery.querier.integration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import javax.mail.internet.ContentType;
import javax.xml.bind.JAXBException;

import org.apache.camel.Body;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.lang3.Validate;
import org.encryptedquery.querier.business.DataSourceImporter;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = DataSourceUploader.class)
public class DataSourceUploader {

	/**
	 * 
	 */
	private static final int BUF_SIZE = 64 * 1024;

	private static Logger log = LoggerFactory.getLogger(DataSourceUploader.class);

	@Reference
	private DataSourceImporter importer;
	@Reference
	private ExecutorService threadPool;

	@Handler
	public void processUpload(@Body InputStream body,
			@Header("Content-Type") String contentType) throws Exception {

		log.info("Uploading data sources file. Content type: {}.", contentType);

		if (contentType == null) {
			processXml(body);
		} else {
			ContentType ct = new ContentType(contentType);
			if ("multipart/form-data".equals(ct.getBaseType())) {
				processMultipart(ct, body);
			} else {
				processXml(body);
			}
		}
	}

	/**
	 * @param body
	 * @throws JAXBException
	 */
	private void processXml(InputStream body) throws JAXBException {
		importer.importDataSources(body);
	}

	/**
	 * @param contentType
	 * @param body
	 * @throws Exception
	 */
	private void processMultipart(ContentType contentType, InputStream body) throws Exception {
		Validate.isTrue("multipart/form-data".equals(contentType.getBaseType()), "Invalid Content Type: %s", contentType.getBaseType());
		String delimiter = contentType.getParameter("boundary");
		log.info("Delimiter: {}", delimiter);

		MultipartStream multipartStream = new MultipartStream(body, delimiter.getBytes(), BUF_SIZE, null);

		boolean nextPart = multipartStream.skipPreamble();
		Validate.isTrue(nextPart, "No parts received.");

		while (nextPart) {
			String header = multipartStream.readHeaders();
			log.info("Headers: {}", header);
			saveFile(multipartStream);
			nextPart = multipartStream.readBoundary();
		}
	}

	private void saveFile(MultipartStream multipartStream) throws Exception {
		log.info("Start saving file.");

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		multipartStream.readBodyData(out);

		InputStream in = new ByteArrayInputStream(out.toByteArray());
		importer.importDataSources(in);

		log.info("Finished saving file.");
	}
}
