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

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.RetrievalStatus;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.json.RetrievalCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.RetrievalResponse;
import org.enquery.encryptedquery.querier.data.service.RetrievalRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = RetrievalStatusResolver.class)
public class RetrievalStatusResolver {

	private final Logger log = LoggerFactory.getLogger(RetrievalStatusResolver.class);
	private final ConcurrentMap<Integer, Instant> inProgess = new ConcurrentHashMap<>(16);
	@Reference
	private RetrievalRepository retrievalRepo;

	public RetrievalResponse resolve(RetrievalResponse json) {
		resolve(json.getData());
		return json;
	}

	public RetrievalCollectionResponse resolve(RetrievalCollectionResponse json) {
		Validate.notNull(json);
		json.getData()
				.stream()
				.forEach(r -> {
					resolve(r);
				});
		return json;
	}

	public org.enquery.encryptedquery.querier.data.entity.json.Retrieval resolve(org.enquery.encryptedquery.querier.data.entity.json.Retrieval jsonRetrieval) {
		Validate.notNull(jsonRetrieval);

		final Integer retrievalId = Integer.valueOf(jsonRetrieval.getId());

		if (inProgess.containsKey(retrievalId)) {
			jsonRetrieval.setStatus(RetrievalStatus.InProgress);
			return jsonRetrieval;
		}

		Retrieval retrieval = retrievalRepo.find(retrievalId);
		if (retrieval.getErrorMessage() != null) {
			jsonRetrieval.setStatus(RetrievalStatus.Failed);
		} else if (retrieval.getPayloadUri() != null) {
			jsonRetrieval.setStatus(RetrievalStatus.Complete);
		} else {
			jsonRetrieval.setStatus(RetrievalStatus.Pending);
		}

		return jsonRetrieval;
	}

	public void retrievalStarted(Retrieval retrieval) {
		log.info("Retrieval {} started.", retrieval.getId());
		inProgess.put(retrieval.getId(), Instant.now());
	}

	public void retrievalEnded(Retrieval retrieval) {
		log.info("Retrieval {} ended.", retrieval.getId());
		inProgess.remove(retrieval.getId());
	}
}
