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
package org.enquery.encryptedquery.querier.encrypt;

import org.enquery.encryptedquery.data.Query;
import org.enquery.encryptedquery.data.QueryKey;

/**
 * Class to hold the information necessary for the PIR querier to perform decryption
 */
public class Querier {

	private final Query query;
	private final QueryKey queryKey;

	public Querier(Query query, QueryKey queryKey) {
		this.query = query;
		this.queryKey = queryKey;
	}

	public Query getQuery() {
		return query;
	}

	public QueryKey getQueryKey() {
		return queryKey;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((query == null) ? 0 : query.hashCode());
		result = prime * result + ((queryKey == null) ? 0 : queryKey.hashCode());
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
		Querier other = (Querier) obj;
		if (query == null) {
			if (other.query != null) {
				return false;
			}
		} else if (!query.equals(other.query)) {
			return false;
		}
		if (queryKey == null) {
			if (other.queryKey != null) {
				return false;
			}
		} else if (!queryKey.equals(other.queryKey)) {
			return false;
		}
		return true;
	}
}
