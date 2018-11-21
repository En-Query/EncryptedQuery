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

import java.math.BigInteger;

/**
 * This class is designed to offer a one-stop-shop for invoking the desired version of modPow
 */
public interface ModPowAbstraction {

	/**
	 * Performs modPow: ({@code base}^{@code exponent}) mod {@code modulus}
	 * 
	 * This method uses the values of {@code paillier.useGMPForModPow} and
	 * {@code paillier.GMPConstantTimeMode} as they were when the class was loaded to decide which
	 * implementation of modPow to invoke.
	 * 
	 * These values can be reloaded by invoking static method
	 * {@code ModPowAbstraction.reloadConfiguration()}
	 * 
	 * @return The result of modPow
	 */
	BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus);

	default BigInteger modPow(long base, BigInteger exponent, BigInteger modulus) {
		return modPow(BigInteger.valueOf(base), exponent, modulus);
	}
}
