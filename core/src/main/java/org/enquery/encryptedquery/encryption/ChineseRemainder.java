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

/**
 * Class to compute the extended GCD of two moduli.
 * <p>
 * This is needed for the speedup for Paillier encryption using the Chinese Remainder Theorem. The
 * algorithm is run when the constructor is called, and the object contains the result.
 * <p>
 * Implements the binary extended GCD algorithm from Section 14.4.3 of Menezes et al., "Handbook of
 * Applied Cryptography".
 */
class ExtendedGCD {
	private BigInteger a, b, v; // ax + by = v, v = GCD(x,y)

	private void computeExtendedGCD(BigInteger x, BigInteger y) {
		BigInteger g = BigInteger.ONE;
		while (!x.testBit(0) && !y.testBit(0)) {
			x = x.shiftRight(1);
			y = y.shiftRight(1);
			g = g.shiftLeft(1);
		}
		BigInteger A = BigInteger.ONE;
		BigInteger B = BigInteger.ZERO;
		BigInteger u = x;
		BigInteger C = BigInteger.ZERO;
		BigInteger D = BigInteger.ONE;
		BigInteger v = y;
		while (true) {
			while (!u.testBit(0)) {
				u = u.shiftRight(1);
				if (!A.testBit(0) && !B.testBit(0)) {
					A = A.shiftRight(1);
					B = B.shiftRight(1);
				} else {
					A = A.add(y);
					A = A.shiftRight(1);
					B = B.subtract(x);
					B = B.shiftRight(1);
				}
			}
			while (!v.testBit(0)) {
				v = v.shiftRight(1);
				if (!C.testBit(0) && !D.testBit(0)) {
					C = C.shiftRight(1);
					D = D.shiftRight(1);
				} else {
					C = C.add(y);
					C = C.shiftRight(1);
					D = D.subtract(x);
					D = D.shiftRight(1);
				}
			}
			if (u.compareTo(v) >= 0) {
				u = u.subtract(v);
				A = A.subtract(C);
				B = B.subtract(D);
			} else {
				v = v.subtract(u);
				C = C.subtract(A);
				D = D.subtract(B);
			}
			if (u.equals(BigInteger.ZERO)) {
				this.a = C;
				this.b = D;
				this.v = v.multiply(g);
				return;
			}
		}
	}

	/**
	 * Creates an {@code ExtendedGCD} object that captures the result of computing the extended GCD
	 * of the given inputs x and y, i.e. values a, b, and v such that ax + by = v and v = GCD(x).
	 *
	 * @param x The first input, x
	 * @param y The second input, y
	 * @throws IllegalArgumentException If {@code x} and {@code y} are not both positive.
	 * 
	 */
	public ExtendedGCD(BigInteger x, BigInteger y) {
		if (x.compareTo(BigInteger.ZERO) <= 0 || y.compareTo(BigInteger.ZERO) <= 0) {
			throw new IllegalArgumentException("x = " + x + " y = " + y + " are not both positive");
		}
		computeExtendedGCD(x, y);
	}

	/**
	 * Returns the first coefficient of the extended GCD.
	 *
	 * @return a, the coefficient of the first input x in the extended GCD.
	 */
	public BigInteger getFirst() {
		return this.a;
	}

	/**
	 * Returns the second coefficient of the extended GCD.
	 *
	 * @return b, the coefficient of the second input y in the extended GCD.
	 */
	public BigInteger getSecond() {
		return this.b;
	}

	/**
	 * Returns the GCD value in the extended GCD.
	 *
	 * @return the GCD of the inputs x and y.
	 */
	public BigInteger getGCD() {
		return this.v;
	}
}


/**
 * Class to solve pairs of congruences modulo two fixed, relatively prime moduli.
 * <p>
 * For any two relatively prime positive integers x and y (the "moduli") and any values 0 <= a < x
 * and 0 <= b < y, the Chinese Remainder Theorem says there is a unique 0 <= c < xy such that c mod
 * x = a and c mod y = b. This class implements an algorithm to find c. For the Paillier
 * cryptosystem, this allows computations mod N or N^2 to be done faster on the private side (where
 * p and q are known) by working modulo p and q (or p^2 and q^2) separately and then combining the
 * results.
 */
public class ChineseRemainder implements Serializable {

	private static final long serialVersionUID = -6162122529224878513L;

	private BigInteger e;
	private BigInteger f;

	/**
	 * Returns a {@code ChineseRemainder} object that, given two moduli x and y, encapsulates two
	 * integers e and f such that e is congruent to 1 and 0 modulo x and y respectively, and f is
	 * congruent to 0 and 1 modulo x and y respectively. The two moduli <b>must</b> be positive and
	 * relatively prime.
	 *
	 * @param x First modulus
	 * @param y Second modulus
	 * @throws IllegalArgumentException If {@code x} and {@code y} are not both positive or
	 *         relatively prime.
	 * 
	 */
	public ChineseRemainder(BigInteger x, BigInteger y) {
		ExtendedGCD egcd = new ExtendedGCD(x, y);
		if (!egcd.getGCD().equals(BigInteger.ONE)) {
			throw new IllegalArgumentException("x = " + x + " y = " + y + " are not relatively prime, GCD = " + egcd.getGCD());
		}
		this.e = egcd.getSecond().multiply(y);
		this.f = egcd.getFirst().multiply(x);
	}

	/**
	 * Returns a number congrent to 1 and 0 modulo the first and second moduli, respectively.
	 *
	 * @return a number congrent to 1 and 0 modulo the first and second moduli, respectively
	 */
	public BigInteger getFirst() {
		return this.e;
	}

	/**
	 * Returns a number congrent to 0 and 1 modulo the first and second moduli, respectively.
	 *
	 * @return a number congrent to 0 and 1 modulo the first and second moduli, respectively
	 */
	public BigInteger getSecond() {
		return this.f;
	}

	/**
	 * Returns a number with the given residues modulo x and y as guaranteed by the Chinese
	 * Remainder Theorem.
	 *
	 * @param res1 the residue modulo x
	 * @param res2 the residue modulo y
	 * @param xy the product of the moduli x and y
	 * @return a number between 0 and xy-1 with the given residues modulo x and y.
	 */
	public BigInteger combine(BigInteger res1, BigInteger res2, BigInteger xy) {
		BigInteger u = res1.multiply(this.e);
		BigInteger v = res2.multiply(this.f);
		return u.add(v).mod(xy);
	}
}
