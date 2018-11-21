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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
import org.enquery.encryptedquery.querier.wideskies.encrypt.Querier;
import org.enquery.encryptedquery.querier.wideskies.encrypt.QueryKey;
import org.enquery.encryptedquery.xml.transformation.QueryKeyTypeConverter;
import org.enquery.encryptedquery.xml.transformation.QueryTypeConverter;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = QueryUpdater.class)
public class QueryUpdater {

	@Reference
	private QueryRepository queryRepo;
	@Reference
	private ExecutorService threadPool;
	private QueryTypeConverter queryTypeConverter;
	private QueryKeyTypeConverter queryKeyTypeConverter;

	@Activate
	void activate() {
		queryTypeConverter = new QueryTypeConverter();
		queryKeyTypeConverter = new QueryKeyTypeConverter();
	}

	/**
	 * Update query error message from the passed Exception. This error could be during encryption.
	 * 
	 * @param query
	 * @param ex
	 * @return
	 * @throws IOException
	 */
	public Query updateWithError(Query query, Exception ex) throws IOException {
		return queryRepo.updateWithError(query, ex);
	}

	/**
	 * Save encrypted query and its encryption key
	 * 
	 * @param queryId
	 * @param querier
	 * @return
	 * @throws IOException
	 * @throws JAXBException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public Query update(int queryId, Querier querier) throws ExecutionException, InterruptedException {
		Validate.notNull(querier);

		// save both the query and key concurrently
		Future<Query> saveKeyFuture = threadPool.submit(() -> saveKey(queryId, querier.getQueryKey()));
		Future<Query> saveBytesFuture = threadPool.submit(() -> saveQueryBytes(queryId, querier.getQuery()));

		// make sure both futures are complete before returning
		Validate.notNull(saveBytesFuture.get());
		Validate.notNull(saveKeyFuture.get());

		// refresh query before returning
		Query result = queryRepo.find(queryId);

		// clear any error message if we are successful saving encrypted query
		if (result.getErrorMessage() != null) {
			result.setErrorMessage(null);
			result = queryRepo.update(result);
		}

		return result;
	}

	/**
	 * Saves the encrypted Query as XML
	 * 
	 * @param jpaQuery
	 * @param coreQuery
	 * @return
	 * @throws JAXBException
	 * @throws IOException
	 */
	private Query saveQueryBytes(int queryId, org.enquery.encryptedquery.query.wideskies.Query coreQuery) {
		org.enquery.encryptedquery.xml.schema.Query xml =
				queryTypeConverter.toXMLQuery(coreQuery);

		try (PipedOutputStream out = new PipedOutputStream();
				PipedInputStream in = new PipedInputStream(out);) {

			threadPool.submit(() -> {
				try {
					queryTypeConverter.marshal(xml, out);
					out.close();
				} catch (JAXBException | IOException e) {
					throw new RuntimeException("Error marshaling query.", e);
				}
			});

			return queryRepo.updateQueryBytes(queryId, in);
		} catch (IOException e) {
			throw new RuntimeException("Error saving query.", e);
		}
	}

	/**
	 * Saves the encryption key as XML
	 * 
	 * @param query
	 * @param queryKey
	 * @return
	 * @throws JAXBException
	 * @throws IOException
	 */
	@SuppressWarnings("static-access")
	private Query saveKey(int queryId, QueryKey queryKey) {
		try {
			org.enquery.encryptedquery.xml.schema.QueryKey xml =
					queryKeyTypeConverter.toXMLQueryKey(queryKey);

			try (PipedOutputStream out = new PipedOutputStream();
					PipedInputStream in = new PipedInputStream(out);) {

				threadPool.submit(() -> {
					try {
						queryKeyTypeConverter.marshal(xml, out);
						out.close();
					} catch (JAXBException | IOException e) {
						throw new RuntimeException("Error marshaling query key.", e);
					}
				});

				return queryRepo.updateQueryKeyBytes(queryId, in);
			}

		} catch (IOException e) {
			throw new RuntimeException("Error saving query key.", e);
		}
	}

}
