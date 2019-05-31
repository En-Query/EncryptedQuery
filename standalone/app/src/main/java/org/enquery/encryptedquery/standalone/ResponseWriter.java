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
package org.enquery.encryptedquery.standalone;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseWriter implements Callable<Integer> {

	private static final Logger log = LoggerFactory.getLogger(ResponseWriter.class);

	private final CryptoScheme crypto;
	private final QueryInfo queryInfo;
	private final BlockingQueue<Response> queue;
	private final Path outputFileName;
	private final QueryTypeConverter queryConverter;

	/**
	 * 
	 */
	public ResponseWriter(CryptoScheme crypto,
			BlockingQueue<Response> queue,
			QueryInfo queryInfo,
			Path outputFileName,
			QueryTypeConverter queryConverter) {

		Validate.notNull(crypto);
		Validate.notNull(queue);
		Validate.notNull(queryInfo);
		Validate.notNull(outputFileName);
		Validate.notNull(queryConverter);

		this.crypto = crypto;
		this.queue = queue;
		this.queryInfo = queryInfo;
		this.outputFileName = outputFileName;
		this.queryConverter = queryConverter;
	}

	/**
	 * This method consolidates the responses from the queue into a single response
	 * 
	 * @param queue
	 * @param query
	 * @return
	 */
	@Override
	public Integer call() {

		final Map<Integer, CipherText> columns = new TreeMap<>();
		final PublicKey publicKey = queryInfo.getPublicKey();

		int count = 0;
		log.info("Aggregating responses from column processors");
		try {
			Response r = queue.take();
			while (!endOfQueue(r)) {
				collect(columns, publicKey, r);
				count++;
				r = queue.take();
			}
		} catch (Exception e) {
			throw new RuntimeException("Exception consolidating response.", e);
		}

		final Response result = new Response(queryInfo);
		result.addResponseElements(columns);
		log.info("Combined {} responses into one.", count);

		try {
			outputResponse(result);
		} catch (IOException | JAXBException e) {
			throw new RuntimeException("Error saving response file.", e);
		}

		return count;
	}


	// Compile the results from all the threads into one response file.
	private void outputResponse(Response outputResponse) throws FileNotFoundException, IOException, JAXBException {
		log.info("Writing response to file: '{}'", outputFileName);

		final CryptoSchemeRegistry registry = new CryptoSchemeRegistry() {
			@Override
			public CryptoScheme cryptoSchemeByName(String schemeId) {
				if (schemeId == null) return null;
				if (schemeId.equals(crypto.name())) return crypto;
				return null;
			}
		};

		ResponseTypeConverter converter = new ResponseTypeConverter();
		converter.setQueryConverter(queryConverter);
		converter.setSchemeRegistry(registry);
		converter.initialize();

		try (OutputStream output = new FileOutputStream(outputFileName.toFile())) {
			org.enquery.encryptedquery.xml.schema.Response xml = converter.toXML(outputResponse);
			converter.marshal(xml, output);
		}
	}

	/**
	 * @param r
	 * @return
	 */
	private boolean endOfQueue(Response r) {
		return r instanceof EofReponse;
	}

	private void collect(final Map<Integer, CipherText> columns, final PublicKey publicKey, Response response) {
		for (Map<Integer, CipherText> nextItem : response.getResponseElements()) {
			nextItem.forEach((k, v) -> {
				CipherText column = columns.get(k);
				if (column != null) {
					v = crypto.computeCipherAdd(publicKey, column, v);
				}
				columns.put(k, v);
			});
		}
	}

}
