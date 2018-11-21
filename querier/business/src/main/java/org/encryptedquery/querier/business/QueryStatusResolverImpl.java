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
package org.encryptedquery.querier.business;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.json.Query;
import org.enquery.encryptedquery.querier.data.entity.json.QueryCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QueryResponse;
import org.enquery.encryptedquery.querier.data.entity.json.QueryStatus;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.querier.data.service.QueryStatusResolver;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = QueryStatusResolver.class)
public class QueryStatusResolverImpl implements QueryStatusResolver {

	@Reference
	private QueryCipher cipher;

	@Reference
	private QueryRepository queryRepo;

	@Reference
	private ScheduleRepository scheduleRepo;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.business.QueryStatusResolver#resolve(org.enquery.encryptedquery.
	 * querier.data.entity.json.QueryCollectionResponse)
	 */
	@Override
	public QueryCollectionResponse resolve(QueryCollectionResponse response) {
		Validate.notNull(response);
		response
				.getData()
				.stream()
				.forEach(r -> resolve(r));
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.business.QueryStatusResolver#resolve(org.enquery.encryptedquery.
	 * querier.data.entity.json.QueryResponse)
	 */
	@Override
	public QueryResponse resolve(QueryResponse response) {
		Validate.notNull(response);
		resolve(response.getData());
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.encryptedquery.querier.business.QueryStatusResolver#resolve(org.enquery.encryptedquery.
	 * querier.data.entity.json.Query)
	 */
	@Override
	public Query resolve(Query query) {
		Validate.notNull(query);

		final int queryId = Integer.valueOf(query.getId());
		boolean encrypting = cipher.isInProgress(queryId);
		if (encrypting) {
			query.setStatus(QueryStatus.Encrypting);
			return query;
		}

		boolean encrypted = queryRepo.isGenerated(queryId);
		if (encrypted) {
			if (isScheduled(queryId)) {
				query.setStatus(QueryStatus.Scheduled);
			} else {
				query.setStatus(QueryStatus.Encrypted);
			}
		} else {
			query.setStatus(QueryStatus.Created);
		}

		return query;
	}

	private boolean isScheduled(int id) {
		org.enquery.encryptedquery.querier.data.entity.jpa.Query query = queryRepo.find(id);
		if (query == null) return false;
		return scheduleRepo.countForQuery(query) > 0;
	}
}
