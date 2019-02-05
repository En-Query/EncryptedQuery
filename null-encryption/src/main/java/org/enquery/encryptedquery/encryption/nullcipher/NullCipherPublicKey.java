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
import java.security.PublicKey;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.json.JSONStringConverter;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 */
public class NullCipherPublicKey implements PublicKey {
	private static final long serialVersionUID = 9133602345987784028L;
	public static final String MEDIA_TYPE = "application/vnd.encryptedquery.nullcipher.PublicKey+json; version=1";
	private static final String ALGORITHM = "Null Cipher";  // XXX should this have a space?

	/*
	 * (non-Javadoc)
	 * 
	 * The following are crypto parameters only -- there is no key-specific data.
	 */
	private final int plainTextByteSize;
	private final int paddingByteSize;
	private final BigInteger mask;

	public NullCipherPublicKey(int plainTextByteSize, int paddingByteSize) {
		Validate.isTrue(plainTextByteSize >= 1);
		Validate.isTrue(paddingByteSize >= 0);
		this.plainTextByteSize = plainTextByteSize;
		this.paddingByteSize = paddingByteSize;
		mask = computeMask(plainTextByteSize, paddingByteSize);
	}

	public NullCipherPublicKey(byte[] encodedForm) {
		String json = new String(encodedForm, Charsets.UTF_8);
		Map<String, String> map = JSONStringConverter.toMap(json);

		Validate.isTrue(MEDIA_TYPE.equals(map.get("format")));
		Validate.isTrue(ALGORITHM.equals(map.get("algorithm")));

		this.plainTextByteSize = Integer.parseInt(map.get("plainTextByteSize"));  // XXX should I use a constant for this?
		this.paddingByteSize = Integer.parseInt(map.get("paddingByteSize"));  // XXX should I use a constant for this?
		mask = computeMask(plainTextByteSize, paddingByteSize);
	}

	public static NullCipherPublicKey from(PublicKey publicKey) {
		Validate.notNull(publicKey);
		Validate.isInstanceOf(NullCipherPublicKey.class, publicKey);
		return (NullCipherPublicKey) publicKey;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.security.Key#getAlgorithm()
	 */
	@Override
	public String getAlgorithm() {
		return ALGORITHM;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.security.Key#getFormat()
	 */
	@Override
	public String getFormat() {
		return MEDIA_TYPE;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.security.Key#getEncoded()
	 */
	@Override
	@JsonIgnore
	public byte[] getEncoded() {
		return JSONStringConverter.toString(this).getBytes(Charsets.UTF_8);
	}

	public int getPlainTextByteSize() {
		return plainTextByteSize;
	}

	public int getPaddingByteSize() {
		return paddingByteSize;
	}
	
	@JsonIgnore
	public BigInteger getMask() {
		return mask;
	}
	
	private BigInteger computeMask(int plainTextByteSize, int paddingByteSize) {
		BigInteger mask = BigInteger.ZERO.setBit(8*(plainTextByteSize + paddingByteSize)).subtract(BigInteger.ONE);
		return mask;
	}
	
}
