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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QuerySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to hold all of the basic information regarding a query
 * <p>
 * Note that the hash key is specific to the query. If we have hash collisions over our selector
 * set, we will append integers to the key starting with 0 until we no longer have collisions
 */
public class QueryInfo implements Serializable, Cloneable {
	private static final long serialVersionUID = 1L;

	private static final Logger logger = LoggerFactory.getLogger(QueryInfo.class);

	// the identifier of the query
	private UUID identifier;

	// the number of selectors in the query, given by
	// \floor{paillerBitSize/dataPartitionBitSize}
	private int numSelectors;

	private String queryType;

	// Bit size of the keyed hash function
	private int hashBitSize;

	// Key for the keyed hash function
	private String hashKey;

	// number of bits for each partition of an incoming data
	// element, must be < 32 right now
	private int dataPartitionBitSize;

	private int numBitsPerDataElement;

	// number of partitions of size dataPartitionBitSize per data element
	private int numPartitionsPerDataElement;

	// whether or not to generate and use the
	// expLookupTable for encryption, it is very
	// expensive to compute
	private boolean useExpLookupTable;

	// whether or not to use the expLookupTable
	// stored in HDFS
	// if it doesn't yet exist, it will be created within the cluster and stored in HDFS
	private boolean useHDFSExpLookupTable;

	// whether or not to embed the selector in the results -
	// results in a very low
	// false positive rate for variable length selectors and a zero false positive rate
	// for selectors of fixed size < 32 bits
	private boolean embedSelector;

	private QuerySchema querySchema;

	public QueryInfo() {}

	public UUID getIdentifier() {
		return identifier;
	}

	public String getQueryType() {
		return queryType;
	}

	public int getNumSelectors() {
		return numSelectors;
	}

	public int getHashBitSize() {
		return hashBitSize;
	}

	public String getHashKey() {
		return hashKey;
	}

	public void setHashKey(String hashKey) {
		this.hashKey = hashKey;
	}

	public int getNumBitsPerDataElement() {
		return numBitsPerDataElement;
	}

	public int getNumPartitionsPerDataElement() {
		return numPartitionsPerDataElement;
	}

	public int getDataPartitionBitSize() {
		return dataPartitionBitSize;
	}

	public boolean useExpLookupTable() {
		return useExpLookupTable;
	}

	public boolean useHDFSExpLookupTable() {
		return useHDFSExpLookupTable;
	}

	public boolean getEmbedSelector() {
		return embedSelector;
	}

	public Map<String, Object> toMap() {
		Map<String, Object> queryInfo = new HashMap<>();
		queryInfo.put("uuid", identifier.toString());
		queryInfo.put("queryType", queryType);
		queryInfo.put("numSelectors", numSelectors);
		queryInfo.put("hashBitSize", hashBitSize);
		queryInfo.put("hashKey", hashKey);
		queryInfo.put("numBitsPerDataElement", numBitsPerDataElement);
		queryInfo.put("numPartitionsPerDataElement", numPartitionsPerDataElement);
		queryInfo.put("dataPartitionsBitSize", dataPartitionBitSize);
		queryInfo.put("useExpLookupTable", useExpLookupTable);
		queryInfo.put("useHDFSExpLookupTable", useHDFSExpLookupTable);
		queryInfo.put("embedSelector", embedSelector);

		return queryInfo;
	}


	public QuerySchema getQuerySchema() {
		return querySchema;
	}

	/**
	 * @param querySchema the querySchema to set
	 */
	public void setQuerySchema(QuerySchema querySchema) {
		this.querySchema = querySchema;
	}

	public void printQueryInfo() {
		StringBuilder builder = new StringBuilder();
		builder.append("Query Info [UUID=").append(identifier).append("]");
		builder.append("\n      numSelectors (" + numSelectors + ") ");
		builder.append("\n      hashBitSize (" + hashBitSize + ")");
		builder.append("\n      hashKey (" + hashKey + ")");
		builder.append("\n      dataPartitionBitSize (" + dataPartitionBitSize + ")");
		builder.append("\n      Query Name (" + queryType + ")");
		builder.append("\n      useExpLookupTable (" + useExpLookupTable + ")");
		builder.append("\n      useHDFSLookupTable (" + useHDFSExpLookupTable + ")");
		builder.append("\n      embedSelector (" + embedSelector + ")");

		logger.info(builder.toString());
	}

