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
package org.enquery.encryptedquery.responder.wideskies.common;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.utils.MontgomeryReduction;

class Pair {
	final private Long l;
	final private BigInteger b;

	public Pair(Long l, BigInteger b) {
		this.l = l;
		this.b = b;
	}

	public Long _1() {
		return l;
	}

	public BigInteger _2() {
		return b;
	}

	@Override
	public String toString() {
		return "(" + _1() + "," + _2() + ")";
	}
}


public class ComputeEncryptedColumnDeRooij implements ComputeEncryptedColumn {

	// private static final Logger logger =
	// LoggerFactory.getLogger(ComputeEncryptedColumnDeRooij.class);
	private Map<Integer, BigInteger> queryElements;
	private BigInteger NSquared;
	private MontgomeryReduction mont;
	public final PriorityQueue<Pair> maxHeap;
	private ModPowAbstraction modPowAbstraction;
	private boolean useMontgomery = false;

	private static Comparator<Pair> pairComparator = new Comparator<Pair>() {
		@Override
		public int compare(Pair pair1, Pair pair2) {
			return Long.compare(pair2._1(), pair1._1());
		}
	};

	public ComputeEncryptedColumnDeRooij() {
		this.maxHeap = new PriorityQueue<>(1, pairComparator);
	}

	@Override
	public void initialize(Map<Integer, BigInteger> queryElements, BigInteger NSquared, ModPowAbstraction modPowAbstraction, Map<String, String> config) {
		clearData();
		this.modPowAbstraction = modPowAbstraction;
		this.queryElements = queryElements;
		this.NSquared = NSquared;
		this.useMontgomery = new Boolean(config.getOrDefault(ResponderProperties.USE_MONTGOMERY, "false"));
		if (useMontgomery) {
			mont = new MontgomeryReduction(NSquared);
		} else {
			mont = null;
		}
	}

	@Override
	public void insertDataPart(int rowIndex, BigInteger part) {
		long partLong = part.longValue();
		if (0 != partLong) {
			Pair pair = new Pair(partLong, queryElements.get(rowIndex));
			maxHeap.add(pair);
		}
	}

	@Override
	public void insertDataPart(BigInteger queryElement, BigInteger part) {
		long partLong = part.longValue();
		if (0 != partLong) {
			Pair pair = new Pair(partLong, queryElement);
			maxHeap.add(pair);
		}
	}

	@Override
	public BigInteger computeColumnAndClearData() {
		if (maxHeap.isEmpty()) {
			if (useMontgomery) {
				return mont.getMontOne();
			} else {
				return BigInteger.ONE;
			}
		}

		Long a;
		BigInteger g;
		Pair pair;
		while (maxHeap.size() > 1) {
			Long b;
			BigInteger h;
			pair = maxHeap.poll();
			a = pair._1();
			g = pair._2();
			pair = maxHeap.poll();
			b = pair._1();
			h = pair._2();
			long q = Long.divideUnsigned(a.longValue(), b.longValue());
			long r = Long.remainderUnsigned(a.longValue(), b.longValue());
			if (useMontgomery) {
				BigInteger power = mont.montExp(g, BigInteger.valueOf(q));
				h = mont.montMultiply(h, power);
			} else {
				BigInteger power = modPowAbstraction.modPow(g, BigInteger.valueOf(q), NSquared);
				h = h.multiply(power).mod(NSquared);
			}
			maxHeap.add(new Pair(b, h));
			if (r != 0) {
				maxHeap.add(new Pair(r, g));
			}
		}

		// maxHeap.size() must be 1
		pair = maxHeap.poll();
		a = pair._1();
		g = pair._2();
		BigInteger answer;
		if (useMontgomery) {
			answer = mont.montExp(g, BigInteger.valueOf(a));
		} else {
			answer = modPowAbstraction.modPow(g, BigInteger.valueOf(a), NSquared);
		}
		return answer;
	}

	@Override
	public void clearData() {
		maxHeap.clear();
	}

	@Override
	public void free() {}

	public void dumpAndClearHeap() {
		System.out.println("dumpAndClearHeap()");
		while (!maxHeap.isEmpty()) {
			Pair pair = maxHeap.poll();
			System.out.println("pair: " + pair);
		}
		System.out.println("done");
	}

	public void dump() {
		System.out.println("dump()");
		Pair[] pairs = maxHeap.toArray(new Pair[0]);
		Arrays.sort(pairs, pairComparator);
		for (Pair pair : pairs) {
			System.out.println("pair: " + pair);
		}
		System.out.println("done");
	}

	@Override
	public String name() {
		return "DeRooij";
	}

	// public static void main(String args[]) {
	// System.out.println("hello, world!");
	// HashMap<Integer, BigInteger> queryElements = new HashMap<>();
	// ComputeEncryptedColumnDeRooij cec = new ComputeEncryptedColumnDeRooij(queryElements,
	// BigInteger.ONE);
	// cec.maxHeap.add(new Pair(Long.valueOf(3), BigInteger.valueOf(0)));
	// cec.maxHeap.add(new Pair(Long.valueOf(-2), BigInteger.valueOf(1)));
	// cec.maxHeap.add(new Pair(Long.valueOf(4), BigInteger.valueOf(2)));
	// System.out.println("cec.size(): " + cec.maxHeap.size());
	// cec.dump();
	// cec.dump();
	// cec.dumpAndClearHeap();
	// cec.dump();
	// System.out.println("cec.size(): " + cec.maxHeap.size());
	// }
}
