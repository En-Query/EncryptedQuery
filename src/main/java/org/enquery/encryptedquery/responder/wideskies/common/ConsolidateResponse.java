/*
 * Copyright 2017 EnQuery.
 * This product includes software licensed to EnQuery under 
 * one or more license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file has been modified from its original source.
 */
package org.enquery.encryptedquery.responder.wideskies.common;

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
	 * @param queue
	 * @param query
	 * @return
	 */
	public static Response consolidateResponse(ConcurrentLinkedQueue<Response> queue, Query query) {

		Response response = new Response(query.getQueryInfo());
		TreeMap<Integer,BigInteger> columns = new TreeMap<>();

		Response nextResponse;
		int consolidateCounter = 0;
		logger.info("Consolidating Response");
		try {
			while ((nextResponse = queue.poll()) != null) {
				for (TreeMap<Integer, BigInteger> nextItem : nextResponse.getResponseElements()) {
					logger.debug("Consolidate {} response", consolidateCounter);

					for (Entry<Integer, BigInteger> entry : nextItem.entrySet()) {

						if (!columns.containsKey(entry.getKey()))
						{
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
			logger.error("Exception consolidating response {}", e.getMessage());
		}
		response.addResponseElements(columns);
		logger.info("Combined {} response files into one", consolidateCounter);

		return response;
	}

}
