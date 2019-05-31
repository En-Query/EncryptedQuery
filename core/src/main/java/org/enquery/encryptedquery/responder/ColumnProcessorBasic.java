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
package org.enquery.encryptedquery.responder;

import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.CryptoScheme;

public class ColumnProcessorBasic implements ColumnProcessor {
	private final Map<Integer, CipherText> queryElements;
	private final PublicKey publicKey;
	private final CryptoScheme crypto;
	private CipherText sum;

	/**
	 * 
	 */
	public ColumnProcessorBasic(
			PublicKey publicKey,
			Map<Integer, CipherText> queryElements,
			CryptoScheme crypto) {

		Validate.notNull(publicKey);
		Validate.notNull(queryElements);
		Validate.notNull(crypto);

		this.publicKey = publicKey;
		this.queryElements = queryElements;
		this.crypto = crypto;
		this.sum = crypto.encryptionOfZero(publicKey);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#insert(int, byte[])
	 */
	@Override
	public void insert(int rowIndex, byte[] input) {
		CipherText queryElement = queryElements.get(rowIndex);
		CipherText encryptedPart = crypto.computeCipherPlainMultiply(publicKey, queryElement, input);
		// CipherText encryptedPart = queryElement.computeCipherPlainMultiply(publicKey, input);
		sum = crypto.computeCipherAdd(publicKey, sum, encryptedPart);
		// sum = sum.computeCipherAdd(publicKey, encryptedPart);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#insert(int, byte[], int, int)
	 */
	@Override
	public void insert(int rowIndex, byte[] input, int inputOffset, int inputLen) {
		ByteBuffer bb = ByteBuffer.wrap(input, inputOffset, inputLen);
		byte[] b = new byte[bb.remaining()];
		bb.get(b);
		insert(rowIndex, b);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#compute()
	 */
	@Override
	public CipherText computeAndClear() {
		CipherText result = sum;
		clear();
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#clear()
	 */
	@Override
	public void clear() {
		sum = crypto.encryptionOfZero(publicKey);
	}
}
