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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.data.QueryInfo;
import org.enquery.encryptedquery.data.QueryKey;
import org.enquery.encryptedquery.encryption.CipherText;
import org.enquery.encryptedquery.encryption.CryptoScheme;
import org.enquery.encryptedquery.encryption.PlainText;
import org.enquery.encryptedquery.utils.PIRException;

/**
 * Decrypt a single line from the response file.
 *
 */
public class DecryptResponseLineTask implements Callable<ClearTextQueryResponse> {

	private ExecutorService executionService;
	private Map<Integer, CipherText> responseLine;
	private QueryKey queryKey;
	private QueryInfo queryInfo;
	private List<PlainText> rElements;
	private CryptoScheme crypto;

	public ExecutorService getExecutionService() {
		return executionService;
	}

	public void setExecutionService(ExecutorService executionService) {
		this.executionService = executionService;
	}

	public Map<Integer, CipherText> getResponseLine() {
		return responseLine;
	}

	public void setResponseLine(Map<Integer, CipherText> responseLine) {
		this.responseLine = responseLine;
	}

	public QueryInfo getQueryInfo() {
		return queryInfo;
	}

	public void setQueryInfo(QueryInfo queryInfo) {
		this.queryInfo = queryInfo;
	}

	@Override
	public ClearTextQueryResponse call() throws Exception {

		Validate.notNull(executionService);
		Validate.notNull(responseLine);
		Validate.notNull(queryKey);
		Validate.notNull(queryInfo);
		Validate.notNull(crypto);

		rElements = decryptElements();
		// selectorMaskMap = initializeSelectorMasks();

		ClearTextQueryResponse result = new ClearTextQueryResponse(queryInfo.getQueryName(), queryInfo.getIdentifier());

		CompletionService<ClearTextQueryResponse.Selector> completionService = new ExecutorCompletionService<>(executionService);
		int pending = 0;
		for (int i = 0; i < queryKey.getSelectors().size(); ++i) {
			completionService.submit(make(i));
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

		return result;
	}

	private DecryptResponseSelectorTask make(int i) {
		DecryptResponseSelectorTask t = new DecryptResponseSelectorTask(rElements,
				queryKey.getSelectors().get(i),
				i,
				queryInfo,
				queryKey.getEmbedSelectorMap(),
				crypto);
		return t;
	}

	// Method to perform basic decryption of each raw response element - does not
	// extract and reconstruct the data elements
	private List<PlainText> decryptElements() {
		return crypto
				.decrypt(queryKey.getKeyPair(), responseLine.values().stream())
				.collect(Collectors.toList());
	}

	public QueryKey getQueryKey() {
		return queryKey;
	}

	public void setQueryKey(QueryKey queryKey) {
		this.queryKey = queryKey;
	}

	public CryptoScheme getCrypto() {
		return crypto;
	}

	public void setCrypto(CryptoScheme crypto) {
		this.crypto = crypto;
	}

}
