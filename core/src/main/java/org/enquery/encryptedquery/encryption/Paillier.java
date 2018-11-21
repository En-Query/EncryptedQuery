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

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;


/**
 * Paillier data holder. To be used for serialization. The actual algortithm is implemented in class
 * PaillierEncryption in this same package.
 * 
 */

public final class Paillier implements Serializable {

	private static final long serialVersionUID = -1450948558450674802L;
	private BigInteger p; // large prime
	private BigInteger q; // large prime
	private int bitLength; // bit length of the modulus N
	private BigInteger pBasePoint; // mod p^2 base point for fast query generation
	private BigInteger pMaxExponent; // one plus max exponent for mod p^2 base point
	private BigInteger qBasePoint; // mod q^2 base point for fast query generation
	private BigInteger qMaxExponent; // one plus max exponent for mod q^2 base point
	private BigInteger N; // N=pq, RSA modulus
	private BigInteger nSquared; // NSquared = N^2
	private BigInteger pSquared; // pSquared = p^2
	private BigInteger qSquared; // qSquared = q^2
	private ChineseRemainder crtN; // CRT for moduli p and q
	private ChineseRemainder crtNSquared; // CRT for moduli p^2 and q^2
	private BigInteger pMinusOne; // p-1
	private BigInteger qMinusOne; // q-1
	private BigInteger wp; // ((p-1)*q)^-1 mod p
	private BigInteger wq; // ((q-1)*p)^-1 mod q

	/**
	 * Returns the value of the large prime {@code p}.
	 *
	 * @return p.
	 */
	public BigInteger getP() {
		return p;
	}

	public ChineseRemainder getCrtNSquared() {
		return crtNSquared;
	}

	public void setCrtNSquared(ChineseRemainder crtNSquared) {
		this.crtNSquared = crtNSquared;
	}

	public ChineseRemainder getCrtN() {
		return crtN;
	}

	public void setCrtN(ChineseRemainder crtN) {
		this.crtN = crtN;
	}

	public BigInteger getWp() {
		return wp;
	}

	public void setWp(BigInteger wp) {
		this.wp = wp;
	}

	public BigInteger getWq() {
		return wq;
	}

	public void setWq(BigInteger wq) {
		this.wq = wq;
	}

	public void setP(BigInteger p) {
		this.p = p;
	}

	public BigInteger getPSquared() {
		return pSquared;
	}

	public void setPSquared(BigInteger pSquared) {
		this.pSquared = pSquared;
	}

	public BigInteger getpMinusOne() {
		return pMinusOne;
	}

	public void setPMinusOne(BigInteger pMinusOne) {
		this.pMinusOne = pMinusOne;
	}

	public BigInteger getQMinusOne() {
		return qMinusOne;
	}

	public void setQMinusOne(BigInteger qMinusOne) {
		this.qMinusOne = qMinusOne;
	}

	/**
	 * Returns the value of the large prime {@code q}.
	 *
	 * @return q.
	 */
	public BigInteger getQ() {
		return q;
	}

	public void setQ(BigInteger q) {
		this.q = q;
	}

	public BigInteger getQSquared() {
		return qSquared;
	}

	public void setQSquared(BigInteger qSquared) {
		this.qSquared = qSquared;
	}

	/**
	 * Returns the RSA modulus value {@code N}.
	 *
	 * @return N, the product of {@code p} and {@code q}.
	 */
	public BigInteger getN() {
		return N;
	}

	public void setN(BigInteger N) {
		this.N = N;
		this.nSquared = N.multiply(N);
	}

	/**
	 * Returns the generator for the mod {@code p}<sup>2</sup> subgroup used for fast query
	 * generation.
	 *
	 * @return the generator for the mod {@code p}<sup>2</sup> subgroup.
	 */
	public BigInteger getPBasePoint() {
		return pBasePoint;
	}

	public void setPBasePoint(BigInteger pBasePoint) {
		this.pBasePoint = pBasePoint;
	}

	/**
	 * Returns one plus the maximum exponent used for the mod {@code p}<sup>2</sup> base point used
	 * for fast query generation.
	 *
	 * @return one plus the maximum exponent used for the mod {@code p}<sup>2</sup> base point.
	 */
	public BigInteger getPMaxExponent() {
		return pMaxExponent;
	}

	public void setPMaxExponent(BigInteger pMaxExponent) {
		this.pMaxExponent = pMaxExponent;
	}

	/**
	 * Returns the generator for the mod {@code q}<sup>2</sup> subgroup used for fast query
	 * generation.
	 *
	 * @return the generator for the mod {@code q}<sup>2</sup> subgroup.
	 */
	public BigInteger getQBasePoint() {
		return qBasePoint;
	}

	public void setQBasePoint(BigInteger qBasePoint) {
		this.qBasePoint = qBasePoint;
	}

	/**
	 * Returns one plus the maximum exponent used for the mod {@code q}<sup>2</sup> base point used
	 * for fast query generation.
	 *
	 * @return one plus the maximum exponent used for the mod {@code q}<sup>2</sup> base point.
	 */
	public BigInteger getQMaxExponent() {
		return qMaxExponent;
	}

	public void setQMaxExponent(BigInteger qMaxExponent) {
		this.qMaxExponent = qMaxExponent;
	}

	/**
	 * Returns the value of {@code N}<sup>2</sup>.
	 *
	 * @return N squared.
	 */
	public BigInteger getNSquared() {
		return nSquared;
	}

	public void setNSquared(BigInteger nSquared) {
		this.nSquared = nSquared;
	}

	/**
	 * Returns the bit length of the modulus {@code N}.
	 *
	 * @return the bit length, as an integer.
	 */
	public int getBitLength() {
		return bitLength;
	}

	/**
	 * @param bitLength the bitLength to set
	 */
	public void setBitLength(int bitLength) {
		this.bitLength = bitLength;
	}

	@Override
	public int hashCode() {
		return Objects.hash(p, q, N, nSquared, bitLength);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Paillier [p=").append(p).append(", pSquared=").append(pSquared).append(", q=").append(q).append(", qSquared=").append(qSquared).append(", bitLength=").append(bitLength)
				.append(", pBasePoint=").append(pBasePoint).append(", pMaxExponent=").append(pMaxExponent).append(", qBasePoint=").append(qBasePoint)
				.append(", qMaxExponent=").append(qMaxExponent).append(", N=").append(N).append(", NSquared=").append(nSquared).append("]");
		return builder.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		Paillier paillier = (Paillier) o;

		if (bitLength != paillier.bitLength)
			return false;
		if (!p.equals(paillier.p))
			return false;
		if (!q.equals(paillier.q))
			return false;
		if (!N.equals(paillier.N))
			return false;
		if (!nSquared.equals(paillier.nSquared))
			return false;
		return true;

	}
}
