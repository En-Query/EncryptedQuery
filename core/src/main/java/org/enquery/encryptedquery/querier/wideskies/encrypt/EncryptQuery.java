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
package org.enquery.encryptedquery.querier.wideskies.encrypt;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.encryption.Paillier;
import org.enquery.encryptedquery.encryption.PaillierEncryption;
import org.enquery.encryptedquery.querier.QuerierProperties;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.utils.KeyedHash;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.osgi.service.component.annotations.Activate;
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

	// Available methods for query generation.
	public static final String DEFAULT = "default";
	public static final String FAST = "fast";
	public static final String FASTWITHJNI = "fastwithjni";
	public static final List<String> METHODS = Arrays.asList(new String[] {DEFAULT, FAST, FASTWITHJNI});

	// Method to use for query generation.
	private String method = DEFAULT;

	@Reference
	private ModPowAbstraction modPowAbstraction;
	@Reference
	private PaillierEncryption paillierEncryption;
	@Reference
	private RandomProvider randomProvider;

	@Reference
	private ExecutorService threadPool;

	private int numThreads = 1;
	private Map<String, String> config;

	public EncryptQuery() {}

	public EncryptQuery(ModPowAbstraction modPowAbstraction, PaillierEncryption paillierEncryption,
			RandomProvider randomProvider, ExecutorService threadPool) {
		Validate.notNull(modPowAbstraction);
		Validate.notNull(paillierEncryption);
		Validate.notNull(randomProvider);
		Validate.notNull(threadPool);

		this.modPowAbstraction = modPowAbstraction;
		this.paillierEncryption = paillierEncryption;
		this.randomProvider = randomProvider;
		if (this.threadPool == null) {
			this.threadPool = threadPool;
		}

	}


	@Activate
	void activate(Map<String, String> config) {
		this.config = config;
		numThreads = Integer.parseInt(config.getOrDefault(QuerierProperties.NUMTHREADS, String.valueOf(Runtime.getRuntime().availableProcessors())));
		method = config.getOrDefault(QuerierProperties.METHOD, DEFAULT);
		Validate.isTrue(METHODS.contains(method), "Invalid method " + method + ". Allowed methods are " + METHODS);
	}

	/**
	 * Encrypts the query described by the query information using Paillier encryption using the
	 * given number of threads.
	 * <p>
	 * The encryption builds a <code>Querier</code> object, calculating and setting the query
	 * vectors.
	 * <p>
	 * If we have hash collisions over our selector set, we will append integers to the key starting
	 * with 0 until we no longer have collisions.
	 * <p>
	 * For encrypted query vector E = E_0, ..., E_{(2^hashBitSize)-1}:
	 * <p>
	 * E_i = 2^{j*dataPartitionBitSize} if i = H_k(selector_j) 0 otherwise
	 *
	 * @param numThreads the number of threads to use when performing the encryption.
	 * @return The querier containing the query, and all information required to perform decryption.
	 * @throws InterruptedException If the task was interrupted during encryption.
	 * @throws PIRException If a problem occurs performing the encryption.
	 */
	public Querier encrypt(QueryInfo queryInfo, List<String> selectors, Paillier paillier) throws InterruptedException, PIRException {
		// Determine the query vector mappings for the selectors; vecPosition -> selectorNum
		Map<Integer, Integer> selectorQueryVecMapping = computeSelectorQueryVecMap(queryInfo, selectors);

		// Form the embedSelectorMap
		// Map to check the embedded selectors in the results for false positives;
		// if the selector is a fixed size < 32 bits, it is included as is
		// if the selector is of variable lengths
		Map<Integer, String> embedSelectorMap = computeEmbeddedSelectorMap(selectors);

		SortedMap<Integer, BigInteger> queryElements =
				parallelEncrypt(selectorQueryVecMapping, queryInfo, paillier);

		logger.info("Completed parallel creation of encrypted query vectors");

		Query query = new Query(queryInfo, paillier.getN(), queryElements);

		// Generate the expTable in Query, if we are using it and if
		// useHDFSExpLookupTable is false -- if we are generating it as standalone and not on the
		// cluster
		if (queryInfo.useExpLookupTable() && !queryInfo.useHDFSExpLookupTable()) {
			logger.info("Starting expTable generation");
			generateExpTable(query);
		}

		// Return the Querier object.
		return new Querier(selectors, paillier, query, embedSelectorMap);
	}

	/**
	 * This should be called after all query elements have been added in order to generate the
	 * expTable. For int exponentiation with BigIntegers, assumes that dataPartitionBitSize < 32.
	 */
	public void generateExpTable(Query query) {
		int maxValue = (1 << query.getQueryInfo().getDataPartitionBitSize()) - 1;

		query.getQueryElements().values().parallelStream().forEach(new Consumer<BigInteger>() {
			@Override
			public void accept(BigInteger element) {
				Map<Integer, BigInteger> powMap = new HashMap<>(maxValue); // <power, element^power
																			// mod N^2>
				for (int i = 0; i <= maxValue; ++i) {
					BigInteger value = modPowAbstraction.modPow(element, BigInteger.valueOf(i), query.getNSquared());
					powMap.put(i, value);
				}
				query.addExp(element, powMap);
			}
		});
		// logger.debug("expTable.size() = " + expTable.keySet().size() + " NSquared = " +
		// NSquared.intValue() + " = " + NSquared.toString());
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

	/**
	 * Encrypt and form the query vector
	 * 
	 * @param selectorQueryVecMapping
	 * @param hashBitSize
	 * @param dataPartitionBitSize
	 * @param paillier
	 * @return
	 * @throws InterruptedException
	 * @throws PIRException
	 */
	private SortedMap<Integer, BigInteger> parallelEncrypt(Map<Integer, Integer> selectorQueryVecMapping, int hashBitSize, int dataPartitionBitSize, Paillier paillier) throws InterruptedException, PIRException {
		int numElements = 1 << hashBitSize;

		EncryptQueryTaskFactory factory = makeFactory(selectorQueryVecMapping, dataPartitionBitSize, paillier);
		CompletionService<SortedMap<Integer, BigInteger>> completionService = new ExecutorCompletionService<>(threadPool);

		// Split the work across the requested number of threads
		int elementsPerThread = numElements / numThreads;
		for (int i = 0; i < numThreads; ++i) {
			// Grab the range for this thread
			int start = i * elementsPerThread;
			int stop = start + elementsPerThread - 1;
			if (i == numThreads - 1) {
				stop = numElements - 1;
			}
			// Create the runnable and execute
			completionService.submit(factory.createTask(start, stop));
		}

		// Pull all encrypted elements and add to resultMap
		SortedMap<Integer, BigInteger> queryElements = new TreeMap<>();
		try {
			int pending = numThreads;
			while (pending > 0) {
				queryElements.putAll(completionService.take().get());
				--pending;
			}
		} catch (ExecutionException e) {
			throw new PIRException("Exception in encryption threads.", e);
		}

		return queryElements;
	}

	/**
	 * Create factory object to produce tasks for all the threads, and to pre-compute data used by
	 * all the threads if necessary
	 * 
	 * @param selectorQueryVecMapping
	 * @param dataPartitionBitSize
	 * @param paillier
	 * @return
	 */
	private EncryptQueryTaskFactory makeFactory(Map<Integer, Integer> selectorQueryVecMapping, int dataPartitionBitSize, Paillier paillier) {
		EncryptQueryTaskFactory result = null;
		if (method.equals(FASTWITHJNI)) {
			result = new EncryptQueryFixedBaseWithJNITaskFactory(dataPartitionBitSize, paillier, selectorQueryVecMapping, config, randomProvider.getSecureRandom());
		} else if (method.equals(FAST)) {
			result = new EncryptQueryFixedBaseTaskFactory(dataPartitionBitSize, paillier, selectorQueryVecMapping, randomProvider.getSecureRandom());
		} else {
			result = new EncryptQueryBasicTaskFactory(paillierEncryption, dataPartitionBitSize, paillier, selectorQueryVecMapping);
		}
		return result;
	}

	/*
	 * Performs the encryption with numThreads.
	 */
	private SortedMap<Integer, BigInteger> parallelEncrypt(Map<Integer, Integer> selectorQueryVecMapping, QueryInfo queryInfo, Paillier paillier) throws InterruptedException, PIRException {
		return parallelEncrypt(selectorQueryVecMapping, queryInfo.getHashBitSize(), queryInfo.getDataPartitionBitSize(), paillier);
	}
}
