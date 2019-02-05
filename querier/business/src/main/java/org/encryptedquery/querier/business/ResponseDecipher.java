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

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.QueryKey;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.querier.data.service.ResultRepository;
import org.enquery.encryptedquery.querier.data.service.RetrievalRepository;
import org.enquery.encryptedquery.querier.decrypt.DecryptResponse;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.xml.transformation.QueryKeyTypeConverter;
import org.enquery.encryptedquery.xml.transformation.ResponseTypeConverter;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * OSGi Service that deciphers responses. Thread-safe.
 *
 */
@Component(service = ResponseDecipher.class)
public class ResponseDecipher {

	private final Logger log = LoggerFactory.getLogger(ResponseDecipher.class);
	private final ConcurrentMap<Integer, Instant> inProgess = new ConcurrentHashMap<>(16);

	@Reference
	private RetrievalRepository retrievalRepo;
	@Reference
	private ResultRepository resultRepo;
	@Reference
	private QueryRepository queryRepo;
	@Reference
	private ExecutorService threadPool;
	@Reference
	private ResponseTypeConverter responseConverter;
	@Reference
	private QueryKeyTypeConverter queryKeyConverter;
	@Reference
	private DecryptResponse responseDecrypter;

	public ClearTextQueryResponse run(org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval jpaRetrieval)
			throws IOException, JAXBException, InterruptedException, PIRException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		Validate.notNull(jpaRetrieval);
		Instant alreadyEncryptingSince = null;
		try {
			alreadyEncryptingSince = inProgess.putIfAbsent(jpaRetrieval.getId(), Instant.now());
			if (alreadyEncryptingSince != null) {
				log.warn("Retrieval {} is currently being decipherer since {}. Ignoring,", jpaRetrieval,
						alreadyEncryptingSince);
				return null;
			}

			log.info("Starting to decipher {}.", jpaRetrieval.getId());

			Response response = loadResponse(jpaRetrieval);
			QueryKey queryKey = loadQueryKey(jpaRetrieval);

			ClearTextQueryResponse result = responseDecrypter.decrypt(response, queryKey);
//			log.info("Finished deciphering  {}. Result: {}", jpaRetrieval.getId(), result);
			log.info("Finished deciphering  {}.", jpaRetrieval.getId());
			return result;
		} finally {
			if (alreadyEncryptingSince == null)
				inProgess.remove(jpaRetrieval.getId());
		}
	}

	private QueryKey loadQueryKey(Retrieval jpaRetrieval) throws IOException, JAXBException {
		final Integer queryId = jpaRetrieval.getResult().getSchedule().getQuery().getId();

		try (InputStream inputStream = queryRepo.loadQueryKeyBytes(queryId)) {
			return queryKeyConverter.toCore(
					queryKeyConverter.unmarshal(inputStream));
		}
	}

	private Response loadResponse(Retrieval jpaRetrieval) throws IOException, JAXBException {
		org.enquery.encryptedquery.xml.schema.Response xml;
		try (InputStream is = retrievalRepo.payloadInputStream(jpaRetrieval)) {
			xml = responseConverter.unmarshal(is);
		}
		return responseConverter.toCore(xml);
	}

	public boolean isInProgress(int retrievalId) {
		return inProgess.containsKey(retrievalId);
	}
}
