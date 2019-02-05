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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import java.util.HashMap;
import java.util.Map;
import java.security.KeyPair;

import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.PlainText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.nullcipher.NullCipherCryptoScheme;
import org.enquery.encryptedquery.encryption.nullcipher.NullCipherCipherText;
import org.enquery.encryptedquery.encryption.nullcipher.NullCipherProperties;
import org.junit.Before;
import org.junit.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

public class NullCipherTest {

//	private final Logger log = LoggerFactory.getLogger(NullCipherTest.class);

	class State {
		private final HashMap<String,String> cfg;
		private final NullCipherCryptoScheme scheme;
		private final KeyPair kp;
		private final NullCipherPublicKey pub;
		private final NullCipherPrivateKey priv;
		public State(int plainTextByteSize, int paddingByteSize) throws Exception {
			cfg = new HashMap<String,String>();
			cfg.put(NullCipherProperties.PLAINTEXT_BYTE_SIZE, Integer.toString(plainTextByteSize));
			cfg.put(NullCipherProperties.PADDING_BYTE_SIZE, Integer.toString(paddingByteSize));
			scheme = new NullCipherCryptoScheme();
			scheme.initialize(cfg);
			kp = scheme.generateKeyPair();
			pub = NullCipherPublicKey.from(kp.getPublic());
			priv = NullCipherPrivateKey.from(kp.getPrivate());
		}
		public NullCipherCryptoScheme getScheme() { return this.scheme; }
		public KeyPair getKeyPair() { return this.kp; }
		public NullCipherPublicKey getPubkey() { return this.pub; }
		public NullCipherPrivateKey getPrivkey() { return this.priv; }
	}
	
	@Before
	public void prepare() {
	}
	
	@Test
	public void testQueryVectors() throws Exception {
		final int plainTextByteSize = 15;
		final int paddingByteSize = 2;
		final int dataChunkSize = 3;
		final int hashBitSize = 8;
		final int[] invMap = { 0, 2, 4, 8, 10 };
		HashMap<Integer,Integer> map = new HashMap<Integer,Integer>();  // "selectorQueryVecMapping"
		State state = new State(plainTextByteSize, paddingByteSize);
		NullCipherPublicKey pub = state.getPubkey();
		assertEquals(pub.getPlainTextByteSize(), plainTextByteSize);
		assertEquals(pub.getPaddingByteSize(), paddingByteSize);
		for (int i = 0; i < invMap.length; ++i) {
			map.put(invMap[i], i);
		}
		NullCipherCryptoScheme scheme = state.getScheme();
		KeyPair kp = state.getKeyPair();
		QueryInfo queryInfo = new QueryInfo();
		queryInfo.setPublicKey(pub);
		queryInfo.setHashBitSize(hashBitSize);
		queryInfo.setDataChunkSize(dataChunkSize);
		Map<Integer,CipherText> queryVector = scheme.generateQueryVector(kp, queryInfo, map);
		
		// For each query element, multiply with a fixed test plaintext and then decrypt.
		// Check that each chunk of the plaintext has the expected value.
		byte[] dataChunk = { (byte)0xca, (byte)0xfe, (byte)0xba };
		byte[] zeroDataChunk = { (byte)0, (byte)0, (byte)0 };
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
				{ (byte)0xca, (byte)0xfe, (byte)0xba },
				{ (byte)0xde, (byte)0xad, (byte)0xbe },
				{ (byte)0x00, (byte)0x01, (byte)0x02 },
				{ (byte)0x03, (byte)0x04, (byte)0x05 },
				{ (byte)0x06, (byte)0x07, (byte)0x00 }
		};
		byte[] dummyChunk = { (byte)0xff, (byte)0xfe, (byte)0xfd };
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
	
	@Test(expected = IllegalArgumentException.class)
	public void initializeSchemeWithInvalidPlaintextByteSize() throws Exception {
		HashMap<String,String> cfg = new HashMap<String,String>();
		cfg.put(NullCipherProperties.PLAINTEXT_BYTE_SIZE, "0");
		NullCipherCryptoScheme scheme = new NullCipherCryptoScheme();
		scheme.initialize(cfg);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void initializeSchemeWithInvalidPaddingByteSize() throws Exception {
		HashMap<String,String> cfg = new HashMap<String,String>();
		cfg.put(NullCipherProperties.PADDING_BYTE_SIZE, "-1");
		NullCipherCryptoScheme scheme = new NullCipherCryptoScheme();
		scheme.initialize(cfg);
	}
	
	/**
	 * An attempt to cast a null pointer into a NullCipherCipherText should 
	 */
	@Test(expected = NullPointerException.class)
	public void createCipherFromNull() {
		NullCipherCipherText.from(null);
	}
	
}