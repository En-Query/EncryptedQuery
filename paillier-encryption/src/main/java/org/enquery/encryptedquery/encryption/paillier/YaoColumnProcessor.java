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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.utils.MontgomeryReduction;

public class YaoColumnProcessor implements ColumnProcessor {
	private final Map<Integer, CipherText> queryElements;
	private final boolean useMontgomery;
	private final MontgomeryReduction mont;
	private final PaillierPublicKey publicKey;
	// private final ModPowAbstraction modPowAbstraction;
	private final int b;
	private final BigInteger[] hs;

	/**
	 * 
	 */
	public YaoColumnProcessor(//
			PaillierPublicKey publicKey,
			Map<Integer, CipherText> queryElements,
			// ModPowAbstraction modPowAbstraction,
			boolean useMontgomery,
			int dataChunkSize) {

		Validate.notNull(queryElements);
		Validate.notNull(publicKey);
		// Validate.notNull(modPowAbstraction);

		// this.modPowAbstraction = modPowAbstraction;
		this.queryElements = queryElements;
		this.publicKey = publicKey;
		this.useMontgomery = useMontgomery;
		if (useMontgomery) {
			mont = new MontgomeryReduction(publicKey.getNSquared());
		} else {
			mont = null;
		}

		if (dataChunkSize <= 0) {
			throw new IllegalArgumentException("Yao responder method requires dataChunkSize > 0, " + dataChunkSize + " given");
		}
		if (dataChunkSize > 2) {
			throw new IllegalArgumentException("Yao responder method currently requires dataChunkSize <= 2 to limit memory usage, " + dataChunkSize + " given");
		}
		this.b = 8 * dataChunkSize;
		this.hs = new BigInteger[1 << b];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#insert(int, byte[])
	 */
	@Override
	public void insert(int rowIndex, byte[] input) {
		// TODO: review data to big int conversion
		BigInteger part = new BigInteger(1, input);

		// NOTE: assuming part < 2**b
		// TODO: handle Montgomery case
		int partInt = part.intValue();
		if (0 != partInt) {
			BigInteger queryElement = ((PaillierCipherText) queryElements.get(rowIndex)).getValue();

			if (hs[partInt] != null) {
				if (useMontgomery) {
					hs[partInt] = mont.montMultiply(hs[partInt], queryElement);
				} else {
					hs[partInt] = hs[partInt].multiply(queryElement).mod(publicKey.getNSquared());
				}
			} else {
				hs[partInt] = queryElement;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#insert(int, byte[], int, int)
	 */
	@Override
	public void insert(int rowIndex, byte[] input, int inputOffset, int inputLen) {
		ByteBuffer bb = ByteBuffer.wrap(input, inputOffset, inputLen);
		byte[] b = new byte[bb.remaining()];
		bb.get(b);
		insert(rowIndex, b);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#compute()
	 */
	@Override
	public CipherText compute() {
		if (useMontgomery) {
			return computeColumnAndClearDataMontgomery();
		}

		BigInteger out = BigInteger.ONE;
		BigInteger a = BigInteger.ONE;
		for (int x = (1 << b) - 1; x > 0; x--) {
			if (hs[x] != null) {
				a = a.multiply(hs[x]).mod(publicKey.getNSquared());
			}
			out = out.multiply(a).mod(publicKey.getNSquared());
		}
		clear();
		return new PaillierCipherText(out);
	}

	private CipherText computeColumnAndClearDataMontgomery() {
		// TODO: handle Montgomery case:
		BigInteger out = mont.getMontOne();
		BigInteger a = mont.getMontOne();
		for (int x = (1 << b) - 1; x > 0; x--) {
			if (hs[x] != null) {
				a = mont.montMultiply(a, hs[x]);
			}
			out = mont.montMultiply(out, a);
		}
		clear();
		return new PaillierCipherText(out);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#clear()
	 */
	@Override
	public void clear() {
		Arrays.fill(hs, null);
	}

}
