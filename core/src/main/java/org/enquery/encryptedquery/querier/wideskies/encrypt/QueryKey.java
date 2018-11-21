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
package org.enquery.encryptedquery.querier.wideskies.encrypt;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.enquery.encryptedquery.encryption.Paillier;

/**
 * Key used to encrypt and decrypt the Query
 *
 */
public class QueryKey implements Serializable {

	private static final long serialVersionUID = 5087698250407136897L;

	// the identifier of the query
	private UUID identifier;

	// Paillier encryption functionality
	private Paillier paillier = null;

	// selectors
	private List<String> selectors = null;

	// map to check the embedded selectors in the results for false positives;
	// if the selector is a fixed size < 32 bits, it is included as is
	// if the selector is of variable lengths
	private Map<Integer, String> embedSelectorMap = null;

	public QueryKey(List<String> selectors, Paillier paillier, Map<Integer, String> embedSelectorMap, UUID identifier) {
		this.selectors = selectors;
		this.paillier = paillier;
		this.embedSelectorMap = embedSelectorMap;
		this.identifier = identifier;
	}

	public UUID getIdentifier() {
		return identifier;
	}

	public Paillier getPaillier() {
		return paillier;
	}

	public List<String> getSelectors() {
		return selectors;
	}

	public Map<Integer, String> getEmbedSelectorMap() {
		return embedSelectorMap;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((embedSelectorMap == null) ? 0 : embedSelectorMap.hashCode());
		result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
		result = prime * result + ((paillier == null) ? 0 : paillier.hashCode());
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
		if (identifier == null) {
			if (other.identifier != null) {
				return false;
			}
		} else if (!identifier.equals(other.identifier)) {
			return false;
		}
		if (paillier == null) {
			if (other.paillier != null) {
				return false;
			}
		} else if (!paillier.equals(other.paillier)) {
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

