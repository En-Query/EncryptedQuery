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
package org.enquery.encryptedquery.encryption.nullcipher;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.PlainText;
import org.enquery.encryptedquery.responder.ColumnProcessorBasic;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public class NullCipherCryptoScheme implements CryptoScheme {

	private final Logger log = LoggerFactory.getLogger(NullCipherCryptoScheme.class);

	public static final int DEFAULT_PLAINTEXT_BYTE_SIZE = 383;
	public static final int DEFAULT_PADDING_BYTE_SIZE = 0;
	
	// used only to create public keys, after that, the value in the public key should be used
	private int plainTextByteSize = DEFAULT_PLAINTEXT_BYTE_SIZE;
	private int paddingByteSize = DEFAULT_PADDING_BYTE_SIZE;
	private Map<String, String> config;
	
	@Override
	@Activate
	public void initialize(Map<String, String> cfg) throws Exception {
		Validate.notNull(cfg);

		config = extractConfig(cfg);

		plainTextByteSize = Integer.parseInt(config.getOrDefault(NullCipherProperties.PLAINTEXT_BYTE_SIZE, Integer.toString(DEFAULT_PLAINTEXT_BYTE_SIZE)));
		paddingByteSize = Integer.parseInt(config.getOrDefault(NullCipherProperties.PADDING_BYTE_SIZE, Integer.toString(DEFAULT_PADDING_BYTE_SIZE)));
		
                Validate.isTrue(plainTextByteSize >= 1);
                Validate.isTrue(paddingByteSize >= 0);

		log.info("Initialized with: " + config);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#name()
	 */
	@Override
	public String name() {
		return "Null";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#description()
	 */
	@Override
	public String description() {
		return "Null Crypto Scheme.";
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

		ColumnProcessor result = new ColumnProcessorBasic(queryInfo.getPublicKey(), queryElements, this);
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#generateKeyPair()
	 */
	@Override
	public KeyPair generateKeyPair() {
		NullCipherPrivateKey priv = 	new NullCipherPrivateKey(
				plainTextByteSize,
				paddingByteSize);
		NullCipherPublicKey pub = new NullCipherPublicKey(
				plainTextByteSize,
				paddingByteSize);

		return new KeyPair(pub, priv);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#privateKeyFromBytes(byte[])
	 */
	@Override
	public PrivateKey privateKeyFromBytes(byte[] bytes) {
		return new NullCipherPrivateKey(bytes);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#publicKeyFromBytes(byte[])
	 */
	@Override
	public PublicKey publicKeyFromBytes(byte[] bytes) {
		return new NullCipherPublicKey(bytes);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#encryptionOfZero()
	 */
	@Override
	public CipherText encryptionOfZero(PublicKey publicKey) {
		Validate.notNull(publicKey);
		Validate.isInstanceOf(NullCipherPublicKey.class, publicKey);
		return new NullCipherCipherText(BigInteger.ZERO);
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
		Validate.isInstanceOf(NullCipherCipherText.class, cipherText);
		NullCipherCipherText nullCipherCipherText = (NullCipherCipherText) cipherText;
		BigInteger c = nullCipherCipherText.getValue();
		Validate.notNull(c);

		NullCipherKeyPair nullCipherKeyPair = new NullCipherKeyPair(keyPair);
		NullCipherPrivateKey priv = nullCipherKeyPair.getPriv();

		BigInteger d = c.shiftRight(8*priv.getPaddingByteSize());
		byte[] dBytes = d.toByteArray();
		return new NullCipherPlainText(dBytes);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#decrypt(java.security.PrivateKey,
	 * java.util.List)
	 */
	@Override
	public List<PlainText> decrypt(KeyPair keyPair, List<CipherText> c) {
		return c.stream().map(ct -> decrypt(keyPair, ct)).collect(Collectors.toList());
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#decrypt(java.security.PrivateKey,
	 * java.util.stream.Stream)
	 */
	@Override
	public Stream<PlainText> decrypt(KeyPair keyPair, Stream<CipherText> c) {
		// TODO: modify this to use explicit thread pool
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

		final NullCipherKeyPair nullCipherKeyPair = new NullCipherKeyPair(keyPair);
		final NullCipherPrivateKey priv = nullCipherKeyPair.getPriv();
		final int chunkSize = queryInfo.getDataChunkSize();
		final int paddingByteSize = priv.getPaddingByteSize();

		// Compute encrypted elements and add to resultMap
		Map<Integer, CipherText> queryElements = new TreeMap<>();
		for (int i = 0; i < 1 << queryInfo.getHashBitSize(); ++i) {
			Integer selectorNum = selectorQueryVecMapping.get(i);
			BigInteger c;
			if (selectorNum == null) {
				c = BigInteger.ZERO;
			} else {
				c = BigInteger.ZERO.setBit(selectorNum * chunkSize * 8 + paddingByteSize * 8);
			}
			NullCipherCipherText queryElement = new NullCipherCipherText(c);
			queryElements.put(i, queryElement);
		}
		
		return queryElements;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#cipherTextFromBytes(byte[])
	 */
	@Override
	public CipherText cipherTextFromBytes(byte[] bytes) {
		return new NullCipherCipherText(bytes);
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
		final NullCipherPublicKey pub = NullCipherPublicKey.from(publicKey);
		final NullCipherCipherText l = NullCipherCipherText.from(left);
		final NullCipherCipherText r = NullCipherCipherText.from(right);
		final BigInteger newValue = l.getValue().add(r.getValue()).and(pub.getMask());
		return new NullCipherCipherText(newValue);
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
		final NullCipherPublicKey pub = NullCipherPublicKey.from(publicKey);
		final NullCipherCipherText l = NullCipherCipherText.from(left);
		// TODO: review data to big int conversion
		BigInteger newValue = l.getValue().multiply(new BigInteger(1, right)).and(pub.getMask());
		return new NullCipherCipherText(newValue);
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
		NullCipherPublicKey pub = NullCipherPublicKey.from(queryInfo.getPublicKey());
		Validate.isTrue(0 <= chunkIndex);
		Validate.isTrue((chunkIndex + 1) * dataChunkSize <= pub.getPlainTextByteSize());

		NullCipherPlainText ppt = NullCipherPlainText.from(plainText);
		
        byte[] bytes = ppt.getBytes();
        final int start = bytes.length - (chunkIndex + 1) * dataChunkSize;
        final int end = start + dataChunkSize;
        byte[] output;
        if (start >= 0) {
        		output = Arrays.copyOfRange(bytes, start, end);
        } else {
        		output = new byte[dataChunkSize];  // defaults to zeros
        		if (end > 0) {
        			System.arraycopy(bytes, 0, output, -start, end);
        		}
        }

        Validate.isTrue(output.length == dataChunkSize);  // XXX remove this once we've done enough testing?
        return output;
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

		for (String p : NullCipherProperties.PROPERTIES) {
			result.compute(p, (k, v) -> cfg.get(p));
		}
		return result;
	}

}
