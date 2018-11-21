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
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.QuerySchema;
import org.enquery.encryptedquery.querier.data.transformation.JSONConverter;
import org.enquery.encryptedquery.querier.data.transformation.QuerySchemaTypeConverter;
import org.enquery.encryptedquery.querier.wideskies.encrypt.EncryptionPropertiesBuilder;
import org.enquery.encryptedquery.querier.wideskies.encrypt.Querier;
import org.enquery.encryptedquery.querier.wideskies.encrypt.QuerierFactory;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.utils.PIRException;
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
	private QuerierFactory querierFactory;
	@Reference
	private QuerySchemaTypeConverter querySchemaConverter;

	public Querier run(org.enquery.encryptedquery.querier.data.entity.jpa.Query jpaQuery) throws PIRException, InterruptedException {
		Instant alreadyEncryptingSince = null;

		try {
			alreadyEncryptingSince = inProgess.putIfAbsent(jpaQuery.getId(), Instant.now());
			if (alreadyEncryptingSince != null) {
				logger.warn("Query {} is currently being encrypted, since {}. Ignoring,", jpaQuery,
						alreadyEncryptingSince);
				return null;
			}

			logger.info("Starting to encrypt query {}.", jpaQuery);
			QuerySchema querySchema = querySchemaConverter.toCoreQuerySchema(jpaQuery.getQuerySchema());

			logger.info("Encrypting query with the following parameters: {}", jpaQuery.getParameters());
			Map<String, String> parameters = JSONConverter.toMapStringString(jpaQuery.getParameters());

			Properties querierProperties = EncryptionPropertiesBuilder.newBuilder()
					.dataPartitionBitSize(Integer.valueOf(parameters.getOrDefault(ResponderProperties.DATA_PARTITION_BIT_SIZE, "8")))
					.hashBitSize(Integer.valueOf(parameters.getOrDefault(ResponderProperties.HASH_BIT_SIZE, "12")))
					.paillierBitSize(Integer.valueOf(parameters.getOrDefault(ResponderProperties.PAILLIER_BIT_SIZE, "384")))
					.certainty(Integer.valueOf(parameters.getOrDefault(ResponderProperties.CERTAINTY, "128")))
					.bitSet(Integer.valueOf(parameters.getOrDefault(ResponderProperties.BIT_SET, "8")))
					.embedSelector(jpaQuery.getEmbedSelector())
					.queryType(jpaQuery.getName())
					.build();

			List<String> selectors = JSONConverter.toList(jpaQuery.getSelectorValues());
			Validate.notNull(selectors, "No selector values, aborting query generation. At least one selector required.");

			logger.info("Generating query with {} selector values", selectors.size());
			UUID queryIdentifier = UUID.randomUUID();
			Querier querier = querierFactory.createQuerier(querySchema, queryIdentifier, selectors, querierProperties);
			logger.info("Finished encrypting query {}.", jpaQuery);
			logger.info("Querier selector value count {}, query exptable size {}", querier.getQueryKey().getSelectors().size(), querier.getQuery().getExpTable().size());
			logger.info("Query expFileBasedLookup size {}", querier.getQuery().getExpFileBasedLookup().size());
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
