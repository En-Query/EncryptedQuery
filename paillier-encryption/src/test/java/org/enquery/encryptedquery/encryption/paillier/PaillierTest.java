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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.PlainText;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaillierTest {

	private final Logger log = LoggerFactory.getLogger(PaillierTest.class);

	final int TEST_PRIME_CERTAINTY = 128;
	final int TEST_ENCRYPT_QUERY_TASK_COUNT = 1;
	final String TEST_MOD_POW_CLASS_NAME = "org.enquery.encryptedquery.encryption.impl.ModPowAbstractionJavaImpl";

	class State {
		private final HashMap<String, String> cfg;
		private final PaillierCryptoScheme scheme;
		private final KeyPair kp;
		private final PaillierPublicKey pub;
		private final PaillierPrivateKey priv;

		public State(int modulusBitSize, String columnProcessor) throws Exception {
			cfg = new HashMap<>();
			cfg.put(PaillierProperties.MODULUS_BIT_SIZE, Integer.toString(modulusBitSize));
			cfg.put(PaillierProperties.PRIME_CERTAINTY, Integer.toString(TEST_PRIME_CERTAINTY));
			cfg.put(PaillierProperties.MOD_POW_CLASS_NAME, TEST_MOD_POW_CLASS_NAME);
			cfg.put(PaillierProperties.ENCRYPT_QUERY_TASK_COUNT, Integer.toString(TEST_ENCRYPT_QUERY_TASK_COUNT));
			if (columnProcessor != null) {
				cfg.put(PaillierProperties.COLUMN_PROCESSOR, columnProcessor);
			}
			scheme = new PaillierCryptoScheme();
			scheme.initialize(cfg);
			kp = scheme.generateKeyPair();
			pub = PaillierPublicKey.from(kp.getPublic());
			priv = PaillierPrivateKey.from(kp.getPrivate());
		}

		public PaillierCryptoScheme getScheme() {
			return this.scheme;
		}

		public KeyPair getKeyPair() {
			return this.kp;
		}

		public PaillierPublicKey getPubkey() {
			return this.pub;
		}

		public PaillierPrivateKey getPrivkey() {
			return this.priv;
		}
	}

	@Before
	public void prepare() {}

	private boolean fermatTest(BigInteger u) {
		BigInteger two = BigInteger.valueOf(2);
		BigInteger pow = two.modPow(u.subtract(BigInteger.ONE), u);
		return pow.compareTo(BigInteger.ONE) == 0;
	}

	private void doCofactorTestCase(int modulusBitSize) throws Exception {
		State state = new State(modulusBitSize, null);
		PaillierPrivateKey priv = state.getPrivkey();
		BigInteger p = priv.getP();
		BigInteger q = priv.getQ();
		BigInteger p1 = priv.getPMaxExponent();
		BigInteger q1 = priv.getQMaxExponent();
		assertTrue(fermatTest(p));
		assertTrue(fermatTest(q));
		if (modulusBitSize >= 1024) {
			// p1 and q1 should be prime divisors of p-1 and q-1
			assertTrue(fermatTest(p1));
			assertTrue(fermatTest(q1));
			assertEquals(p.subtract(BigInteger.ONE).mod(p1).compareTo(BigInteger.ZERO), 0);
			assertEquals(q.subtract(BigInteger.ONE).mod(q1).compareTo(BigInteger.ZERO), 0);
		} else {
			// p1 and q1 should be p-1 and q-1
			assertEquals(p1.compareTo(p.subtract(BigInteger.ONE)), 0);
			assertEquals(q1.compareTo(q.subtract(BigInteger.ONE)), 0);
		}

	}

	@Test
	public void testCofactors() throws Exception {
		doCofactorTestCase(512);
		doCofactorTestCase(1024);
		doCofactorTestCase(2048);
		doCofactorTestCase(3072);
	}

	@Test
	public void testQueryVectors() throws Exception {
		int[] keySizes = {512, 1024, 2048};
		String[] columnProcessors = {null, "Basic", "DeRooij", "Yao"};
		for (int keySize : keySizes) {
			for (String columnProcessor : columnProcessors) {
				log.info("keySize = {}, columnProcessor = {}", keySize, columnProcessor);
				runTestCase(keySize, columnProcessor);
			}
		}
		log.info("keySize = {}, columnProcessor = {}", 3072, "Basic");
		runTestCase(3072, "Basic");
	}

	private void runTestCase(int modulusBitSize, String columnProcessor) throws Exception {
		final int dataChunkSize = 2;
		final int hashBitSize = 8;
		final int[] invMap = {0, 2, 4, 8, 10};
		HashMap<Integer, Integer> map = new HashMap<>(); // "selectorQueryVecMapping"
		State state = new State(modulusBitSize, columnProcessor);
		PaillierPublicKey pub = state.getPubkey();
		assertEquals(pub.getModulusBitSize(), modulusBitSize);
		for (int i = 0; i < invMap.length; ++i) {
			map.put(invMap[i], i);
		}
		PaillierCryptoScheme scheme = state.getScheme();
		KeyPair kp = state.getKeyPair();
		QueryInfo queryInfo = new QueryInfo();
		queryInfo.setPublicKey(pub);
		queryInfo.setHashBitSize(hashBitSize);
		queryInfo.setDataChunkSize(dataChunkSize);
		Map<Integer, CipherText> queryVector = scheme.generateQueryVector(kp, queryInfo, map);

		// For each query element, multiply with a fixed test plaintext and then decrypt.
		// Check that each chunk of the plaintext has the expected value.
		byte[] dataChunk = {(byte) 0xca, (byte) 0xfe};
		byte[] zeroDataChunk = {(byte) 0, (byte) 0};
		for (int i = 0; i < (1 << hashBitSize); ++i) {
			CipherText queryElement = queryVector.get(i);
			CipherText prod = scheme.computeCipherPlainMultiply(pub, queryElement, dataChunk);
			PlainText dec = scheme.decrypt(kp, prod);
			if (map.get(i) == null) {
				for (int j = 0; j < invMap.length; j++) {
					byte[] decBytes = scheme.plainTextChunk(queryInfo, dec, j);
					assertArrayEquals(decBytes, zeroDataChunk);
				}
			} else {
				for (int j = 0; j < invMap.length; j++) {
					byte[] decBytes = scheme.plainTextChunk(queryInfo, dec, j);
					if (j == map.get(i)) {
						assertArrayEquals(decBytes, dataChunk);
					} else {
						assertArrayEquals(decBytes, zeroDataChunk);
					}
				}

			}
		}

		// Testing the homomorphic property on the query elements.
		//
		// The query elements corresponding to the special hash values
		// are multiplied with the special data chunks, while the other
		// query elements are multiplied with the dummy chunk. The products
		// are added together and the result is decrypted. It is checked
		// that the special data chunks can be extracted from the resulting
		// PlainText.
		byte[][] dataChunks = {
				{(byte) 0xca, (byte) 0xfe,},
				{(byte) 0xde, (byte) 0xad,},
				{(byte) 0x00, (byte) 0x01,},
				{(byte) 0x03, (byte) 0x04,},
				{(byte) 0x06, (byte) 0x07,}
		};
		byte[] dummyChunk = {(byte) 0xff, (byte) 0xfe,};
		assertEquals(dataChunks.length, invMap.length);
		for (int i = 0; i < dataChunks.length; ++i) {
			assertEquals(dataChunks[i].length, dataChunkSize);
		}
		CipherText acc = scheme.encryptionOfZero(pub);
		for (int i = 0; i < (1 << hashBitSize); ++i) {
			CipherText queryElement = queryVector.get(i);
			byte[] pt = (map.get(i) == null) ? dummyChunk : dataChunks[map.get(i)];
			CipherText tmp = scheme.computeCipherPlainMultiply(pub, queryElement, pt);
			acc = scheme.computeCipherAdd(pub, acc, tmp);
		}
		PlainText decrypt = scheme.decrypt(kp, acc);
		for (int i = 0; i < invMap.length; ++i) {
			byte[] chunk = scheme.plainTextChunk(queryInfo, decrypt, i);
			assertArrayEquals(chunk, dataChunks[i]);
		}

		// Repeating the same test, this time with a column processor
		ColumnProcessor cp = scheme.makeColumnProcessor(queryInfo, queryVector);
		for (int i = 0; i < (1 << hashBitSize); i++) {
			byte[] pt = (map.get(i) == null) ? dummyChunk : dataChunks[map.get(i)];
			cp.insert(i, pt);
		}
		CipherText acc2 = cp.compute();
		PlainText decrypt2 = scheme.decrypt(kp, acc2);
		for (int i = 0; i < invMap.length; ++i) {
			byte[] chunk = scheme.plainTextChunk(queryInfo, decrypt2, i);
			assertArrayEquals(chunk, dataChunks[i]);
		}
	}

	// illegal column processor
	@Test(expected = IllegalArgumentException.class)
	public void initializeSchemeWithInvalidColumnProcessor() throws Exception {
		HashMap<String, String> cfg = new HashMap<>();
		cfg.put(PaillierProperties.COLUMN_PROCESSOR, "NoSuchColumnProcessor");
		PaillierCryptoScheme scheme = new PaillierCryptoScheme();
		scheme.initialize(cfg);
	}

	/**
	 * An attempt to cast a null pointer into a NullCipherCipherText should fail
	 */
	@Test(expected = NullPointerException.class)
	public void createCipherFromNull() {
		PaillierCipherText.from(null);
	}

}
