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
import java.security.PrivateKey;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.encryption.ChineseRemainder;
import org.enquery.encryptedquery.json.JSONStringConverter;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 */
public class PaillierPrivateKey implements PrivateKey {

	private static final long serialVersionUID = -3444611296943031049L;
	public static final String MEDIA_TYPE = "application/vnd.encryptedquery.paillier.PrivateKey+json; version=1";
	private static final String ALGORITHM = "Paillier";

	private final BigInteger p,
			q,
			pBasePoint,
			qBasePoint,
			pMaxExponent,
			qMaxExponent;
	private final int modulusBitSize;

	private BigInteger pSquared; // pSquared = p^2
	private BigInteger qSquared; // qSquared = q^2
	private ChineseRemainder crtNSquared; // CRT for moduli p^2 and q^2
	private BigInteger pMinusOne;
	private BigInteger qMinusOne;
	private BigInteger wp;
	private BigInteger wq;
	private ChineseRemainder crtN;

	/**
	 * 
	 */
	public PaillierPrivateKey(//
			BigInteger p,
			BigInteger q,
			BigInteger pBasePoint,
			BigInteger qBasePoint,
			BigInteger pMaxExponent,
			BigInteger qMaxExponent,
			int modulusBitSize)//
	{
		Validate.notNull(p, "P can't be null");
		Validate.notNull(q, "Q can't be null");
		Validate.notNull(pBasePoint, "pBasePoint can't be null");
		Validate.notNull(qBasePoint, "qBasePoint can't be null");
		Validate.notNull(pMaxExponent, "pMaxExponent can't be null");
		Validate.notNull(qMaxExponent, "qMaxExponent can't be null");

		this.p = p;
		this.q = q;
		this.pBasePoint = pBasePoint;
		this.qBasePoint = qBasePoint;
		this.pMaxExponent = pMaxExponent;
		this.qMaxExponent = qMaxExponent;
		this.modulusBitSize = modulusBitSize;

		calcDerivedValues();
	}

	public PaillierPrivateKey(byte[] encodedForm) {
		String json = new String(encodedForm, Charsets.UTF_8);
		Map<String, String> map = JSONStringConverter.toMap(json);

		Validate.isTrue(MEDIA_TYPE.equals(map.get("format")));
		Validate.isTrue(ALGORITHM.equals(map.get("algorithm")));

		this.p = new BigInteger(map.get("p"));
		this.q = new BigInteger(map.get("q"));
		this.pBasePoint = new BigInteger(map.get("pbasePoint"));
		this.qBasePoint = new BigInteger(map.get("qbasePoint"));
		this.pMaxExponent = new BigInteger(map.get("pmaxExponent"));
		this.qMaxExponent = new BigInteger(map.get("qmaxExponent"));
		this.modulusBitSize = Integer.valueOf(map.get("modulusBitSize"));

		calcDerivedValues();
	}

	public static PaillierPrivateKey from(PrivateKey privateKey) {
		Validate.notNull(privateKey);
		Validate.isInstanceOf(PaillierPrivateKey.class, privateKey);
		return (PaillierPrivateKey) privateKey;
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

	public BigInteger getP() {
		return p;
	}

	public BigInteger getQ() {
		return q;
	}

	public BigInteger getPBasePoint() {
		return pBasePoint;
	}

	public BigInteger getQBasePoint() {
		return qBasePoint;
	}

	public BigInteger getPMaxExponent() {
		return pMaxExponent;
	}

	public BigInteger getQMaxExponent() {
		return qMaxExponent;
	}

	public int getModulusBitSize() {
		return modulusBitSize;
	}

	@JsonIgnore
	public BigInteger getPSquared() {
		return pSquared;
	}

	@JsonIgnore
	public BigInteger getQSquared() {
		return qSquared;
	}

	@JsonIgnore
	public ChineseRemainder getCrtNSquared() {
		return crtNSquared;
	}

	@JsonIgnore
	public BigInteger getPMinusOne() {
		return pMinusOne;
	}

	@JsonIgnore
	public BigInteger getQMinusOne() {
		return qMinusOne;
	}

	@JsonIgnore
	public BigInteger getWp() {
		return wp;
	}

	@JsonIgnore
	public BigInteger getWq() {
		return wq;
	}

	@JsonIgnore
	public ChineseRemainder getCrtN() {
		return crtN;
	}

	private void calcDerivedValues() {
		pSquared = p.multiply(p);
		qSquared = q.multiply(q);
		crtNSquared = new ChineseRemainder(getPSquared(), getQSquared());
		crtN = new ChineseRemainder(p, q);
		pMinusOne = p.subtract(BigInteger.ONE);
		qMinusOne = q.subtract(BigInteger.ONE);
		wp = p.subtract(q).modInverse(p);
		wq = q.subtract(p).modInverse(q);
	}
}
