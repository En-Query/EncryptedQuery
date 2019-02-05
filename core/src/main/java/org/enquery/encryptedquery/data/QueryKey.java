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
import java.security.KeyPair;
import java.util.List;
import java.util.Map;

/**
 * Key used to encrypt and decrypt the Query
 *
 */
public class QueryKey implements Serializable {

	private static final long serialVersionUID = 5087698250407136897L;

	private final String queryId;
	private final KeyPair keyPair;
	private final String cryptoSchemeId;
	private final List<String> selectors;

	// map to check the embedded selectors in the results for false positives;
	// if the selector is a fixed size < 32 bits, it is included as is
	// if the selector is of variable lengths
	private Map<Integer, String> embedSelectorMap = null;

	public QueryKey(List<String> selectors,
			KeyPair keyPair,
			Map<Integer, String> embedSelectorMap,
			String queryId,
			String cryptoSchemeId)//
	{
		this.selectors = selectors;
		this.keyPair = keyPair;
		this.embedSelectorMap = embedSelectorMap;
		this.queryId = queryId.toString();
		this.cryptoSchemeId = cryptoSchemeId;
	}

	public String getQueryId() {
		return queryId;
	}

	public List<String> getSelectors() {
		return selectors;
	}

	public Map<Integer, String> getEmbedSelectorMap() {
		return embedSelectorMap;
	}

	public KeyPair getKeyPair() {
		return keyPair;
	}

	public String getCryptoSchemeId() {
		return cryptoSchemeId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((embedSelectorMap == null) ? 0 : embedSelectorMap.hashCode());
		result = prime * result + ((queryId == null) ? 0 : queryId.hashCode());
		result = prime * result + ((keyPair == null) ? 0 : keyPair.hashCode());
		result = prime * result + ((selectors == null) ? 0 : selectors.hashCode());
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
		QueryKey other = (QueryKey) obj;
		if (embedSelectorMap == null) {
			if (other.embedSelectorMap != null) {
				return false;
			}
		} else if (!embedSelectorMap.equals(other.embedSelectorMap)) {
			return false;
		}
		if (queryId == null) {
			if (other.queryId != null) {
				return false;
			}
		} else if (!queryId.equals(other.queryId)) {
			return false;
		}
		if (keyPair == null) {
			if (other.keyPair != null) {
				return false;
			}
		} else if (!keyPair.equals(other.keyPair)) {
			return false;
		}
		if (selectors == null) {
			if (other.selectors != null) {
				return false;
			}
		} else if (!selectors.equals(other.selectors)) {
			return false;
		}
		return true;
	}
}

