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
package org.enquery.encryptedquery.encryption.seal;

import java.security.PublicKey;
import java.util.Arrays;

import org.apache.commons.lang3.Validate;

public class SEALPublicKey implements PublicKey {

	private static final long serialVersionUID = -645743811999304284L;
	public static final String MEDIA_TYPE = "application/vnd.encryptedquery.seal.PublicKey+json; version=1";
	private byte[] pk;
	private byte[] rk;

	/*****************************************
	 * \ Future implementation advise: I would recommend adding the relinearization keys into the
	 * public key as a separate byte array if you use them as it will avoid modifying the data
	 * structures passed from the querier to the responder.
	 * 
	 * \
	 *****************************************/
	public SEALPublicKey(byte[] both) {
		// first grabs the number of bytes in a pk and puts it in the pk
		int pkbytes = 2 * (4 * (8 * 8192)); // num of polys, num of primes, bytes per prime, coeffs per poly
		this.pk = Arrays.copyOfRange(both, 0, pkbytes);
		this.rk = Arrays.copyOfRange(both, pkbytes, both.length);
	}

	public SEALPublicKey(byte[] pk, byte[] rk) {
		Validate.notNull(pk);
		Validate.notNull(rk);
		this.pk = Arrays.copyOf(pk, pk.length);
		this.rk = Arrays.copyOf(rk, rk.length);
	}

	public byte[] getpk() {
		return Arrays.copyOf(pk, pk.length);
	}

	public byte[] getrk() {
		return Arrays.copyOf(rk, rk.length);
	}

	@Override
	public String getAlgorithm() {
		return SEALCryptoScheme.ALGORITHM;
	}

	@Override
	public String getFormat() {
		return MEDIA_TYPE;
	}

	@Override
	public byte[] getEncoded() {
		byte[] both = Arrays.copyOf(pk, pk.length + rk.length);
		System.arraycopy(rk, 0, both, pk.length, rk.length);
		return both;
	}
}
