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
package org.enquery.encryptedquery.encryption.impl;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abtract class implementing <code>createQuery</code> and <code>deleteQuery</code>
 * 
 */
public abstract class AbstractCryptoScheme implements CryptoScheme {

	private static final Logger log = LoggerFactory.getLogger(AbstractCryptoScheme.class);

	// may be worthwile synchronizing on query id
	// but maybe not, since createQuery, deleteQuery and findQueryFromHandle are
	// only called during initialization/finallization of the query execution
	// a limited number of times, so synchronized method may be just fine
	private Map<Integer, QueryData> queries = new HashMap<>();
	private int nextHandle = new Random().nextInt();


	/**
	 * If caller does not call clear(), we call it here during garbage collection
	 * 
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#clear()
	 */
	@Override
	synchronized public void close() {
		if (queries != null) {
			queries.clear();
			queries = null;
		}
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.encryption.CryptoScheme#createQuery(org.enquery.encryptedquery.
	 * data.QueryInfo, java.util.Map)
	 */
	@Override
	synchronized public byte[] loadQuery(QueryInfo queryInfo, Map<Integer, CipherText> queryElements) {
		Validate.notNull(queryInfo);
		Validate.notNull(queryElements);

		// First, check if we already have this query
		// if so, increment the reference count and return the same handle.
		for (QueryData qd : queries.values()) {
			if (qd.queryInfo.getIdentifier().equals(queryInfo.getIdentifier())) {
				qd.referenceCount++;
				log.info("Incremented existing query handle {} reference count to {}.", qd.handle, qd.referenceCount);
				return toByteArray(qd.handle);
			}
		}

		QueryData queryData = new QueryData();
		queryData.handle = nextHandle++;
		queryData.queryInfo = queryInfo;
		queryData.queryElements = queryElements;
		queryData.referenceCount = 1;

		log.info("Created query with handle {}.", queryData.handle);
		queries.put(queryData.handle, queryData);
		return toByteArray(queryData.handle);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.encryption.CryptoScheme#deleteQuery(byte[])
	 */
	@Override
	synchronized public void unloadQuery(byte[] handle) {
		Validate.notNull(handle);
		QueryData queryData = findQueryFromHandle(handle);
		queryData.referenceCount--;
		if (queryData.referenceCount == 0) {
			queries.remove(queryData.handle);
		}
	}

	synchronized protected QueryData findQueryFromHandle(byte[] handle) {
		Validate.notNull(handle);
		int handleInt = fromByteArray(handle);
		QueryData queryData = queries.get(handleInt);
		log.info("Searching query for handle {}.", handleInt);
		Validate.notNull(queryData, "Invalid handle provided. Query not found for this handle.");
		return queryData;
	}

	/**
	 * @param key
	 * @return
	 */
	private byte[] toByteArray(Integer value) {
		ByteBuffer result = ByteBuffer.allocate(Integer.BYTES);
		result.putInt(value);
		return result.array();
	}

	private int fromByteArray(byte[] handle) {
		ByteBuffer result = ByteBuffer.wrap(handle);
		return result.getInt();
	}

}
