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
package org.enquery.encryptedquery.query.wideskies;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class to hold the PIR query vectors
 */

public class Query implements Serializable {

	private static final long serialVersionUID = -3441581700420591701L;

	public static final long querySerialVersionUID = 1L;

	// So that we can serialize the version number in gson.
	public final long queryVersion = querySerialVersionUID;

	// holds all query info
	private QueryInfo queryInfo;

	// query elements - ordered on insertion
	private SortedMap<Integer, BigInteger> queryElements = new TreeMap<>();

	// lookup table for exponentiation of query vectors - based on dataPartitionBitSize
	// element -> <power, element^power mod N^2>
	// @Expose
	private Map<BigInteger, Map<Integer, BigInteger>> expTable = new ConcurrentHashMap<>();

	// File based lookup table for modular exponentiation
	// element hash -> filename containing it's <power, element^power mod N^2> modular
	// exponentiations
	private Map<Integer, String> expFileBasedLookup = new HashMap<>();

	// N=pq, RSA modulus for the Paillier encryption associated with the
	// queryElements
	private BigInteger N;

	private BigInteger NSquared;

	public Query() {}

	public Query(QueryInfo queryInfo, BigInteger N, SortedMap<Integer, BigInteger> queryElements) {
		this(queryInfo, N, N.pow(2), queryElements);
	}

	public Query(QueryInfo queryInfo, BigInteger N, BigInteger NSquared, SortedMap<Integer, BigInteger> queryElements) {
		this.queryInfo = queryInfo;
		this.N = N;
		this.NSquared = NSquared;
		this.queryElements = queryElements;
	}

	public QueryInfo getQueryInfo() {
		return queryInfo;
	}

	public SortedMap<Integer, BigInteger> getQueryElements() {
		return queryElements;
	}

	public BigInteger getQueryElement(int index) {
		return queryElements.get(index);
	}

	public BigInteger getN() {
		return N;
	}

	public BigInteger getNSquared() {
		return NSquared;
	}

	public Map<Integer, String> getExpFileBasedLookup() {
		return expFileBasedLookup;
	}

	public String getExpFile(int i) {
		return expFileBasedLookup.get(i);
	}

	public void setExpFileBasedLookup(Map<Integer, String> expInput) {
		expFileBasedLookup = expInput;
	}

	public BigInteger getExp(BigInteger value, int power) {
		Map<Integer, BigInteger> powerMap = expTable.get(value);
		return (powerMap == null) ? null : powerMap.get(power);
	}

	public void addExp(BigInteger element, Map<Integer, BigInteger> powMap) {
		expTable.put(element, powMap);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		Query query = (Query) o;

		if (!queryInfo.equals(query.queryInfo)) {
			return false;
		}
		if (!queryElements.equals(query.queryElements)) {
			return false;
		}
		if (expTable != null ? !expTable.equals(query.expTable) : query.expTable != null) {
			return false;
		}
		if (expFileBasedLookup != null ? !expFileBasedLookup.equals(query.expFileBasedLookup) : query.expFileBasedLookup != null) {
			return false;
		}
		if (!N.equals(query.N)) {
			return false;
		}
		return NSquared.equals(query.NSquared);
	}

	@Override
	public int hashCode() {
		return Objects.hash(queryInfo, queryElements, expTable, expFileBasedLookup, N, NSquared);
	}

	public Map<BigInteger, Map<Integer, BigInteger>> getExpTable() {
		return expTable;
	}

	public void setExpTable(Map<BigInteger, Map<Integer, BigInteger>> expTable) {
		this.expTable = expTable;
	}

	/**
	 * @param queryInfo the queryInfo to set
	 */
	public void setQueryInfo(QueryInfo queryInfo) {
		this.queryInfo = queryInfo;
	}

	/**
	 * @param queryElements the queryElements to set
	 */
	public void setQueryElements(SortedMap<Integer, BigInteger> queryElements) {
		this.queryElements = queryElements;
	}

	/**
	 * @param n the n to set
	 */
	public void setN(BigInteger n) {
		N = n;
	}

	/**
	 * @param nSquared the nSquared to set
	 */
	public void setNSquared(BigInteger nSquared) {
		NSquared = nSquared;
	}
}
