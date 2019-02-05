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

import java.security.PrivateKey;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.json.JSONStringConverter;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 */
public class NullCipherPrivateKey implements PrivateKey {
	private static final long serialVersionUID = -2855341065406884447L;
	public static final String MEDIA_TYPE = "application/vnd.encryptedquery.nullcipher.PrivateKey+json; version=1";
	private static final String ALGORITHM = "Null Cipher";  // XXX should this have a space?
 
	private final int plainTextByteSize;
	private final int paddingByteSize;

	/**
	 * 
	 */
	public NullCipherPrivateKey(//
			int plainTextByteSize,
			int paddingByteSize)//
	{
		Validate.isTrue(plainTextByteSize >= 1);
		Validate.isTrue(paddingByteSize >= 0);
		this.plainTextByteSize = plainTextByteSize;
		this.paddingByteSize = paddingByteSize;
	}

	public NullCipherPrivateKey(byte[] encodedForm) {
		String json = new String(encodedForm, Charsets.UTF_8);
		Map<String, String> map = JSONStringConverter.toMap(json);

		Validate.isTrue(MEDIA_TYPE.equals(map.get("format")));
		Validate.isTrue(ALGORITHM.equals(map.get("algorithm")));

		this.plainTextByteSize = Integer.parseInt(map.get("plainTextByteSize"));  // XXX should I use a constant for this?
		this.paddingByteSize = Integer.parseInt(map.get("paddingByteSize"));  // XXX should I use a constant for this?
	}

	public static NullCipherPrivateKey from(PrivateKey privateKey) {
		Validate.notNull(privateKey);
		Validate.isInstanceOf(NullCipherPrivateKey.class, privateKey);
		return (NullCipherPrivateKey) privateKey;
	}

	@JsonIgnore
	@Override
	public boolean isDestroyed() {
		return PrivateKey.super.isDestroyed();
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
}
