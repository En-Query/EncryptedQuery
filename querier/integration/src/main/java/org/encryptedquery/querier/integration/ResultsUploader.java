package org.encryptedquery.querier.integration;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;

import javax.mail.internet.ContentType;

import org.apache.camel.Body;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.lang3.Validate;
import org.encryptedquery.querier.business.ResultUpdater;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = ResultsUploader.class)
public class ResultsUploader {

	/**
	 * 
	 */
	private static final int BUF_SIZE = 64 * 1024;

	private static Logger log = LoggerFactory.getLogger(ResultsUploader.class);

	@Reference
	private ExecutorService threadPool;
	@Reference
	private ResultUpdater updater;

	@Handler
	public void processUpload(@Body InputStream body,
			@Header("Content-Type") String contentType) throws Exception {

		log.info("Uploading results file. Content type: {}.", contentType);

		if (contentType == null) {
			updater.importFromResultExportXMLStream(body);
		} else {
			ContentType ct = new ContentType(contentType);
			if ("multipart/form-data".equals(ct.getBaseType())) {
				processMultipart(ct, body);
			} else {
				updater.importFromResultExportXMLStream(body);
			}
		}
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

		PipedOutputStream out = new PipedOutputStream();
		PipedInputStream in = new PipedInputStream(out);
		threadPool.submit(() -> {
			try {
				multipartStream.readBodyData(out);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		updater.importFromResultExportXMLStream(in);

		log.info("Finished saving file.");
	}
}
