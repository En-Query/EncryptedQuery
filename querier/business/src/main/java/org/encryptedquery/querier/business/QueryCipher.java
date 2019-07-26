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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.querier.QuerierProperties;
import org.enquery.encryptedquery.querier.data.transformation.JSONConverter;
import org.enquery.encryptedquery.querier.data.transformation.QuerySchemaTypeConverter;
import org.enquery.encryptedquery.querier.encrypt.EncryptQuery;
import org.enquery.encryptedquery.querier.encrypt.Querier;
import org.enquery.encryptedquery.utils.PIRException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * OSGi Service that encrypts Queries. Thread-safe.
 *
 */
@Component(service = QueryCipher.class)
public class QueryCipher {

	private final Logger logger = LoggerFactory.getLogger(QueryCipher.class);
	private final ConcurrentMap<Integer, Instant> inProgess = new ConcurrentHashMap<>(16);

	@Reference
	private QuerySchemaTypeConverter querySchemaConverter;
	@Reference
	private EncryptQuery encryptQuery;

	private Integer hashBitSize;

	@Activate
	public void activate(Map<String, String> config) {
		hashBitSize = Integer.parseInt(config.getOrDefault(QuerierProperties.HASH_BIT_SIZE, "12"));
	}

	public Querier run(org.enquery.encryptedquery.querier.data.entity.jpa.Query jpaQuery) throws PIRException, InterruptedException {
		Instant alreadyEncryptingSince = null;

		try {
			alreadyEncryptingSince = inProgess.putIfAbsent(jpaQuery.getId(), Instant.now());
			if (alreadyEncryptingSince != null) {
				logger.warn("Query {} is currently being encrypted, since {}. Ignoring,", jpaQuery,
						alreadyEncryptingSince);
				return null;
			}

			QuerySchema querySchema = querySchemaConverter.toCoreQuerySchema(jpaQuery.getQuerySchema());

			logger.info("Starting to encrypt {}.", jpaQuery);

			List<String> selectors = JSONConverter.toList(jpaQuery.getSelectorValues());
			Validate.notNull(selectors, "No selector values, aborting query generation. At least one selector required.");

			// TODO: get from JPA Query record as individual fields, we should move away from
			// generic maps
			Map<String, String> parameters = JSONConverter.toMapStringString(jpaQuery.getParameters());
			// boolean embedSelector = jpaQuery.getEmbedSelector();
			int dataChunkSize = Integer.valueOf(parameters.getOrDefault(QuerierProperties.DATA_CHUNK_SIZE, "1"));
			if (parameters.containsKey(QuerierProperties.HASH_BIT_SIZE)) {
				hashBitSize = Integer.parseInt(parameters.getOrDefault(QuerierProperties.HASH_BIT_SIZE, hashBitSize.toString()));
			}

			logger.info("  - HashBitSize ( {} )", hashBitSize);
			logger.info("  - Number of Selectors ( {} )", selectors.size());
			logger.info("  - Additional Parameters:");
			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				logger.info("     {} = {}", entry.getKey(), entry.getValue());
			}

			Querier querier = encryptQuery.encrypt(querySchema, selectors, dataChunkSize, hashBitSize);

			logger.info("Finished encrypting {}.", jpaQuery);
			return querier;
		} finally {
			if (alreadyEncryptingSince == null)
				inProgess.remove(jpaQuery.getId());
		}
	}

	public boolean isInProgress(int queryId) {
		return inProgess.containsKey(queryId);
	}
}
