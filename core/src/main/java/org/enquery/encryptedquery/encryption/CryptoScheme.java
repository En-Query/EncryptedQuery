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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.enquery.encryptedquery.data.QueryInfo;

/**
 * Each instance of this class encapsulates both a choice of Crypto scheme, plus a set of
 * parameters. e.g. "Paillier"
 * 
 * 
 */
public interface CryptoScheme extends AutoCloseable {

	/**
	 * Call once after creation. Any resources obtained during this initialization should be
	 * released in the AutoCloseable#close() method
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
	 * Initializes the Crypto subsystem with a Query to be executed. This should be used by
	 * Responder as part of running a query. The query data is used by one more more Column
	 * Processors returned by <code>makeColumnProcessor</code>. The content of the returned handle
	 * is implementation dependent, and should be considered opaque, and not modified in any form.
	 * 
	 * @param queryInfo
	 * @param queryElements
	 * @return Opaque handle to the Crypto internal query.
	 */
	byte[] loadQuery(QueryInfo queryInfo, Map<Integer, CipherText> queryElements);

	/**
	 * Destroy previously initialized Query given its handle. Any memory or other resources
	 * allocated by <code>loadQuery</code> call will be dellocated by this call. Any running
	 * processes associated to this query will be interrupted. The handle becomes invalid and can't
	 * be used any longer.
	 * 
	 * @param handle Handle obtained from <code>createQuery</code>
	 */
	void unloadQuery(byte[] handle);

	/**
	 * Initializes and return a new instance of ColumnProcessor.
	 * 
	 * @param handle Handle obtained from <code>createQuery</code>
	 * @return a column processor
	 */
	ColumnProcessor makeColumnProcessor(byte[] handle);

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

	List<PlainText> decrypt(KeyPair keyPair, List<CipherText> c);

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

	/**
	 * This function performs the homomorphic addition operation on two CipherText values and
	 * returns the resulting new CipherText.
	 * 
	 * @param publicKey
	 * @param left
	 * @param right
	 * @return
	 */
	CipherText computeCipherAdd(PublicKey publicKey, CipherText left, CipherText right);

	/**
	 * This function performs the homomorphic multiplication on one CipherText value and a plaintext
	 * data chunk and returns the resulting CipherText.
	 * 
	 * @param publicKey
	 * @param left
	 * @param right
	 * @return
	 */
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
