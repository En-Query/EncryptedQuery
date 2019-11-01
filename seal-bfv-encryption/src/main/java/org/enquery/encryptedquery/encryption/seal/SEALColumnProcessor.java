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

import java.util.Arrays;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;

public class SEALColumnProcessor implements ColumnProcessor {

	private long contextptr;
	private long queryHandle;
	private long accumptr;
	private long numsel;
	private long nttptr;
	private long invnttptr;
	private long relinptr;
	private long subqueryptr;
	private int bitlen;
	private int sublen;
	private SEALPublicKey publicKey;

	public SEALColumnProcessor(long contextptr, QueryInfo queryInfo, long queryHandle, long subQueryHandle) {
		Validate.isTrue(contextptr != 0L);
		Validate.notNull(queryInfo);
		Validate.isTrue(subQueryHandle != 0L);

		/****************************************
		 * \ Future implementation advice: If you are using multiple lists of selectors, you need to
		 * change this code. \
		 ****************************************/
		this.contextptr = contextptr;

		// within the SEALColumnProcessor object thus instantiated,
		// secretly round the hash bit size up to its nearest
		// multiple of 4
		int roundedHashBitSize = queryInfo.getHashBitSize();
		if ((roundedHashBitSize % 4) != 0) {
			roundedHashBitSize = 4 * ((roundedHashBitSize / 4) + 1);
		}
		this.bitlen = roundedHashBitSize;

		// Caution: In SEALCryptoScheme.java, the attribute
		// sublen instead refers to what here would be
		// 1 << (bitlen / 4)
		this.sublen = 1 << (bitlen / 2);

		// secretly round the number of selectors up to
		// its nearest power of 2
		this.numsel = queryInfo.getNumSelectors();
		int lognumsel = 0;
		while ((1 << lognumsel) < numsel) {
			lognumsel++;
		}
		numsel = 1 << lognumsel;

		this.queryHandle = queryHandle;
		this.nttptr = SEALCpp.createntt(contextptr, numsel);
		Validate.isTrue(this.nttptr != 0L);

		this.invnttptr = SEALCpp.createinvntt(contextptr, numsel);
		Validate.isTrue(this.invnttptr != 0L);

		this.publicKey = (SEALPublicKey) queryInfo.getPublicKey();
		this.relinptr = SEALCpp.createrk(publicKey.getrk(), contextptr);
		Validate.isTrue(this.relinptr != 0L);

		long evalptr = SEALCpp.createevaluator(contextptr);
		Validate.isTrue(evalptr != 0L);
		try {
			this.accumptr = SEALCpp.createaccum(contextptr, bitlen);
			Validate.isTrue(this.accumptr != 0L);

			this.subqueryptr = subQueryHandle;
		} finally {
			SEALCpp.destroyevaluator(evalptr);
		}
	}


	// insert bytes into row i
	@Override
	public void insert(int i, byte[] bytes) {
		Validate.notNull(bytes);
		// This code packs our database values into
		// plaintexts in a way that avoids values above the
		// plaintext modulus. If you change the plaintext modulus
		// you need to:
		// 1: change this
		// or
		// 2: create a more general version of this that allows
		// for arbitrary plaintext moduli

		// copy the data chunk bytes into pbytes[] extended (by zero bytes, as necessary)
		byte[] pbytes = Arrays.copyOf(bytes, (int) (4 * (8192 / numsel)));

		// 0 <= i < bitlen = sublen^2,
		// so i = (i % sublen) + (i / sublen) * sublen
		// to the base-sublen;
		SEALCpp.insert(
			i % sublen, 
			i / sublen, 
			pbytes, 
			contextptr, 
			subqueryptr, 
			accumptr, 
			nttptr,
			invnttptr, 
			numsel
		);
	}

	@Override
	public void insert(int i, byte[] bytes, int i1, int i2) {
		Validate.notNull(bytes);

		// currently this simply calls the other insert methods.
		// for a more complicated system you may want to
		// modify this
		insert(i, Arrays.copyOfRange(bytes, i1, i1 + i2));
	}

	// Recall: the parameter bitlen = roundedHashBitSize
	@Override
	public CipherText computeAndClear() {

		byte[] raw = SEALCpp.getaccum(
				contextptr, 
				publicKey.getpk(), 
				accumptr, 
				relinptr,
				queryHandle, 
				bitlen
			     );
		SEALCipherText cipher = new SEALCipherText(raw);
		clear();
		return cipher;
	}

	@Override
	public void clear() {
		SEALCpp.resetaccum(contextptr, accumptr, bitlen);
	}

	@Override
	protected void finalize() throws Throwable {
		if (accumptr != 0L) {
			SEALCpp.destroyaccum(accumptr, bitlen);
			accumptr = 0L;
		}
		if (nttptr != 0L) {
			SEALCpp.destroyntt(nttptr);
			nttptr = 0L;
		}
		if (relinptr != 0L) {
			SEALCpp.destroyrk(relinptr);
			relinptr = 0L;
		}
		super.finalize();
	}
}
