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
package org.enquery.encryptedquery.querier.encrypt;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.QueryKey;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to create encrypted query
 */
@Component(service = EncryptQuery.class)
public class EncryptQuery {
	private static final Logger logger = LoggerFactory.getLogger(EncryptQuery.class);

	@Reference
	private CryptoScheme crypto;
	@Reference
	private RandomProvider randomProvider;

	public CryptoScheme getCrypto() {
		return crypto;
	}

	public void setCrypto(CryptoScheme crypto) {
		this.crypto = crypto;
	}

	public RandomProvider getRandomProvider() {
		return randomProvider;
	}

	public void setRandomProvider(RandomProvider randomProvider) {
		this.randomProvider = randomProvider;
	}

	public Querier encrypt(QuerySchema querySchema, List<String> selectors,
			boolean embedSelector,
			int dataChunkSize,
			int hashBitSize) throws InterruptedException, PIRException {

		Validate.notNull(querySchema);
		Validate.notNull(selectors);
		Validate.isTrue(selectors.size() > 0);
		Validate.noNullElements(selectors);
		// Validate.notNull(config);
		querySchema.validate();
		// validateQuerierEncryptionProperties(config);
		Validate.notNull(crypto);

		int numSelectors = selectors.size();
		// int hashBitSize = Integer.parseInt(config.getOrDefault(HASH_BIT_SIZE, "12"));
		// int bitSet = Integer.parseInt(properties.getProperty(BIT_SET, "32"));
		// int dataChunkSize = Integer.parseInt(config.getOrDefault(DATA_CHUNK_SIZE, "8"));
		// int modulusBitSize = Integer.parseInt(properties.getProperty(PAILLIER_BIT_SIZE, "3072"));
		// int certainty = Integer.parseInt(properties.getProperty(CERTAINTY, "128"));
		// boolean embedSelector = Boolean.valueOf(config.getOrDefault(EMBEDSELECTOR, "true"));

		KeyPair keyPair = crypto.generateKeyPair();

		QueryInfo queryInfo = new QueryInfo();
		queryInfo.setIdentifier(UUID.randomUUID().toString());
		queryInfo.setCryptoSchemeId(crypto.name());
		queryInfo.setPublicKey(keyPair.getPublic());
		queryInfo.setNumSelectors(numSelectors);
		queryInfo.setHashBitSize(hashBitSize);
		queryInfo.setDataChunkSize(dataChunkSize);
		queryInfo.setQueryName(querySchema.getName());
		queryInfo.setEmbedSelector(embedSelector);
		queryInfo.setQuerySchema(querySchema);


		// Determine the query vector mappings for the selectors; vecPosition -> selectorNum
		Map<Integer, Integer> selectorQueryVecMapping = computeSelectorQueryVecMap(queryInfo, selectors);

		// Form the embedSelectorMap
		// Map to check the embedded selectors in the results for false positives;
		// if the selector is a fixed size < 32 bits, it is included as is
		// if the selector is of variable lengths
		Map<Integer, String> embedSelectorMap = computeEmbeddedSelectorMap(selectors);

		final Map<Integer, CipherText> queryElements = crypto.generateQueryVector(keyPair,
				queryInfo,
				selectorQueryVecMapping);

		logger.info("Completed creation of encrypted query vectors");

		Query query = new Query(queryInfo, queryElements);

		QueryKey queryKey = new QueryKey(selectors,
				keyPair,
				embedSelectorMap,
				query.getQueryInfo().getIdentifier(),
				crypto.name());

		return new Querier(query, queryKey);
	}

	/**
	 * Use this method to get a securely generated, random string of 2*numBytes length
	 *
	 * @param numBytes How many bytes of random data to return.
	 * @return Random hex string of 2*numBytes length
	 */
	private String getRandByteString(int numBytes) {
		byte[] randomData = new byte[numBytes];
		randomProvider.getSecureRandom().nextBytes(randomData);
		return Hex.encodeHexString(randomData);
	}

	/**
	 * Helper class to contain both a newly generated hash key and the selector hash to index
	 * mapping
	 */
	public static class KeyAndSelectorMapping {
		private final String hashKey;
		private final Map<Integer, Integer> selectorQueryVecMapping;

		public KeyAndSelectorMapping(String hashKey, Map<Integer, Integer> selectorQueryVecMapping) {
			this.hashKey = hashKey;
			this.selectorQueryVecMapping = selectorQueryVecMapping;
		}

		public String getHashKey() {
			return this.hashKey;
		}

		public Map<Integer, Integer> getSelectorQueryVecMapping() {
			return this.selectorQueryVecMapping;
		}
	}

	private KeyAndSelectorMapping computeSelectorQueryVecMap(int hashBitSize, List<String> selectors) {
		String hashKey = getRandByteString(10);
		int numSelectors = selectors.size();
		Map<Integer, Integer> selectorQueryVecMapping = new HashMap<>(numSelectors);

		int attempts = 0;
		int maxAttempts = 3;
		for (int index = 0; index < numSelectors; index++) {
			String selector = selectors.get(index);
			int hash = KeyedHash.hash(hashKey, hashBitSize, selector);

			// All keyed hashes of the selectors must be unique
			if (selectorQueryVecMapping.put(hash, index) == null) {
				// The hash is unique
				logger.debug("index = " + index + "selector = " + selector + " hash = " + hash);
			} else {
				// Hash collision. Each selectors hash needs to be unique. If not then try a new key
				// to calculate the hash with
				// Try this 3 times then start logging and skipping selectors that are causing a
				// collision
				if (attempts < maxAttempts) {
					selectorQueryVecMapping.clear();
					hashKey = getRandByteString(10);
					logger.info("Attempt " + attempts + " resulted in a collision for index = " + index + "selector = " + selector + " hash collision = " + hash + " new key = " + hashKey);
					index = -1;
					attempts++;
				} else {
					logger.info("Max Attempts reached ( " + attempts + " ) skipping selector = " + selector + " hash collision = " + hash + " new key = " + hashKey + " Index = " + index);
				}
			}
		}

		// return hashKey and mapping
		return new KeyAndSelectorMapping(hashKey, selectorQueryVecMapping);
	}

	private Map<Integer, Integer> computeSelectorQueryVecMap(QueryInfo queryInfo, List<String> selectors) {
		KeyAndSelectorMapping km = computeSelectorQueryVecMap(queryInfo.getHashBitSize(), selectors);
		String hashKey = km.getHashKey();
		Map<Integer, Integer> selectorQueryVecMapping = km.getSelectorQueryVecMapping();
		queryInfo.setHashKey(hashKey);
		return selectorQueryVecMapping;
	}

	private Map<Integer, String> computeEmbeddedSelectorMap(List<String> selectors) throws PIRException {
		// Can create partitioner with 0 size for string bits as that is not used in this case.
		// Partitioner partitioner = new Partitioner(0);
		Map<Integer, String> embedSelectorMap = new HashMap<>(selectors.size());
		int sNum = 0;
		for (String selector : selectors) {
			// Load the Hash of the Selector into the map.
			// String embeddedSelector = QueryUtils.getEmbeddedSelector(selector,
			// qSchema.getElement(selectorName));
			String embeddedSelector = String.valueOf(KeyedHash.hash("aux", 32, selector));
			embedSelectorMap.put(sNum, embeddedSelector);
			sNum += 1;
		}
		return embedSelectorMap;
	}
}
