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
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.ColumnProcessor;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.utils.MontgomeryReduction;

public class DeRooijColumnProcessor implements ColumnProcessor {

	private static class ExponentAndBase {
		final private Long exponent;
		final private BigInteger base;

		public ExponentAndBase(Long exponent, BigInteger base) {
			this.exponent = exponent;
			this.base = base;
		}

		public Long exponent() {
			return exponent;
		}

		public BigInteger base() {
			return base;
		}

		@Override
		public String toString() {
			return "exponent=" + exponent() + ", base=" + base();
		}
	}

	private Map<Integer, CipherText> queryElements;
	private MontgomeryReduction mont;
	private final PriorityQueue<ExponentAndBase> maxHeap;
	private final ModPowAbstraction modPowAbstraction;
	private final boolean useMontgomery;
	private final PaillierPublicKey publicKey;

	private static Comparator<ExponentAndBase> pairComparator = new Comparator<ExponentAndBase>() {
		@Override
		public int compare(ExponentAndBase pair1, ExponentAndBase pair2) {
			return Long.compare(pair2.exponent(), pair1.exponent());
		}
	};

	public DeRooijColumnProcessor(//
			PaillierPublicKey publicKey,
			Map<Integer, CipherText> queryElements,
			ModPowAbstraction modPowAbstraction,
			boolean useMontgomery) {

		Validate.notNull(queryElements);
		Validate.notNull(publicKey);
		Validate.notNull(modPowAbstraction);

		this.maxHeap = new PriorityQueue<>(1, pairComparator);
		this.modPowAbstraction = modPowAbstraction;
		this.queryElements = queryElements;
		this.publicKey = publicKey;
		this.useMontgomery = useMontgomery;
		if (useMontgomery) {
			mont = new MontgomeryReduction(publicKey.getNSquared());
		} else {
			mont = null;
		}
	}

	public void dumpAndClearHeap() {
		System.out.println("dumpAndClearHeap()");
		while (!maxHeap.isEmpty()) {
			ExponentAndBase exponentAndBase = maxHeap.poll();
			System.out.println("pair: " + exponentAndBase);
		}
		System.out.println("done");
	}

	public void dump() {
		System.out.println("dump()");
		ExponentAndBase[] pairs = maxHeap.toArray(new ExponentAndBase[0]);
		Arrays.sort(pairs, pairComparator);
		for (ExponentAndBase exponentAndBase : pairs) {
			System.out.println("pair: " + exponentAndBase);
		}
		System.out.println("done");
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#insert(int, byte[])
	 */
	@Override
	public void insert(int rowIndex, byte[] input) {
		// TODO: review data to big int conversion
		BigInteger dataAsBigInt = new BigInteger(1, input);
		long partLong = dataAsBigInt.longValue();
		if (0 != partLong) {
			BigInteger element = ((PaillierCipherText) queryElements.get(rowIndex)).getValue();
			ExponentAndBase exponentAndBase = new ExponentAndBase(partLong, element);
			maxHeap.add(exponentAndBase);
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
	public CipherText computeAndClear() {
		if (maxHeap.isEmpty()) {
			BigInteger one;
			if (useMontgomery) {
				one = mont.getMontOne();
			} else {
				one = BigInteger.ONE;
			}
			return new PaillierCipherText(one);
		}

		Long a;
		BigInteger g;
		ExponentAndBase exponentAndBase;
		while (maxHeap.size() > 1) {
			Long b;
			BigInteger h;
			exponentAndBase = maxHeap.poll();
			a = exponentAndBase.exponent();
			g = exponentAndBase.base();
			exponentAndBase = maxHeap.poll();
			b = exponentAndBase.exponent();
			h = exponentAndBase.base();
			long q = Long.divideUnsigned(a.longValue(), b.longValue());
			long r = Long.remainderUnsigned(a.longValue(), b.longValue());
			if (useMontgomery) {
				BigInteger power = mont.montExp(g, BigInteger.valueOf(q));
				h = mont.montMultiply(h, power);
			} else {
				BigInteger power = modPowAbstraction.modPow(g, BigInteger.valueOf(q), publicKey.getNSquared());
				h = h.multiply(power).mod(publicKey.getNSquared());
			}
			maxHeap.add(new ExponentAndBase(b, h));
			if (r != 0) {
				maxHeap.add(new ExponentAndBase(r, g));
			}
		}

		Validate.isTrue(maxHeap.size() == 1, "maxHeap.size() must be 1");

		exponentAndBase = maxHeap.poll();
		a = exponentAndBase.exponent();
		g = exponentAndBase.base();

		BigInteger answer;
		if (useMontgomery) {
			answer = mont.montExp(g, BigInteger.valueOf(a));
		} else {
			answer = modPowAbstraction.modPow(g, BigInteger.valueOf(a), publicKey.getNSquared());
		}
		clear();
		return new PaillierCipherText(answer);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.ColumnProcessor#clear()
	 */
	@Override
	public void clear() {
		maxHeap.clear();
	}
}
