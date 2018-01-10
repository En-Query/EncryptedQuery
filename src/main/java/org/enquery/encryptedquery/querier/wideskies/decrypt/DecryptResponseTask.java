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
package org.enquery.encryptedquery.querier.wideskies.decrypt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.schema.response.QueryResponseJSON;
import org.enquery.encryptedquery.utils.PIRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spark_project.guava.collect.Sets;

import com.google.common.collect.Sets.SetView;

/**
 * Runnable class for multithreaded PIR decryption
 * <p>
 * NOTE: rElements and selectorMaskMap are joint access objects, for now
 *
 */
class DecryptResponseTask<V> implements Callable<Map<String, List<QueryResponseJSON>>> {
	private static final Logger logger = LoggerFactory.getLogger(DecryptResponseTask.class);

	private final List<BigInteger> rElements;
	private final TreeMap<Integer, String> selectors;
	private final Map<String, BigInteger> selectorMaskMap;
	private final QueryInfo queryInfo;

	private final Map<Integer, String> embedSelectorMap;

	public DecryptResponseTask(List<BigInteger> rElementsInput, TreeMap<Integer, String> selectorsInput, Map<String, BigInteger> selectorMaskMapInput,
			QueryInfo queryInfoInput, Map<Integer, String> embedSelectorMapInput) {
		rElements = rElementsInput;
		selectors = selectorsInput;
		selectorMaskMap = selectorMaskMapInput;
		queryInfo = queryInfoInput;
		embedSelectorMap = embedSelectorMapInput;

		queryInfo.printQueryInfo();
	}

	private static byte[] bigIntegerToByteArray(final BigInteger bigInteger, int byteArraySize) {

		byte[] bytes = bigInteger.toByteArray();
		byte[] returnBytes = null;

		// bigInteger.toByteArray returns a two's compliment.  We need an unsigned value
		if (bytes[0] == 0) {
			returnBytes = Arrays.copyOfRange(bytes, 1, bytes.length);
		} else {
			returnBytes = bytes;
		}

		//If the Biginteger is zero, we still need to return a byte array of zeros!
		if (returnBytes.length < byteArraySize) {
        	returnBytes = new byte[byteArraySize];
            for (int i = 0 ; i < 2; i++) {
            	returnBytes[i] = 0x00;
            }
        }

		return returnBytes;

	}
	
