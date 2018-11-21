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
package org.enquery.encryptedquery.querier.wideskies.decrypt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.encryption.PaillierEncryption;
import org.enquery.encryptedquery.encryption.PrimeGenerator;
import org.enquery.encryptedquery.querier.wideskies.encrypt.QueryKey;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;

/**
 * Decrypt a single line from the response file.
 *
 */
public class DecryptResponseLineTask implements Callable<ClearTextQueryResponse> {

	private static final BigInteger TWO_BI = BigInteger.valueOf(2);

	private ExecutorService executionService;
	private TreeMap<Integer, BigInteger> responseLine;
	private QueryKey queryKey;
	private QueryInfo queryInfo;
	private ModPowAbstraction modPowAbstraction;
	private PrimeGenerator primeGenerator;
	private RandomProvider randomProvider;

	private List<BigInteger> rElements;

	private Map<String, BigInteger> selectorMaskMap;

	public ExecutorService getExecutionService() {
		return executionService;
	}

	public void setExecutionService(ExecutorService executionService) {
		this.executionService = executionService;
	}


	public TreeMap<Integer, BigInteger> getResponseLine() {
		return responseLine;
	}

	public void setResponseLine(TreeMap<Integer, BigInteger> responseLine) {
		this.responseLine = responseLine;
	}

	public QueryKey getQueryKey() {
		return queryKey;
	}

	public void setQueryKey(QueryKey queryKey) {
		this.queryKey = queryKey;
	}

	public QueryInfo getQueryInfo() {
		return queryInfo;
	}

	public void setQueryInfo(QueryInfo queryInfo) {
		this.queryInfo = queryInfo;
	}

	public ModPowAbstraction getModPowAbstraction() {
		return modPowAbstraction;
	}

	public void setModPowAbstraction(ModPowAbstraction modPowAbstraction) {
		this.modPowAbstraction = modPowAbstraction;
	}

	public PrimeGenerator getPrimeGenerator() {
		return primeGenerator;
	}

	public void setPrimeGenerator(PrimeGenerator primeGenerator) {
		this.primeGenerator = primeGenerator;
	}

	public RandomProvider getRandomProvider() {
		return randomProvider;
	}

	public void setRandomProvider(RandomProvider randomProvider) {
		this.randomProvider = randomProvider;
	}

	@Override
	public ClearTextQueryResponse call() throws Exception {

		Validate.notNull(executionService);
		Validate.notNull(responseLine);
		Validate.notNull(queryKey);
		Validate.notNull(queryInfo);
		Validate.notNull(modPowAbstraction);
		Validate.notNull(primeGenerator);
		Validate.notNull(randomProvider);

		rElements = decryptElements();
		selectorMaskMap = initializeSelectorMasks();

		ClearTextQueryResponse result = new ClearTextQueryResponse(queryInfo.getQueryType(), queryInfo.getIdentifier().toString());

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
				selectorMaskMap,
				queryInfo,
				queryKey.getEmbedSelectorMap());
		return t;
	}

	private Map<String, BigInteger> initializeSelectorMasks() {
		final int dataPartitionBitSize = queryInfo.getDataPartitionBitSize();
		final Map<String, BigInteger> result = new HashMap<>();
		int selectorNum = 0;
		for (final String selector : queryKey.getSelectors()) {
			// 2^{selectorNum*dataPartitionBitSize}(2^{dataPartitionBitSize} - 1)
			final BigInteger mask = TWO_BI.pow(selectorNum * dataPartitionBitSize).multiply((TWO_BI.pow(dataPartitionBitSize).subtract(BigInteger.ONE)));
			// logger.info("selector = " + selector + " mask = " + mask.toString(2));
			result.put(selector, mask);
			++selectorNum;
		}
		return result;
	}

	// Method to perform basic decryption of each raw response element - does not
	// extract and reconstruct the data elements
	private List<BigInteger> decryptElements() {
		final List<BigInteger> result = new ArrayList<>();
		final PaillierEncryption paillierEncryption = new PaillierEncryption(modPowAbstraction, primeGenerator, randomProvider);
		for (final BigInteger encElement : responseLine.values()) {
			result.add(paillierEncryption.decrypt(queryKey.getPaillier(), encElement));
		}
		return result;
	}

}
