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
import org.enquery.encryptedquery.encryption.PlainText;

/**
 *
 */
public class PaillierPlainText implements PlainText {

	private static final long serialVersionUID = -7689336792900305706L;
	final private byte[] value;

	public PaillierPlainText(BigInteger value) {
		Validate.notNull(value);
		this.value = value.toByteArray();
	}

	public PaillierPlainText(byte[] value) {
		Validate.notNull(value);
		this.value = value;
	}

	public static PaillierPlainText from(PlainText plainText) {
		Validate.notNull(plainText);
		Validate.isTrue(plainText instanceof PaillierPlainText);
		return ((PaillierPlainText) plainText);
	}

	public byte[] getValue() {
		return value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.PlainText#toBytes()
	 */
	@Override
	public byte[] toBytes() {
		return value;
	}

}
