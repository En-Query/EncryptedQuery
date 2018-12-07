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
package org.enquery.encryptedquery.flink;

import java.math.BigInteger;

import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.util.Collector;

public class ColumnReduceFunction implements
		GroupReduceFunction<Tuple2<Integer, BigInteger>, Tuple2<Integer, BigInteger>> {

	private static final long serialVersionUID = 1L;
	private final BigInteger nSquared;

	public ColumnReduceFunction(BigInteger nSquared) {
		this.nSquared = nSquared;
	}

	@Override
	public void reduce(Iterable<Tuple2<Integer, BigInteger>> values, Collector<Tuple2<Integer, BigInteger>> out) throws Exception {
		BigInteger accumulator = BigInteger.valueOf(1);
		Integer column = null;
		for (Tuple2<Integer, BigInteger> entry : values) {
			// capture the column only once, all will be the same
			if (column == null) {
				column = entry.f0;
			}
			accumulator = (accumulator.multiply(entry.f1)).mod(nSquared);
		}
		out.collect(Tuple2.of(column, accumulator));
	}
}