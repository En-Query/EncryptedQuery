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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

	// optional SQL-like filter expression to filter out records
	private String filterExpression;

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
		queryInfo.put("filterExpression", filterExpression);
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
		// builder.append("\n embedSelector (" + embedSelector + ")");
		logger.info(builder.toString());
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

	public String getFilterExpression() {
		return filterExpression;
	}

	public void setFilterExpression(String filterExpression) {
		this.filterExpression = filterExpression;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cryptoSchemeId == null) ? 0 : cryptoSchemeId.hashCode());
		result = prime * result + dataChunkSize;
		// result = prime * result + (embedSelector ? 1231 : 1237);
		result = prime * result + hashBitSize;
		result = prime * result + ((hashKey == null) ? 0 : hashKey.hashCode());
		result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
		result = prime * result + numBitsPerDataElement;
		result = prime * result + numPartitionsPerDataElement;
		result = prime * result + numSelectors;
		result = prime * result + ((queryName == null) ? 0 : queryName.hashCode());
		result = prime * result + ((querySchema == null) ? 0 : querySchema.hashCode());
		result = prime * result + ((filterExpression == null) ? 0 : filterExpression.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		QueryInfo other = (QueryInfo) obj;
		if (cryptoSchemeId == null) {
			if (other.cryptoSchemeId != null) {
				return false;
			}
		} else if (!cryptoSchemeId.equals(other.cryptoSchemeId)) {
			return false;
		}
		if (dataChunkSize != other.dataChunkSize) {
			return false;
		}
		if (hashBitSize != other.hashBitSize) {
			return false;
		}
		if (hashKey == null) {
			if (other.hashKey != null) {
				return false;
			}
		} else if (!hashKey.equals(other.hashKey)) {
			return false;
		}
		if (identifier == null) {
			if (other.identifier != null) {
				return false;
			}
		} else if (!identifier.equals(other.identifier)) {
			return false;
		}
		if (numBitsPerDataElement != other.numBitsPerDataElement) {
			return false;
		}
		if (numPartitionsPerDataElement != other.numPartitionsPerDataElement) {
			return false;
		}
		if (numSelectors != other.numSelectors) {
			return false;
		}
		if (queryName == null) {
			if (other.queryName != null) {
				return false;
			}
		} else if (!queryName.equals(other.queryName)) {
			return false;
		}
		if (querySchema == null) {
			if (other.querySchema != null) {
				return false;
			}
		} else if (!querySchema.equals(other.querySchema)) {
			return false;
		}
		if (!publicKeysEquals(publicKey, other.publicKey)) {
			return false;
		}
		if (cryptoSchemeId == null) {
			if (other.cryptoSchemeId != null) {
				return false;
			}
		} else if (!cryptoSchemeId.equals(cryptoSchemeId)) {
			return false;
		}
		return true;
	}

	/**
	 * @param publicKey2
	 * @param publicKey3
	 * @return
	 */
	private boolean publicKeysEquals(PublicKey pk1, PublicKey pk2) {
		if (pk1 == null) {
			if (pk2 != null) {
				return false;
			}
		}
		if (pk2 == null) {
			if (pk1 != null) {
				return false;
			}
		}

		return Arrays.equals(pk1.getEncoded(), pk2.getEncoded()) &&
				Objects.equals(pk1.getAlgorithm(), pk2.getAlgorithm()) &&
				Objects.equals(pk1.getFormat(), pk2.getFormat());
	}

}
