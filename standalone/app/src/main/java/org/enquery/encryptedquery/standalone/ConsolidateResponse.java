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

import java.security.PublicKey;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsolidateResponse {

	private static final Logger logger = LoggerFactory.getLogger(ConsolidateResponse.class);

	private final CryptoScheme crypto;

	/**
	 * 
	 */
	public ConsolidateResponse(CryptoScheme crypto) {
		Validate.notNull(crypto);
		this.crypto = crypto;
	}

	/**
	 * This method consolidates the responses from the queue into a single response
	 * 
	 * @param queue
	 * @param query
	 * @return
	 */
	public Response consolidateResponse(ConcurrentLinkedQueue<Response> queue, QueryInfo queryInfo) {
		Validate.notNull(queue);
		Validate.notNull(queryInfo);

		final Response result = new Response(queryInfo);
		final Map<Integer, CipherText> columns = new TreeMap<>();
		final PublicKey publicKey = queryInfo.getPublicKey();

		Response nextResponse;
		int count = 0;
		logger.info("Consolidating Response");
		try {
			while ((nextResponse = queue.poll()) != null) {
				collect(columns, publicKey, nextResponse);
				count++;
			}
		} catch (Exception e) {
			throw new RuntimeException("Exception consolidating response.", e);
		}
		result.addResponseElements(columns);
		logger.info("Combined {} response files into one", count);
		return result;
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
