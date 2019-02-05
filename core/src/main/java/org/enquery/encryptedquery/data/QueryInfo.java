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
package org.enquery.encryptedquery.data;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to hold all of the basic information regarding a query
 * <p>
 * Note that the hash key is specific to the query. If we have hash collisions over our selector
 * set, we will append integers to the key starting with 0 until we no longer have collisions
 */
public class QueryInfo implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final Logger logger = LoggerFactory.getLogger(QueryInfo.class);

	private String identifier;
	// the number of selectors in the query, given by
	// \floor{paillerBitSize/dataPartitionBitSize}
	private int numSelectors;
	private String cryptoSchemeId;
	private String queryName;
	private PublicKey publicKey;

	// Bit size of the keyed hash function
	private int hashBitSize;

	// Key for the keyed hash function
	private String hashKey;

	// number of bytes for each partition of an incoming data
	// element
	private int dataChunkSize;

	private int numBitsPerDataElement;

	// number of partitions of size dataPartitionBitSize per data element
	private int numPartitionsPerDataElement;

	// whether or not to embed the selector in the results -
	// results in a very low
	// false positive rate for variable length selectors and a zero false positive rate
	// for selectors of fixed size < 32 bits
	private boolean embedSelector;

	private QuerySchema querySchema;

	public String getIdentifier() {
		return identifier;
	}

	public String getQueryName() {
		return queryName;
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

	public int getDataChunkSize() {
		return dataChunkSize;
	}

	public boolean getEmbedSelector() {
		return embedSelector;
	}

	public Map<String, Object> toMap() {
		Map<String, Object> queryInfo = new HashMap<>();
		queryInfo.put("uuid", identifier.toString());
		queryInfo.put("queryName", queryName);
		queryInfo.put("numSelectors", numSelectors);
		queryInfo.put("hashBitSize", hashBitSize);
		queryInfo.put("hashKey", hashKey);
		queryInfo.put("numBitsPerDataElement", numBitsPerDataElement);
		queryInfo.put("numPartitionsPerDataElement", numPartitionsPerDataElement);
		queryInfo.put("dataChunkSize", dataChunkSize);
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
		builder.append("\n      dataChunkSize (" + dataChunkSize + ")");
		builder.append("\n      Query Name (" + queryName + ")");
		builder.append("\n      embedSelector (" + embedSelector + ")");
		logger.info(builder.toString());
	}

	// @Override
	// public QueryInfo clone() {
	// try {
	// return (QueryInfo) super.clone();
	// } catch (CloneNotSupportedException e) {
	// throw new RuntimeException(e);
	// }
	// }


	public void setNumBitsPerDataElement(int numBitsPerDataElement) {
		this.numBitsPerDataElement = numBitsPerDataElement;
	}

	public void setNumPartitionsPerDataElement(int numPartitionsPerDataElement) {
		this.numPartitionsPerDataElement = numPartitionsPerDataElement;
	}

	public void setNumSelectors(int numSelectors) {
		this.numSelectors = numSelectors;
	}

	public void setQueryName(String queryName) {
		this.queryName = queryName;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public void setHashBitSize(int hashBitSize) {
		this.hashBitSize = hashBitSize;
	}

	/**
	 * Number of bits for each partition of an incoming data element
	 * 
	 * @param dataChunkSize
	 */
	public void setDataChunkSize(int dataChunkSize) {
		this.dataChunkSize = dataChunkSize;
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

	public String getCryptoSchemeId() {
		return cryptoSchemeId;
	}

	public void setCryptoSchemeId(String cryptoSchemeId) {
		this.cryptoSchemeId = cryptoSchemeId;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(PublicKey publicKey) {
		this.publicKey = publicKey;
	}
}
