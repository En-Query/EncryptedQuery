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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.Decryption;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.json.ResultStatus;
import org.enquery.encryptedquery.querier.data.service.DecryptionRepository;
import org.enquery.encryptedquery.querier.data.service.ResultRepository;
import org.enquery.encryptedquery.querier.data.service.RetrievalRepository;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *
 */
public class ResultStatusResolverTest {

	@Test
	public void noDownloadsNoDecryptions() {
		ResultStatusResolver resolver = new ResultStatusResolver();
		resolver.setResultRepo(mockResultRepo());
		resolver.setRetrievalRepo(mockRetrievalRepo(null));
		resolver.setDecryptionRepo(mockDecryptionRepo(null));
		resolver.setResponseDecipher(mockResponseDecipher(false));

		org.enquery.encryptedquery.querier.data.entity.json.Result jsonResult =
				new org.enquery.encryptedquery.querier.data.entity.json.Result();

		jsonResult.setId("1");
		resolver.resolve(jsonResult);
		assertEquals(ResultStatus.Ready, jsonResult.getStatus());
	}

	@Test
	public void downloadInProgressStatus() {
		Retrieval retrieval = new Retrieval();
		retrieval.setId(1);

		ResultStatusResolver resolver = new ResultStatusResolver();
		resolver.setResultRepo(mockResultRepo());
		resolver.setRetrievalRepo(mockRetrievalRepo(retrieval));
		resolver.setDecryptionRepo(mockDecryptionRepo(null));
		resolver.setResponseDecipher(mockResponseDecipher(false));

		org.enquery.encryptedquery.querier.data.entity.json.Result jsonResult =
				new org.enquery.encryptedquery.querier.data.entity.json.Result();

		jsonResult.setId("1");
		resolver.resolve(jsonResult);
		assertEquals(ResultStatus.Downloading, jsonResult.getStatus());
	}

	@Test
	public void downloadSuccessStatus() {
		Retrieval retrieval = new Retrieval();
		retrieval.setId(1);
		retrieval.setPayloadUri("uri");

		ResultStatusResolver resolver = new ResultStatusResolver();
		resolver.setResultRepo(mockResultRepo());
		resolver.setRetrievalRepo(mockRetrievalRepo(retrieval));
		resolver.setDecryptionRepo(mockDecryptionRepo(null));
		resolver.setResponseDecipher(mockResponseDecipher(false));

		org.enquery.encryptedquery.querier.data.entity.json.Result jsonResult =
				new org.enquery.encryptedquery.querier.data.entity.json.Result();

		jsonResult.setId("1");
		resolver.resolve(jsonResult);
		assertEquals(ResultStatus.Downloaded, jsonResult.getStatus());
	}

	@Test
	public void downloadFailedStatus() {
		Retrieval retrieval = new Retrieval();
		retrieval.setId(1);
		retrieval.setErrorMessage("some error");

		ResultStatusResolver resolver = new ResultStatusResolver();
		resolver.setResultRepo(mockResultRepo());
		resolver.setRetrievalRepo(mockRetrievalRepo(retrieval));
		resolver.setDecryptionRepo(mockDecryptionRepo(null));
		resolver.setResponseDecipher(mockResponseDecipher(false));

		org.enquery.encryptedquery.querier.data.entity.json.Result jsonResult =
				new org.enquery.encryptedquery.querier.data.entity.json.Result();

		jsonResult.setId("1");
		resolver.resolve(jsonResult);
		assertEquals(ResultStatus.Ready, jsonResult.getStatus());
	}

	@Test
	public void decryptionInProgressStatus() {
		Retrieval retrieval = new Retrieval();
		retrieval.setId(1);
		retrieval.setPayloadUri("uri");

		ResultStatusResolver resolver = new ResultStatusResolver();
		resolver.setResultRepo(mockResultRepo());
		resolver.setRetrievalRepo(mockRetrievalRepo(retrieval));
		resolver.setDecryptionRepo(mockDecryptionRepo(null));
		resolver.setResponseDecipher(mockResponseDecipher(true));

		org.enquery.encryptedquery.querier.data.entity.json.Result jsonResult =
				new org.enquery.encryptedquery.querier.data.entity.json.Result();

		jsonResult.setId("1");
		resolver.resolve(jsonResult);
		assertEquals(ResultStatus.Decrypting, jsonResult.getStatus());
	}

