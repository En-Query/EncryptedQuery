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
package org.enquery.encryptedquery.xml.transformation;

import javax.xml.namespace.QName;

/**
 *
 */
public interface QueryNames {
	String QUERY_NS = "http://enquery.net/encryptedquery/query";
	QName QUERY = new QName(QUERY_NS, "query");
	QName QUERY_INFO = new QName(QUERY_NS, "queryInfo");
	QName QUERY_ID = new QName(QUERY_NS, "queryId");
	QName QUERY_NAME = new QName(QUERY_NS, "queryName");
	QName CRYPTO_SCHEME_ID = new QName(QUERY_NS, "cryptoSchemeId");
	QName PUBLIC_KEY = new QName(QUERY_NS, "publicKey");
	QName NUM_SELECTORS = new QName(QUERY_NS, "numSelectors");
	QName HASH_BIT_SIZE = new QName(QUERY_NS, "hashBitSize");
	QName HASH_KEY = new QName(QUERY_NS, "hashKey");
	QName NUM_BITS_PER_DATA_ELEMENT = new QName(QUERY_NS, "numBitsPerDataElement");
	QName DATA_CHUNK_SIZE = new QName(QUERY_NS, "dataChunkSize");
	QName NUM_PARTITIONS_PER_DATA_ELEMENT = new QName(QUERY_NS, "numPartitionsPerDataElement");
	QName FILTER_EXPRESSION = new QName(QUERY_NS, "filterExpression");
}