	@Override
	public QueryInfo clone() {
		try {
			return (QueryInfo) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		QueryInfo queryInfo = (QueryInfo) o;

		if (numSelectors != queryInfo.numSelectors)
			return false;
		if (hashBitSize != queryInfo.hashBitSize)
			return false;
		if (numBitsPerDataElement != queryInfo.numBitsPerDataElement)
			return false;
		if (dataPartitionBitSize != queryInfo.dataPartitionBitSize)
			return false;
		if (numPartitionsPerDataElement != queryInfo.numPartitionsPerDataElement)
			return false;
		if (useExpLookupTable != queryInfo.useExpLookupTable)
			return false;
		if (useHDFSExpLookupTable != queryInfo.useHDFSExpLookupTable)
			return false;
		if (embedSelector != queryInfo.embedSelector)
			return false;
		if (!identifier.equals(queryInfo.identifier))
			return false;
		if (!queryType.equals(queryInfo.queryType))
			return false;
		if (!hashKey.equals(queryInfo.hashKey))
			return false;
		return querySchema != null ? querySchema.equals(queryInfo.querySchema) : queryInfo.querySchema == null;

	}

	@Override
	public int hashCode() {
		return Objects.hash(identifier, numSelectors, queryType, hashBitSize, hashKey, numBitsPerDataElement, useExpLookupTable, useHDFSExpLookupTable,
				embedSelector, querySchema);
	}

	public void setNumBitsPerDataElement(int numBitsPerDataElement) {
		this.numBitsPerDataElement = numBitsPerDataElement;
	}

	public void setNumPartitionsPerDataElement(int numPartitionsPerDataElement) {
		this.numPartitionsPerDataElement = numPartitionsPerDataElement;
	}

	public void setNumSelectors(int numSelectors) {
		this.numSelectors = numSelectors;
	}

	public void setQueryType(String queryType) {
		this.queryType = queryType;
	}

	public void setIdentifier(UUID identifier) {
		this.identifier = identifier;
	}

	public void setHashBitSize(int hashBitSize) {
		this.hashBitSize = hashBitSize;
	}

	/**
	 * Number of bits for each partition of an incoming data element, must be < 32 right now
	 * 
	 * @param dataPartitionBitSize
	 */
	public void setDataPartitionBitSize(int dataPartitionBitSize) {
		Validate.isTrue(dataPartitionBitSize < 32);
		this.dataPartitionBitSize = dataPartitionBitSize;
	}

	/**
	 * Whether or not to generate and use the expLookupTable for encryption, it is very expensive to
	 * compute
	 * 
	 * @return the useExpLookupTable
	 */
	public boolean isUseExpLookupTable() {
		return useExpLookupTable;
	}

	/**
	 * @param useExpLookupTable the useExpLookupTable to set
	 */
	public void setUseExpLookupTable(boolean useExpLookupTable) {
		this.useExpLookupTable = useExpLookupTable;
	}

	/**
	 * Whether or not to embed the selector in the results results in a very low false positive rate
	 * for variable length selectors and a zero false positive rate for selectors of fixed size < 32
	 * bits
	 * 
	 * @return the embedSelector
	 */
	public boolean isEmbedSelector() {
		return embedSelector;
	}

	/**
	 * @param embedSelector the embedSelector to set
	 */
	public void setEmbedSelector(boolean embedSelector) {
		this.embedSelector = embedSelector;
	}

	/**
	 * Whether or not to use the expLookupTable stored in HDFS if it doesn't yet exist, it will be
	 * created within the cluster and stored in HDFS
	 * 
	 * @return the useHDFSExpLookupTable
	 */
	public boolean isUseHDFSExpLookupTable() {
		return useHDFSExpLookupTable;
	}

	/**
	 * @param useHDFSExpLookupTable the useHDFSExpLookupTable to set
	 */
	public void setUseHDFSExpLookupTable(boolean useHDFSExpLookupTable) {
		this.useHDFSExpLookupTable = useHDFSExpLookupTable;
	}

}
