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

import java.util.Collection;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Decryption;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.json.ResultCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ResultResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ResultStatus;
import org.enquery.encryptedquery.querier.data.service.DecryptionRepository;
import org.enquery.encryptedquery.querier.data.service.ResultRepository;
import org.enquery.encryptedquery.querier.data.service.RetrievalRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = ResultStatusResolver.class)
public class ResultStatusResolver {

	@Reference
	private ResultRepository resultRepo;
	@Reference
	private RetrievalRepository retrievalRepo;
	@Reference
	private ResponseDecipher responseDecipher;
	@Reference
	private DecryptionRepository decryptionRepo;

	public ResultResponse resolve(ResultResponse json) {
		resolve(json.getData());
		return json;
	}


	ResultRepository getResultRepo() {
		return resultRepo;
	}


	void setResultRepo(ResultRepository resultRepo) {
		this.resultRepo = resultRepo;
	}


	RetrievalRepository getRetrievalRepo() {
		return retrievalRepo;
	}


	void setRetrievalRepo(RetrievalRepository retrievalRepo) {
		this.retrievalRepo = retrievalRepo;
	}


	ResponseDecipher getResponseDecipher() {
		return responseDecipher;
	}


	void setResponseDecipher(ResponseDecipher responseDecipher) {
		this.responseDecipher = responseDecipher;
	}


	DecryptionRepository getDecryptionRepo() {
		return decryptionRepo;
	}


	void setDecryptionRepo(DecryptionRepository decryptionRepo) {
		this.decryptionRepo = decryptionRepo;
	}


	public ResultCollectionResponse resolve(ResultCollectionResponse json) {
		Validate.notNull(json);
		json.getData()
				.stream()
				.forEach(r -> {
					resolve(r);
				});
		return json;
	}

	public org.enquery.encryptedquery.querier.data.entity.json.Result resolve(org.enquery.encryptedquery.querier.data.entity.json.Result jsonResult) {
		Validate.notNull(jsonResult);

		final Integer id = Integer.valueOf(jsonResult.getId());
		final Result result = resultRepo.find(id);
		Validate.notNull(result, "Result %d not found", id);

		final Collection<Retrieval> retrievals = retrievalRepo.listForResult(result);

		if (retrievals.isEmpty()) {
			jsonResult.setStatus(ResultStatus.Ready);
			return jsonResult;
		}

		boolean decrypting = retrievals
				.stream()
				.anyMatch(r -> responseDecipher.isInProgress(r.getId()));

		if (decrypting) {
			jsonResult.setStatus(ResultStatus.Decrypting);
			return jsonResult;
		}

		boolean downloading = retrievals
				.stream()
				.allMatch(r -> r.getPayloadUri() == null && r.getErrorMessage() == null);

		if (downloading) {
			jsonResult.setStatus(ResultStatus.Downloading);
			return jsonResult;
		}

		boolean decrypted = retrievals
				.stream()
				.anyMatch(r -> {
					Collection<Decryption> decryptions = decryptionRepo.listForRetrieval(r);
					if (decryptions.isEmpty()) return false;

					return decryptions
							.stream()
							.allMatch(dec -> dec.getErrorMessage() == null && dec.getPayloadUri() != null);
				});

		if (decrypted) {
			jsonResult.setStatus(ResultStatus.Decrypted);
			return jsonResult;
		}

		boolean downloaded = retrievals
				.stream()
				.anyMatch(r -> r.getPayloadUri() != null
						&& r.getErrorMessage() == null);

		if (downloaded) {
			jsonResult.setStatus(ResultStatus.Downloaded);
			return jsonResult;
		}

		jsonResult.setStatus(ResultStatus.Ready);
		return jsonResult;
	}
}
