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
import java.security.PublicKey;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.json.JSONStringConverter;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 */
public class PaillierPublicKey implements PublicKey {

	private static final long serialVersionUID = 2174466128340735447L;
	public static final String MEDIA_TYPE = "application/vnd.encryptedquery.paillier.PublicKey+json; version=1";
	private static final String ALGORITHM = "Paillier";

	private final BigInteger n;
	private final int modulusBitSize;
	private BigInteger nSquared;

	/**
	 * 
	 */
	public PaillierPublicKey(BigInteger n, int modulusBitSize) {
		Validate.notNull(n);
		this.n = n;
		this.modulusBitSize = modulusBitSize;
		calcDerivedValues();
	}

	public PaillierPublicKey(byte[] encodedForm) {
		String json = new String(encodedForm, Charsets.UTF_8);
		Map<String, String> map = JSONStringConverter.toMap(json);

		Validate.isTrue(MEDIA_TYPE.equals(map.get("format")));
		Validate.isTrue(ALGORITHM.equals(map.get("algorithm")));

		this.n = new BigInteger(map.get("n"));
		this.modulusBitSize = Integer.parseInt(map.get("modulusBitSize"));

		calcDerivedValues();
	}

	public static PaillierPublicKey from(PublicKey publicKey) {
		Validate.notNull(publicKey);
		Validate.isInstanceOf(PaillierPublicKey.class, publicKey);
		return (PaillierPublicKey) publicKey;
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

	public BigInteger getN() {
		return n;
	}

	public int getModulusBitSize() {
		return modulusBitSize;
	}

	@JsonIgnore
	public BigInteger getNSquared() {
		return nSquared;
	}

	private void calcDerivedValues() {
		nSquared = getN().multiply(getN());
	}
}
