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
package org.enquery.encryptedquery.encryption.paillier;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.concurrency.ThreadPool;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.encryption.PlainText;
import org.enquery.encryptedquery.encryption.PrimeGenerator;
import org.enquery.encryptedquery.responder.ColumnProcessorBasic;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public class PaillierCryptoScheme implements CryptoScheme {

	private final Logger log = LoggerFactory.getLogger(PaillierCryptoScheme.class);

	// Available methods for query generation.
	public enum QueryEncryptionMethodId {
		Fast, FastWithJNI
	}

	// Available column processors
	public enum ColumnProcessorId {
		Basic, DeRooij, DeRooijJNI, Yao, YaoJNI
	}

	// Method to use for query generation.
	private QueryEncryptionMethodId encryptQueryMethod = QueryEncryptionMethodId.Fast;

	private ModPowAbstraction modPowAbstraction;
	private PrimeGenerator primeGenerator;
	private RandomProvider randomProvider;
	@Reference
	private ExecutorService threadPool;
	private int primeCertainty = 128;

	// used only to create public keys, after that, the value in the public key should be used
	private int defaultModulusBitSize = 3072;
	private int encryptQueryTaskCount = 16;
	private ColumnProcessorId columnProcessorId;
	private boolean useMontgomery;
	private Map<String, String> config;

	@Override
	@Activate
	public void initialize(Map<String, String> cfg) throws Exception {
		Validate.notNull(cfg);

		config = extractConfig(cfg);

		primeCertainty = Integer.parseInt(config.getOrDefault(PaillierProperties.PRIME_CERTAINTY, "128"));
		encryptQueryTaskCount = Integer.parseInt(config.getOrDefault(PaillierProperties.ENCRYPT_QUERY_TASK_COUNT, "16"));
		defaultModulusBitSize = Integer.parseInt(config.getOrDefault(PaillierProperties.MODULUS_BIT_SIZE, "3072"));

		encryptQueryMethod = QueryEncryptionMethodId.valueOf(
				config.getOrDefault(PaillierProperties.ENCRYPT_QUERY_METHOD,
						QueryEncryptionMethodId.Fast.toString()));

		columnProcessorId = ColumnProcessorId.valueOf(
				config.getOrDefault(PaillierProperties.COLUMN_PROCESSOR,
						ColumnProcessorId.Basic.toString()));

		intializeReferences(config);

		log.info("Initialized with: " + config);
	}

	/**
	 * In non-OSGi environments, the @Reference annotated field needs to be manually initialized.
	 * This allow this class to be used, for example, in stand-alone, Flink, Hadoop, etc.
	 * 
	 * @param config
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	private void intializeReferences(Map<String, String> config) throws Exception {
		if (modPowAbstraction == null) {
			modPowAbstraction = makeModPowAbstraction(config);
		}

		if (randomProvider == null) {
			randomProvider = makeRandomProvider(config);
		}

		if (primeGenerator == null) {
			primeGenerator = new PrimeGenerator(modPowAbstraction, randomProvider);
		}

		if (threadPool == null) {
			threadPool = makeThreadPool(config);
		}
	}

	private ExecutorService makeThreadPool(Map<String, String> config) {
		ThreadPool result = new ThreadPool();
		Map<String, String> poolConfig = new HashMap<>();

		poolConfig.compute(ThreadPool.CORE_POOL_SIZE, (k, v) -> config.get(PaillierProperties.CORE_POOL_SIZE));
		poolConfig.compute(ThreadPool.CORE_POOL_SIZE, (k, v) -> config.get(PaillierProperties.CORE_POOL_SIZE));
		poolConfig.compute(ThreadPool.MAX_TASK_QUEUE_SIZE, (k, v) -> config.get(PaillierProperties.MAX_TASK_QUEUE_SIZE));
		poolConfig.compute(ThreadPool.SHUTDOWN_WAIT_TIME_SECONDS, (k, v) -> config.get(PaillierProperties.SHUTDOWN_WAIT_TIME_SECONDS));
		poolConfig.compute(ThreadPool.KEEP_ALIVE_TIME_SECONDS, (k, v) -> config.get(PaillierProperties.KEEP_ALIVE_TIME_SECONDS));
		poolConfig.compute(ThreadPool.MAX_POOL_SIZE, (k, v) -> config.get(PaillierProperties.MAX_POOL_SIZE));
		result.initialize(poolConfig);
		return result;
	}



	@SuppressWarnings("unchecked")
	private ModPowAbstraction makeModPowAbstraction(Map<String, String> config) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		String modPowClassName = config.get(PaillierProperties.MOD_POW_CLASS_NAME);
		Validate.notBlank(modPowClassName, "Missing configuration key: %s", PaillierProperties.MOD_POW_CLASS_NAME);
		Class<ModPowAbstraction> modPowClass = (Class<ModPowAbstraction>) Class.forName(modPowClassName);
		return modPowClass.newInstance();
	}

	private RandomProvider makeRandomProvider(Map<String, String> config) {
		Map<String, String> randomConfig = new HashMap<>();
		randomConfig.compute(RandomProvider.SECURE_RANDOM_ALG, (k, v) -> config.get(PaillierProperties.SECURE_RANDOM_ALG));
		randomConfig.compute(RandomProvider.SECURE_RANDOM_PROVIDER, (k, v) -> config.get(PaillierProperties.SECURE_RANDOM_PROVIDER));
		RandomProvider result = new RandomProvider();
		result.initialize(randomConfig);
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#name()
	 */
	@Override
	public String name() {
		return "Paillier";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#description()
	 */
	@Override
	public String description() {
		return "Paillier Crypto Scheme.";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#makeColumnProcessor(java.security.
	 * PublicKey, java.util.Map)
	 */
	@Override
	public ColumnProcessor makeColumnProcessor(QueryInfo queryInfo,
			Map<Integer, CipherText> queryElements) {

		Validate.notNull(queryInfo);
		Validate.notNull(queryElements);


		PaillierPublicKey ppk = PaillierPublicKey.from(queryInfo.getPublicKey());
		ColumnProcessor result = null;
		switch (columnProcessorId) {
			case Basic:
				result = new ColumnProcessorBasic(queryInfo.getPublicKey(), queryElements, this);
				break;
			case DeRooij:
				result = new DeRooijColumnProcessor(ppk, queryElements, modPowAbstraction, useMontgomery);
				break;
			case DeRooijJNI:
				result = new DeRooijJNIColumnProcessor(ppk, queryElements, queryInfo, config);
				break;
			case Yao:
				result = new YaoColumnProcessor(ppk, queryElements, useMontgomery, queryInfo.getDataChunkSize());
				break;
			case YaoJNI:
				result = new YaoJNIColumnProcessor(ppk, queryElements, useMontgomery, queryInfo, config);
				break;
		}

		Validate.notNull(result, "Invalid column processor id: %s.", columnProcessorId);
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#generateKeyPair()
	 */
	@Override
	public KeyPair generateKeyPair() {
		PaillierPrivateKey priv = null;

		// Generate the primes
		if (defaultModulusBitSize >= 1024) {
			BigInteger[] pq = primeGenerator.getPrimePairWithAuxiliaryPrimes(defaultModulusBitSize, primeCertainty);
			priv = makePrivateKey(pq[0], pq[1], pq[2], pq[3]);
		} else {
			BigInteger[] pq = primeGenerator.getPrimePair(defaultModulusBitSize, primeCertainty);
			priv = makePrivateKey(pq[0], pq[1]);
		}

		final BigInteger n = priv.getP().multiply(priv.getQ());
		PaillierPublicKey pub = new PaillierPublicKey(n, defaultModulusBitSize);

		return new KeyPair(pub, priv);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#privateKeyFromBytes(byte[])
	 */
	@Override
	public PrivateKey privateKeyFromBytes(byte[] bytes) {
		return new PaillierPrivateKey(bytes);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#publicKeyFromBytes(byte[])
	 */
	@Override
	public PublicKey publicKeyFromBytes(byte[] bytes) {
		return new PaillierPublicKey(bytes);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#encryptionOfZero()
	 */
	@Override
	public CipherText encryptionOfZero(PublicKey publicKey) {
		Validate.notNull(publicKey);
		Validate.isInstanceOf(PaillierPublicKey.class, publicKey);
		return new PaillierCipherText(BigInteger.ONE);
	}

	private PaillierPrivateKey makePrivateKey(BigInteger p, BigInteger p1, BigInteger q, BigInteger q1) {
		BigInteger[] pBasePointAndMaxExponent = calcBasePointAndExponentWithAuxiliaryPrime(p, p1);
		BigInteger pBasePoint = pBasePointAndMaxExponent[0];
		BigInteger pMaxExponent = pBasePointAndMaxExponent[1];
		BigInteger[] qBasePointAndMaxExponent = calcBasePointAndExponentWithAuxiliaryPrime(q, q1);
		BigInteger qBasePoint = qBasePointAndMaxExponent[0];
		BigInteger qMaxExponent = qBasePointAndMaxExponent[1];


		return new PaillierPrivateKey(
				p,
				q,
				pBasePoint,
				qBasePoint,
				pMaxExponent,
				qMaxExponent,
				defaultModulusBitSize);
	}

	/**
	 * Called to initialize the Paillier object's base points and maximum exponents for the order
	 * p-1 subgroup of (Z/p^2Z)* and the order q-1 subgroup of (Z/p^2Z)*. This function is called
	 * during Paillier object construction in the case when auxiliary primes are not being used.
	 *
	 * Each of the two base points is generated by raising a random value to the p-th or q-th power
	 * and does not necessarily generate the entire respective subgroup. The maximum exponents are
	 * set to p-1 and q-1.
	 * 
	 * @param q
	 * @param p
	 */
	private PaillierPrivateKey makePrivateKey(BigInteger p, BigInteger q) {
		BigInteger pSquared = p.multiply(p);
		BigInteger qSquared = q.multiply(q);

		BigInteger pBasePoint;
		// setting base point to (rand)^p mod p^2
		// this depends only on rand mod p so we can pick rand < p
		do {
			pBasePoint = new BigInteger(p.bitLength(), randomProvider.getSecureRandom());
		} while (pBasePoint.compareTo(BigInteger.ONE) <= 0 || pBasePoint.compareTo(p) >= 0);

		pBasePoint = modPowAbstraction.modPow(pBasePoint, p, pSquared);
		BigInteger pMaxExponent = p.subtract(BigInteger.ONE);

		// doing the same for q
		BigInteger qBasePoint;
		do {
			qBasePoint = new BigInteger(q.bitLength(), randomProvider.getSecureRandom());
		} while (qBasePoint.compareTo(BigInteger.ONE) <= 0 || qBasePoint.compareTo(q) >= 0);

		qBasePoint = modPowAbstraction.modPow(qBasePoint, q, qSquared);
		BigInteger qMaxExponent = q.subtract(BigInteger.ONE);

		return new PaillierPrivateKey(
				p,
				q,
				pBasePoint,
				qBasePoint,
				pMaxExponent,
				qMaxExponent,
				defaultModulusBitSize);
	}

	/**
	 * Returns an ordered pair consisting of a choice of basepoint and maximum exponent for either
	 * the order p1 subgroup of (Z/p^2Z)* or the order q1 subgroup of (Z/p^2Z)*. This function is
	 * called during Paillier object construction in the case when auxiliary primes are also
	 * generated.
	 *
	 * The base point is generated by raising a random value to the p-th or q-th power; it is then
	 * checked that the base point has order divisible by p1 or p2. The base points do not
	 * necessarily generate the entire respective subgroup. The maximum exponent is set to the value
	 * of the auxiliary prime p1.
	 *
	 * @return array whose first element is the base point and the second element is the maximum
	 *         exponent
	 */
	private BigInteger[] calcBasePointAndExponentWithAuxiliaryPrime(BigInteger p, BigInteger p1) {
		// checking p1 divides p - 1 and saving (p - 1)/p1 for later
		BigInteger[] divmod = p.subtract(BigInteger.ONE).divideAndRemainder(p1);
		BigInteger div = divmod[0];
		BigInteger rem = divmod[1];
		if (rem.compareTo(BigInteger.ZERO) != 0) {
			throw new IllegalArgumentException("auxiliary prime fails to divide prime minus one");
		}
		BigInteger pSquared = p.multiply(p);
		// exponent and random base point for p^2
		BigInteger maxExponent = p1;
		BigInteger[] result = null;
		while (result == null) {
			BigInteger basePoint = new BigInteger(p.bitLength(), randomProvider.getSecureRandom());
			if (basePoint.compareTo(BigInteger.ONE) <= 0 || basePoint.compareTo(p) >= 0) continue;

			basePoint = modPowAbstraction.modPow(basePoint, p, pSquared);
			BigInteger tmp = modPowAbstraction.modPow(basePoint, div, pSquared);
			if (tmp.compareTo(BigInteger.ONE) == 0) continue;

			result = new BigInteger[] {basePoint, maxExponent};
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#decrypt(java.security.PrivateKey,
	 * org.enquery.encryptedquery.encryption.CipherText)
	 */
	@Override
	public PlainText decrypt(KeyPair keyPair, CipherText cipherText) {
		Validate.notNull(cipherText);
		Validate.isInstanceOf(PaillierCipherText.class, cipherText);
		PaillierCipherText paillierCipherText = (PaillierCipherText) cipherText;
		BigInteger c = paillierCipherText.getValue();
		Validate.notNull(c);

		PaillierKeyPair paillierKeyPair = new PaillierKeyPair(keyPair);
		PaillierPublicKey pub = paillierKeyPair.getPub();
		PaillierPrivateKey priv = paillierKeyPair.getPriv();

		// x = c^(p-1) mod p^2, y = (x - 1)/p, z = y * ((p-1)*q)^-1 mod p
		// x' = c^(q-1) mod q^2, y' = (x'- 1)/q, z' = y' * ((q-1)*p)^-1 mod q
		// d = crt.combine(z, z')
		BigInteger p = priv.getP();
		BigInteger q = priv.getQ();
		BigInteger cModPSquared = c.mod(priv.getPSquared());
		BigInteger cModQSquared = c.mod(priv.getQSquared());
		BigInteger xp = modPowAbstraction.modPow(cModPSquared, priv.getPMinusOne(), priv.getPSquared());
		BigInteger xq = modPowAbstraction.modPow(cModQSquared, priv.getQMinusOne(), priv.getQSquared());
		BigInteger yp = xp.subtract(BigInteger.ONE).divide(p);
		BigInteger yq = xq.subtract(BigInteger.ONE).divide(q);
		BigInteger zp = yp.multiply(priv.getWp()).mod(p);
		BigInteger zq = yq.multiply(priv.getWq()).mod(q);
		BigInteger d = priv.getCrtN().combine(zp, zq, pub.getN());
		return new PaillierPlainText(d);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#decrypt(java.security.PrivateKey,
	 * java.util.stream.Stream)
	 */
	@Override
	public Stream<PlainText> decrypt(KeyPair keyPair, Stream<CipherText> c) {
		return c.parallel()
				.map(ct -> decrypt(keyPair, ct));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#generateQueryVector(int,
	 * java.util.Map)
	 */
	@Override
	public Map<Integer, CipherText> generateQueryVector(KeyPair keyPair, QueryInfo queryInfo, Map<Integer, Integer> selectorQueryVecMapping) {

		Validate.notNull(keyPair);
		Validate.notNull(queryInfo);
		Validate.notNull(selectorQueryVecMapping);

		final PaillierKeyPair paillierKeyPair = new PaillierKeyPair(keyPair);
		final int dataPartitionBitSize = queryInfo.getDataChunkSize() * 8;
		final EncryptQueryTaskFactory factory = makeFactory(paillierKeyPair, dataPartitionBitSize, selectorQueryVecMapping);
		final CompletionService<Map<Integer, CipherText>> completionService = new ExecutorCompletionService<>(threadPool);

		// Split the work across the requested number of threads
		final int numElements = 1 << queryInfo.getHashBitSize();
		final int elementsPerThread = numElements / encryptQueryTaskCount;
		for (int i = 0; i < encryptQueryTaskCount; ++i) {
			// Grab the range for this thread
			int start = i * elementsPerThread;
			int stop = start + elementsPerThread - 1;
			if (i == encryptQueryTaskCount - 1) {
				stop = numElements - 1;
			}
			// Create the runnable and execute
			completionService.submit(factory.createTask(start, stop));
		}

		// Pull all encrypted elements and add to resultMap
		Map<Integer, CipherText> queryElements = new TreeMap<>();
		try {
			int pending = encryptQueryTaskCount;
			while (pending > 0) {
				queryElements.putAll(completionService.take().get());
				--pending;
			}
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException("Exception in encryption threads.", e);
		}

		return queryElements;
	}

	/**
	 * Create factory object to produce tasks for all the threads, and to pre-compute data used by
	 * all the threads if necessary
	 * 
	 * @param publicKey
	 * 
	 * @param selectorQueryVecMapping
	 * @param dataPartitionBitSize
	 * @param paillier
	 * @return
	 */
	private EncryptQueryTaskFactory makeFactory(//
			PaillierKeyPair keyPair,
			int dataPartitionBitSize,
			Map<Integer, Integer> selectorQueryVecMapping) //
	{

		EncryptQueryTaskFactory result = null;
		switch (encryptQueryMethod) {
			case Fast:
				result = new EncryptQueryFixedBaseTaskFactory(
						dataPartitionBitSize,
						selectorQueryVecMapping,
						randomProvider.getSecureRandom(),
						keyPair);
				break;

			case FastWithJNI:
				result = new EncryptQueryFixedBaseWithJNITaskFactory(//
						dataPartitionBitSize,
						selectorQueryVecMapping,
						randomProvider.getSecureRandom(),
						keyPair);
				break;
		}

		Validate.notNull(result, "Unknown EncryptQueryTaskFactory method: %s", encryptQueryMethod);
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#cipherTextFromBytes(byte[])
	 */
	@Override
	public CipherText cipherTextFromBytes(byte[] bytes) {
		return new PaillierCipherText(bytes);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.encryption.CryptoScheme#computeCipherAdd(java.security.PublicKey,
	 * org.enquery.encryptedquery.encryption.CipherText,
	 * org.enquery.encryptedquery.encryption.CipherText)
	 */
	@Override
	public CipherText computeCipherAdd(PublicKey publicKey, CipherText left, CipherText right) {
		final PaillierPublicKey k = PaillierPublicKey.from(publicKey);
		final PaillierCipherText l = PaillierCipherText.from(left);
		final PaillierCipherText r = PaillierCipherText.from(right);
		final BigInteger newValue = (l.getValue().multiply(r.getValue())).mod(k.getNSquared());
		return new PaillierCipherText(newValue);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.encryption.CryptoScheme#computeCipherPlainMultiply(java.security.
	 * PublicKey, org.enquery.encryptedquery.encryption.CipherText, byte[])
	 */
	@Override
	public CipherText computeCipherPlainMultiply(PublicKey publicKey, CipherText left, byte[] right) {
		final PaillierPublicKey k = PaillierPublicKey.from(publicKey);
		final PaillierCipherText l = PaillierCipherText.from(left);
		// TODO: review data to big int conversion
		BigInteger newValue = modPowAbstraction.modPow(l.getValue(), new BigInteger(1, right), k.getNSquared());
		return new PaillierCipherText(newValue);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.encryption.CryptoScheme#plainTextChunk(org.enquery.encryptedquery.
	 * data.QueryInfo, org.enquery.encryptedquery.encryption.PlainText, int)
	 */
	@Override
	public byte[] plainTextChunk(QueryInfo queryInfo, PlainText plainText, int chunkIndex) {
		Validate.notNull(queryInfo);
		Validate.notNull(plainText);

		final int dataChunkSize = queryInfo.getDataChunkSize();
		final PaillierPublicKey pub = PaillierPublicKey.from(queryInfo.getPublicKey());
		Validate.isTrue(0 <= chunkIndex);
		Validate.isTrue((chunkIndex + 1) * dataChunkSize * 8 <= pub.getModulusBitSize());

		final PaillierPlainText ppt = PaillierPlainText.from(plainText);

		final byte[] bytes = ppt.getValue();
		byte[] result = null;
		final int start = bytes.length - (chunkIndex + 1) * dataChunkSize;
		final int end = start + dataChunkSize;
		if (start >= 0) {
			result = Arrays.copyOfRange(bytes, start, end);
		} else {
			// defaults to zeros
			result = new byte[dataChunkSize];
			if (end > 0) {
				System.arraycopy(bytes, 0, result, -start, end);
			}
		}

		Validate.isTrue(result.length == dataChunkSize);
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#configuration()
	 */
	@Override
	public Iterable<Map.Entry<String, String>> configurationEntries() {
		return config.entrySet();
	}



	/**
	 * @param cfg
	 * @return
	 */
	private Map<String, String> extractConfig(Map<String, String> cfg) {
		// weird Osgi is sending Map where not all entry values are string
		// we are only interested in capturing the string values
		Map<String, String> result = new HashMap<>();

		for (String p : PaillierProperties.PROPERTIES) {
			result.compute(p, (k, v) -> cfg.get(p));
		}
		return result;
	}

}
