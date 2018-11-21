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
package org.enquery.encryptedquery.utils;

import java.math.BigInteger;

public class MontgomeryReduction {
	private final BigInteger N;
	private final BigInteger Rmask;
	private final int Rlen;
	private final BigInteger Nprime;
	private final BigInteger montOne;

	public MontgomeryReduction(BigInteger N) {
		this.N = N;
		this.Rlen = N.bitLength();
		BigInteger R = BigInteger.ZERO.setBit(this.Rlen);
		this.Rmask = R.subtract(BigInteger.ONE);
		this.Nprime = N.negate().modInverse(R);
		this.montOne = toMontgomery(BigInteger.ONE);
	}

	private BigInteger REDC(BigInteger x) {
		// assume: 0 <= x and x < N
		BigInteger m = x.and(this.Rmask).multiply(Nprime).and(this.Rmask);
		BigInteger t = x.add(m.multiply(N)).shiftRight(Rlen);
		if (t.compareTo(N) >= 0) {
			return t.subtract(N);
		}
		return t;
	}

	public BigInteger toMontgomery(BigInteger x) {
		if (x.compareTo(BigInteger.ZERO) < 0 || x.compareTo(N) >= 0) {
			throw new IllegalArgumentException("x = " + x + "is not in range");
		}
		return x.shiftLeft(Rlen).mod(N);
	}

	public BigInteger fromMontgomery(BigInteger xm) {
		return REDC(xm);
	}

	public BigInteger montMultiply(BigInteger xm, BigInteger ym) {
		// assume: 0 <= xm < N and 0 <= ym < N
		return REDC(xm.multiply(ym));
	}

	// slow -- for testing only
	public BigInteger montExp(BigInteger xm, BigInteger e) {
		BigInteger prod = montOne;
		int elen = e.bitLength();
		for (int i = 0; i < elen; i++) {
			if (e.testBit(i)) {
				prod = montMultiply(prod, xm);
			}
			if (i < elen - 1) {
				xm = montMultiply(xm, xm);
			}
		}
		return prod;
	}

	public BigInteger getMontOne() {
		return montOne;
	}
}
