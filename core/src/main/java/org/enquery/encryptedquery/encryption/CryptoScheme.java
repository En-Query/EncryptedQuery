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
package org.enquery.encryptedquery.encryption;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.stream.Stream;

import org.enquery.encryptedquery.data.QueryInfo;

/**
 * Each instance of this class encapsulates both a choice of Crypto scheme, plus a set of
 * parameters. e.g. "Paillier"
 * 
 * 
 */
public interface CryptoScheme {

	/**
	 * Call once after creation.
	 * 
	 * @param config
	 */
	void initialize(Map<String, String> config) throws Exception;

	/**
	 * Read only access to configuration used to initialize this CryptoScheme
	 * 
	 * @param config
	 */
	Iterable<Map.Entry<String, String>> configurationEntries();

	/**
	 * Name of this CryptoScheme. e.g. "Paillier". Must be unique name.
	 * 
	 * @return Name, not null.
	 */
	String name();

	/**
	 * Description of this CryptoScheme.
	 * 
	 * @return Description string, not null.
	 */
	String description();

	/**
	 * Initializes and return a new instance of ColumnProcessor.
	 * 
	 * @return a column processor
	 */
	ColumnProcessor makeColumnProcessor(QueryInfo queryInfo,
			Map<Integer, CipherText> queryElements);

	/**
	 * Generates a key pair.
	 * 
	 * @return
	 */
	KeyPair generateKeyPair();

	PrivateKey privateKeyFromBytes(byte[] bytes);

	PublicKey publicKeyFromBytes(byte[] bytes);

	CipherText cipherTextFromBytes(byte[] bytes);

	CipherText encryptionOfZero(PublicKey publicKey);

	PlainText decrypt(KeyPair keyPair, CipherText cipherText);

	Stream<PlainText> decrypt(KeyPair keyPair, Stream<CipherText> c);

	/**
	 * Generates a query vector.<br/>
	 * 
	 * The mapping <code>selectorQueryVecMapping</code> encodes information for how data for
	 * different selectors are packed into plaintexts.
	 * 
	 * @param keyPair Key pair containing private and public key
	 * @param hashBitSize
	 * @param selectorQueryVecMapping Keys in this map are the possible selector hash values, i.e. 0
	 *        ... 2^hashBitSize-1, and values are defined when the key is the hash value of a
	 *        targeted selector. When defined, the value indicates which targeted selector the hash
	 *        value corresponds to (e.g. the 0-th, 1-st, ....).
	 * @return
	 */
	Map<Integer, CipherText> generateQueryVector(KeyPair keyPair, QueryInfo queryInfo, Map<Integer, Integer> selectorQueryVecMapping);

	CipherText computeCipherAdd(PublicKey publicKey, CipherText left, CipherText right);

	CipherText computeCipherPlainMultiply(PublicKey publicKey, CipherText left, byte[] right);

	/**
	 * Extract a plain text chunk from a plain text
	 * 
	 * @param queryInfo
	 * @param plainText
	 * @param chunkIndex > 0, index of the chunk to extract
	 * @return
	 */
	byte[] plainTextChunk(QueryInfo queryInfo, PlainText plainText, int chunkIndex);
}
