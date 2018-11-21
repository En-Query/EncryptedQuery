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

import java.math.BigInteger;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsolidateResponse {

	private static final Logger logger = LoggerFactory.getLogger(ConsolidateResponse.class);

	/**
	 * This method consolidates the responses from the queue into a single response
	 * 
	 * @param queue
	 * @param query
	 * @return
	 */
	public static Response consolidateResponse(ConcurrentLinkedQueue<Response> queue, Query query) {

		Response response = new Response(query.getQueryInfo());
		TreeMap<Integer, BigInteger> columns = new TreeMap<>();

		Response nextResponse;
		int consolidateCounter = 0;
		logger.info("Consolidating Response");
		try {
			while ((nextResponse = queue.poll()) != null) {
				for (TreeMap<Integer, BigInteger> nextItem : nextResponse.getResponseElements()) {
					logger.debug("Consolidate {} response", consolidateCounter);

					for (Entry<Integer, BigInteger> entry : nextItem.entrySet()) {

						if (!columns.containsKey(entry.getKey())) {
							columns.put(entry.getKey(), BigInteger.valueOf(1));
						}
						BigInteger column = columns.get(entry.getKey());

						column = (column.multiply(entry.getValue())).mod(query.getNSquared());

						columns.put(entry.getKey(), column);

					}
				}
				consolidateCounter++;
			}
		} catch (Exception e) {
			throw new RuntimeException("Exception consolidating response.", e);
		}
		response.addResponseElements(columns);
		logger.info("Combined {} response files into one", consolidateCounter);

		return response;
	}

}
