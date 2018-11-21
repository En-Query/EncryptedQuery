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
import java.util.TreeMap;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.ClearTextQueryResponse;
import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.encryption.PrimeGenerator;
import org.enquery.encryptedquery.querier.wideskies.encrypt.QueryKey;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.utils.PIRException;
import org.enquery.encryptedquery.utils.RandomProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decrypt a Response
 */
public class DecryptResponse {
	private static final Logger log = LoggerFactory.getLogger(DecryptResponse.class);

	private ModPowAbstraction modPowAbstraction;
	private PrimeGenerator primeGenerator;
	private RandomProvider randomProvider;

	private String modPowAbstractionClassName;
	private String primeGeneratorClassName;
	private String randomProviderClassName;
	private ExecutorService executionService;


	public ExecutorService getExecutionService() {
		return executionService;
	}

	public void setExecutionService(ExecutorService executionService) {
		this.executionService = executionService;
	}

	@SuppressWarnings("unchecked")
	public void activate() throws InstantiationException, IllegalAccessException, ClassNotFoundException {

		Validate.notNull(executionService);

		modPowAbstractionClassName = "org.enquery.encryptedquery.encryption.impl.ModPowAbstractionJavaImpl";
		log.info("Mod Power Clsas {}", modPowAbstractionClassName);

		Class<ModPowAbstraction> modPowClass = (Class<ModPowAbstraction>) Class.forName(modPowAbstractionClassName);
		modPowAbstraction = modPowClass.newInstance();

		randomProviderClassName = "org.enquery.encryptedquery.utils.RandomProvider";
		log.info("Random Provider Clsas {}", randomProviderClassName);

		Class<RandomProvider> randomProviderClass = (Class<RandomProvider>) Class.forName(randomProviderClassName);
		randomProvider = randomProviderClass.newInstance();

		primeGeneratorClassName = "org.enquery.encryptedquery.encryption.PrimeGenerator";
		log.info("Prime Generator Class {}", primeGeneratorClassName);

		Class<PrimeGenerator> primeGeneratorClass = (Class<PrimeGenerator>) Class.forName(primeGeneratorClassName);
		primeGenerator = primeGeneratorClass.newInstance();

	}

	public ClearTextQueryResponse decrypt(ExecutorService es, Response response, QueryKey queryKey) throws InterruptedException, PIRException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		executionService = es;
		activate();
		return decrypt(response, queryKey);
	}

	/**
	 * Method to decrypt the response elements and reconstructs the data elements
	 * <p>
	 * Each element of response.getResponseElements() is an encrypted column vector E(Y_i)
	 * <p>
	 * To decrypt and recover data elements:
	 * <p>
	 * (1) Decrypt E(Y_i) to yield
	 * <p>
	 * Y_i, where Y_i = \sum_{j = 0}^{numSelectors} 2^{j*dataPartitionBitSize} D_j
	 * <p>
	 * such that D_j is dataPartitionBitSize-many bits of data corresponding to selector_k for j =
	 * H_k(selector_k), for some 0 <= k < numSelectors
	 * <p>
	 * (2) Reassemble data elements across columns where, hit r for selector_k, D^k_r, is such that
	 * <p>
	 * D^k_r = D^k_r,0 || D^k_r,1 || ... || D^k_r,(numPartitionsPerDataElement - 1)
	 * <p>
	 * where D^k_r,l = Y_{r*numPartitionsPerDataElement + l} & (2^{r*numPartitionsPerDataElement} *
	 * (2^numBitsPerDataElement - 1))
	 * 
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 *
	 */
	public ClearTextQueryResponse decrypt(Response response, QueryKey queryKey) throws InterruptedException, PIRException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		long startTime = System.currentTimeMillis();

		Validate.notNull(response);
		Validate.notNull(queryKey);
		Validate.notNull(executionService);

		final QueryInfo queryInfo = response.getQueryInfo();
		final ClearTextQueryResponse result = new ClearTextQueryResponse(queryInfo.getQueryType(), queryInfo.getIdentifier().toString());

		CompletionService<ClearTextQueryResponse> completionService = new ExecutorCompletionService<>(executionService);
		int pending = 0;
		for (final TreeMap<Integer, BigInteger> responseLine : response.getResponseElements()) {
			DecryptResponseLineTask task = make(response, queryKey, responseLine);
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

	private DecryptResponseLineTask make(Response response, QueryKey queryKey, TreeMap<Integer, BigInteger> responseLine) {
		DecryptResponseLineTask task = new DecryptResponseLineTask();
		task.setExecutionService(executionService);
		task.setResponseLine(responseLine);
		task.setQueryKey(queryKey);
		task.setQueryInfo(response.getQueryInfo());
		task.setModPowAbstraction(modPowAbstraction);
		task.setPrimeGenerator(primeGenerator);
		task.setRandomProvider(randomProvider);
		return task;
	}

}
