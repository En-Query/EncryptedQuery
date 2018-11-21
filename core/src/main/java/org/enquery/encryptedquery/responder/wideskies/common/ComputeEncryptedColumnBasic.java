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
import java.util.Map;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;

public class ComputeEncryptedColumnBasic implements ComputeEncryptedColumn {
	private Map<Integer, BigInteger> queryElements;
	private BigInteger NSquared;
	private BigInteger product;

	private ModPowAbstraction modPowAbstraction;

	@Override
	public void initialize(Map<Integer, BigInteger> queryElements, BigInteger NSquared,
			ModPowAbstraction modPowAbstraction, Map<String, String> config) {
		clearData();
		this.modPowAbstraction = modPowAbstraction;
		this.queryElements = queryElements;
		this.NSquared = NSquared;
	}

	@Override
	public void insertDataPart(int rowIndex, BigInteger part) {
		if (part.compareTo(BigInteger.ZERO) == 0) {
			return;
		}
		BigInteger queryElement = queryElements.get(rowIndex);
		BigInteger encryptedPart = modPowAbstraction.modPow(queryElement, part, NSquared);
		product = product.multiply(encryptedPart).mod(NSquared);
	}

	@Override
	public void insertDataPart(BigInteger queryElement, BigInteger part) {
		if (part.compareTo(BigInteger.ZERO) == 0) {
			return;
		}
		BigInteger encryptedPart = modPowAbstraction.modPow(queryElement, part, NSquared);
		product = product.multiply(encryptedPart).mod(NSquared);
	}

	@Override
	public BigInteger computeColumnAndClearData() {
		BigInteger answer = product;
		product = BigInteger.ONE;
		return answer;
	}

	@Override
	public void clearData() {
		product = BigInteger.ONE;
	}

	@Override
	public void free() {}

	@Override
	public String name() {
		return "Basic";
	}
}
