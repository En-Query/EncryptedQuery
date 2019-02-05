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

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.encryption.CipherText;

/**
 *
 */
public class PaillierCipherText implements CipherText {

	private static final long serialVersionUID = 5101017701311688090L;
	private final BigInteger value;

	public PaillierCipherText(BigInteger value) {
		this.value = value;
	}

	public PaillierCipherText(byte[] encodedForm) {
		this.value = new BigInteger(encodedForm);
	}

	public static PaillierCipherText from(CipherText cipherText) {
		Validate.notNull(cipherText);
		Validate.isTrue(cipherText instanceof PaillierCipherText);
		return ((PaillierCipherText) cipherText);
	}

	public BigInteger getValue() {
		return value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CipherText#toBytes()
	 */
	@Override
	public byte[] toBytes() {
		return this.value.toByteArray();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("PaillierCipherText [value=").append(value).append("]");
		return builder.toString();
	}

}