	@Override
	public Map<String, List<QueryResponseJSON>> call() throws PIRException {
		// Pull the necessary parameters
		int dataPartitionBitSize = queryInfo.getDataPartitionBitSize();
		int numPartitionsPerDataElement = queryInfo.getNumPartitionsPerDataElement();

		QuerySchema qSchema = QuerySchemaRegistry.get(queryInfo.getQueryType());
		String selectorName = qSchema.getSelectorName();

		HashMap<String, Integer> falseHits = new HashMap<String, Integer>();
		HashMap<String, Integer> goodHits = new HashMap<String, Integer>();
		
		// Result is a map of (selector -> List of hits).
		Map<String, List<QueryResponseJSON>> resultMap = new HashMap<>(selectors.size());
		for (String selector : selectors.values()) {
			resultMap.put(selector, new ArrayList<QueryResponseJSON>());
		}

		// Pull the hits for each selector
		int maxHitsPerSelector = rElements.size() / numPartitionsPerDataElement; // Max number of
																					// data hits in
																					// the response
																					// elements for
																					// a given
																					// selector
		logger.debug("numResults = " + rElements.size() + " numPartitionsPerDataElement = " + numPartitionsPerDataElement + " maxHits = " + maxHitsPerSelector);

		for (int hits = 0; hits < maxHitsPerSelector; hits++) {
			int selectorIndex = selectors.firstKey();

			while (selectorIndex <= selectors.lastKey()) {
				String selector = selectors.get(selectorIndex);
				logger.debug("selector = " + selector);

				List<BigInteger> partitions = new ArrayList<>();
				List<BigInteger> parts = new ArrayList<>();
				
				boolean zeroElement = true;
				for (int partNum = 0; partNum < numPartitionsPerDataElement; partNum++) {
					BigInteger part = (rElements.get(hits * numPartitionsPerDataElement + partNum)).and(selectorMaskMap.get(selector)); // pull
																																		// off
																																		// the
																																		// correct
																																		// bits

					logger.debug("rElements.get(" + (hits * numPartitionsPerDataElement + partNum) + ") = "
							+ rElements.get(hits * numPartitionsPerDataElement + partNum).toString(2) + " bitLength = "
							+ rElements.get(hits * numPartitionsPerDataElement + partNum).bitLength() + " val = "
							+ rElements.get(hits * numPartitionsPerDataElement + partNum));
					logger.debug("colNum = " + (hits * numPartitionsPerDataElement + partNum) + " partNum = " + partNum + " part = " + part);

					part = part.shiftRight(selectorIndex * dataPartitionBitSize);
					partitions.add(part);

					logger.debug("partNum = " + partNum + " part = " + part.intValue());

					zeroElement = zeroElement && part.equals(BigInteger.ZERO);
				}

			    int bytesPerPartition = 1;
			    if (( dataPartitionBitSize % 8 ) == 0 ) {
			    	bytesPerPartition = dataPartitionBitSize / 8 ;
			    }
			    else {
			    	logger.error("dataPartitionBitSize must be a multiple of 8 !! {}", dataPartitionBitSize);
			    }
				logger.debug("bytesPerPartition {} \"partitions.size() {}", bytesPerPartition, partitions.size());

				if (bytesPerPartition > 1 ) {
				int index = 1;
			    for (BigInteger bi : partitions) {
			    	logger.debug("Part {} BigInt {} / Byte {}", index, bi.toString(), bi.toString(16) );
			    	index++;
                    byte[] partitionBytes = bigIntegerToByteArray(bi, bytesPerPartition);
                    for (byte b : partitionBytes) {
                    	parts.add(BigInteger.valueOf((long) b & 0xFF));
                    	logger.debug("Added part {}", BigInteger.valueOf((long) b & 0xFF).toString(16));
                    }
			    }
				} else {
                   parts = partitions;					
				}
				
				logger.debug("parts.size() = " + parts.size());
				
				int counter = 0;
				for (BigInteger bi : parts) {
					logger.debug("part {} value {}", counter, bi.toString(16));
					counter++;
				}
				
			    if (!zeroElement) {
					// Convert biHit to the appropriate QueryResponseJSON object, based on the
					// queryType
					QueryResponseJSON qrJOSN = QueryUtils.extractQueryResponseJSON(queryInfo, qSchema, parts);
					qrJOSN.setMapping(selectorName, selector);
					logger.debug("selector = " + selector + " qrJOSN = " + qrJOSN.getJSONString());

					// Add the hit for this selector - if we are using embedded selectors, check to
					// make sure
					// that the hit's embedded selector in the qrJOSN and the once in the
					// embedSelectorMap match
					boolean addHit = true;
					if (queryInfo.getEmbedSelector()) {
						if (!(embedSelectorMap.get(selectorIndex)).equals(qrJOSN.getValue(QueryResponseJSON.SELECTOR))) {
							addHit = false;
							logger.debug("qrJOSN embedded selector = " + qrJOSN.getValue(QueryResponseJSON.SELECTOR) + " != original embedded selector = "
									+ embedSelectorMap.get(selectorIndex));
							if (falseHits.containsKey(selector)) {
								falseHits.put(selector, falseHits.get(selector).intValue() + 1);
							} else {
								falseHits.put(selector, 1);
							}
						}
					}
					if (addHit) {
						List<QueryResponseJSON> selectorHitList = resultMap.get(selector);
						selectorHitList.add(qrJOSN);
						resultMap.put(selector, selectorHitList);

						// Add the selector into the wlJSONHit
						qrJOSN.setMapping(QueryResponseJSON.SELECTOR, selector);
						if (goodHits.containsKey(selector)) {
							goodHits.put(selector, goodHits.get(selector).intValue() + 1);
						} else {
							goodHits.put(selector, 1);
						}
					}
				}

				++selectorIndex;
			}
		}
		
		Set<String> selectors = Sets.union(falseHits.keySet(), goodHits.keySet());
        for (String key : selectors) {
	   		logger.info("There were " + goodHits.get(key) + " Hits and " + falseHits.get(key) + " false hit(s) for selector " + key);
	    }

		return resultMap;
	}
}