	@Test
	public void decryptionSuccessStatus() {
		Retrieval retrieval = new Retrieval();
		retrieval.setId(1);
		retrieval.setPayloadUri("uri");

		Decryption decryption = new Decryption();
		decryption.setId(1);
		decryption.setPayloadUri("uri");

		ResultStatusResolver resolver = new ResultStatusResolver();
		resolver.setResultRepo(mockResultRepo());
		resolver.setRetrievalRepo(mockRetrievalRepo(retrieval));
		resolver.setDecryptionRepo(mockDecryptionRepo(decryption));
		resolver.setResponseDecipher(mockResponseDecipher(false));

		org.enquery.encryptedquery.querier.data.entity.json.Result jsonResult =
				new org.enquery.encryptedquery.querier.data.entity.json.Result();

		jsonResult.setId("1");
		resolver.resolve(jsonResult);
		assertEquals(ResultStatus.Decrypted, jsonResult.getStatus());
	}

	@Test
	public void decryptionErrorStatus() {
		Retrieval retrieval = new Retrieval();
		retrieval.setId(1);
		retrieval.setPayloadUri("uri");

		Decryption decryption = new Decryption();
		decryption.setId(1);
		decryption.setErrorMessage("Some error");

		ResultStatusResolver resolver = new ResultStatusResolver();
		resolver.setResultRepo(mockResultRepo());
		resolver.setRetrievalRepo(mockRetrievalRepo(retrieval));
		resolver.setDecryptionRepo(mockDecryptionRepo(decryption));
		resolver.setResponseDecipher(mockResponseDecipher(false));

		org.enquery.encryptedquery.querier.data.entity.json.Result jsonResult =
				new org.enquery.encryptedquery.querier.data.entity.json.Result();

		jsonResult.setId("1");
		resolver.resolve(jsonResult);
		assertEquals(ResultStatus.Downloaded, jsonResult.getStatus());
	}

	/**
	 * @return
	 */
	private DecryptionRepository mockDecryptionRepo(Decryption decryption) {
		DecryptionRepository result = Mockito.mock(DecryptionRepository.class);

		Collection<Decryption> value = new ArrayList<>();
		if (decryption != null) {
			value.add(decryption);
		}

		Mockito.when(
				result.listForRetrieval(
						Mockito.any(Retrieval.class)))
				.thenReturn(value);

		return result;
	}

	/**
	 * @return
	 */
	private ResponseDecipher mockResponseDecipher(boolean decrypting) {
		ResponseDecipher result = Mockito.mock(ResponseDecipher.class);
		Mockito.when(result.isInProgress(Mockito.anyInt())).thenReturn(decrypting);
		return result;
	}

	/**
	 * @return
	 */
	private RetrievalRepository mockRetrievalRepo(Retrieval retrieval) {
		RetrievalRepository result = Mockito.mock(RetrievalRepository.class);
		Mockito.when(
				result.listForResult(
						Mockito.any(Result.class)))
				.thenAnswer(r -> {
					Collection<Retrieval> retrievalList = new ArrayList<>();
					if (retrieval != null) {
						retrieval.setResult((Result) r.getArguments()[0]);
						retrievalList.add(retrieval);
					}
					return retrievalList;
				});
		return result;
	}

	private static ResultRepository mockResultRepo() {
		ResultRepository result = Mockito.mock(ResultRepository.class);

		Result jpaResult = new Result();
		jpaResult.setId(1);
		jpaResult.setResponderId(1);
		jpaResult.setResponderUri("responder uri");

		Mockito.when(result.find(1)).thenReturn(jpaResult);
		return result;
	}
}
