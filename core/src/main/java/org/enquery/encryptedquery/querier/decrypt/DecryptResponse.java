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

package org.enquery.encryptedquery.querier.decrypt;

import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.QueryKey;
import org.enquery.encryptedquery.data.Response;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.CryptoSchemeRegistry;
import org.enquery.encryptedquery.utils.PIRException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decrypt a Response
 */
@Component(service = DecryptResponse.class)
public class DecryptResponse {
	private static final Logger log = LoggerFactory.getLogger(DecryptResponse.class);

	@Reference
	private ExecutorService executionService;
	@Reference
	private CryptoSchemeRegistry cryptoRegistry;

	public ExecutorService getExecutionService() {
		return executionService;
	}

	public void setExecutionService(ExecutorService executionService) {
		this.executionService = executionService;
	}

	public CryptoSchemeRegistry getCryptoRegistry() {
		return cryptoRegistry;
	}

	public void setCryptoRegistry(CryptoSchemeRegistry cryptoRegistry) {
		this.cryptoRegistry = cryptoRegistry;
	}

	public ClearTextQueryResponse decrypt(Response response, QueryKey queryKey) throws InterruptedException, PIRException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		long startTime = System.currentTimeMillis();

		Validate.notNull(response);
		Validate.notNull(queryKey);
		Validate.notNull(executionService);

		final QueryInfo queryInfo = response.getQueryInfo();
		final ClearTextQueryResponse result = new ClearTextQueryResponse(queryInfo.getQueryName(), queryInfo.getIdentifier());
		final CryptoScheme crypto = cryptoRegistry.cryptoSchemeByName(queryInfo.getCryptoSchemeId());
		Validate.notNull(crypto, "CryptoScheme not found for id: %s", queryInfo.getCryptoSchemeId());

		CompletionService<ClearTextQueryResponse> completionService = new ExecutorCompletionService<>(executionService);
		int pending = 0;
		for (final Map<Integer, CipherText> responseLine : response.getResponseElements()) {
			DecryptResponseLineTask task = make(response, queryKey, responseLine, crypto);
			completionService.submit(task);
			++pending;
		}
		try {
			while (pending > 0) {
				result.add(completionService.take().get());
				--pending;
			}
		} catch (ExecutionException e) {
			throw new PIRException("Exception in decryption threads.", e);
		}

		log.info("Decrypted response in {} ms", (System.currentTimeMillis() - startTime));
		return result;
	}

	private DecryptResponseLineTask make(Response response, QueryKey queryKey, Map<Integer, CipherText> responseLine, CryptoScheme crypto) {
		DecryptResponseLineTask task = new DecryptResponseLineTask();
		task.setExecutionService(executionService);
		task.setResponseLine(responseLine);
		task.setQueryKey(queryKey);
		task.setQueryInfo(response.getQueryInfo());
		task.setCrypto(crypto);
		return task;
	}

}
