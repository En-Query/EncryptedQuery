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
import java.util.Map;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.utils.MontgomeryReduction;

public class ComputeEncryptedColumnYao implements ComputeEncryptedColumn {
	private Map<Integer, BigInteger> queryElements;
	private boolean useMontgomery;
	private BigInteger NSquared;
	private int b;
	private BigInteger[] hs;
	private MontgomeryReduction mont;

	public static void validateParameters(int dataPartitionBitSize) {
		if (dataPartitionBitSize <= 0) {
			throw new IllegalArgumentException("Yao responder method requires dataPartitionBitSize > 0, " + dataPartitionBitSize + " given");
		}
		if (dataPartitionBitSize > 16) {
			throw new IllegalArgumentException("Yao responder method currently requires dataPartitionBitSize <= 16 to limit memory usage, " + dataPartitionBitSize + " given");
		}
	}

	@Override
	public void initialize(Map<Integer, BigInteger> queryElements, BigInteger NSquared, ModPowAbstraction modPowAbstraction, Map<String, String> config) {
		// public ComputeEncryptedColumnYao(Map<Integer,BigInteger> queryElements, BigInteger
		// NSquared, int dataPartitionBitSize, boolean useMontgomery)
		// validateParameters(...)?

		this.queryElements = queryElements;
		this.NSquared = NSquared;

		this.useMontgomery = new Boolean(config.getOrDefault(ResponderProperties.USE_MONTGOMERY, "false"));
		if (useMontgomery) {
			mont = new MontgomeryReduction(NSquared);
		} else {
			mont = null;
		}

		this.b = Integer.valueOf(config.get(ResponderProperties.DATA_PARTITION_BIT_SIZE));
		validateParameters(b);

		this.hs = new BigInteger[1 << b];
	}

	@Override
	public void insertDataPart(int rowIndex, BigInteger part) {
		// NOTE: assuming part < 2**b
		// TODO: handle Montgomery case
		int partInt = part.intValue();
		if (0 != partInt) {
			BigInteger queryElement = queryElements.get(rowIndex);
			if (hs[partInt] != null) {
				if (useMontgomery) {
					hs[partInt] = mont.montMultiply(hs[partInt], queryElement);
				} else {
					hs[partInt] = hs[partInt].multiply(queryElement).mod(NSquared);
				}
			} else {
				hs[partInt] = queryElement;
			}
		}
	}

	@Override
	public void insertDataPart(BigInteger queryElement, BigInteger part) {
		int partInt = part.intValue();
		if (0 != partInt) {
			if (hs[partInt] != null) {
				if (useMontgomery) {
					hs[partInt] = mont.montMultiply(hs[partInt], queryElement);
				} else {
					hs[partInt] = hs[partInt].multiply(queryElement).mod(NSquared);
				}
			} else {
				hs[partInt] = queryElement;
			}
		}
	}

	@Override
	public BigInteger computeColumnAndClearData() {
		if (useMontgomery) {
			return computeColumnAndClearDataMontgomery();
		}

		BigInteger out = BigInteger.ONE;
		BigInteger a = BigInteger.ONE;
		for (int x = (1 << b) - 1; x > 0; x--) {
			if (hs[x] != null) {
				a = a.multiply(hs[x]).mod(NSquared);
			}
			out = out.multiply(a).mod(NSquared);
		}
		Arrays.fill(hs, null);
		return out;
	}

	private BigInteger computeColumnAndClearDataMontgomery() {
		// TODO: handle Montgomery case:
		BigInteger out = mont.getMontOne();
		BigInteger a = mont.getMontOne();
		for (int x = (1 << b) - 1; x > 0; x--) {
			if (hs[x] != null) {
				a = mont.montMultiply(a, hs[x]);
			}
			out = mont.montMultiply(out, a);
		}
		Arrays.fill(hs, null);
		return out;
	}

	@Override
	public void clearData() {
		Arrays.fill(hs, null);
	}

	@Override
	public void free() {}

	@Override
	public String name() {
		return "Yao";
	}

}
