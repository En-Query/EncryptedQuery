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

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Decryption;
import org.enquery.encryptedquery.querier.data.entity.json.DecryptionCollectionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DecryptionResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DecryptionStatus;
import org.enquery.encryptedquery.querier.data.service.DecryptionRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = DecryptionStatusResolver.class)
public class DecryptionStatusResolver {

	@Reference
	private ResponseDecipher responseDecipher;
	@Reference
	private DecryptionRepository decryptionRepo;

	public DecryptionCollectionResponse resolve(DecryptionCollectionResponse data) {
		Validate.notNull(data);
		data.getData()
				.stream()
				.forEach(d -> resolve(d));
		return data;
	}

	public DecryptionResponse resolve(DecryptionResponse data) {
		resolve(data.getData());
		return data;
	}

	public org.enquery.encryptedquery.querier.data.entity.json.Decryption resolve(org.enquery.encryptedquery.querier.data.entity.json.Decryption jsonDecryption) {
		Validate.notNull(jsonDecryption);

		final Integer retrievalId = Integer.valueOf(jsonDecryption.getRetrieval().getId());
		if (responseDecipher.isInProgress(retrievalId)) {
			jsonDecryption.setStatus(DecryptionStatus.InProgress);
			return jsonDecryption;
		}

		final Integer decryptionId = Integer.valueOf(jsonDecryption.getId());
		Decryption decryption = decryptionRepo.find(decryptionId);
		Validate.notNull(decryption, "Decryption %d not found", decryptionId);

		if (decryption.getErrorMessage() != null) {
			jsonDecryption.setStatus(DecryptionStatus.Failed);
			return jsonDecryption;
		}

		if (decryption.getPayloadUri() != null) {
			jsonDecryption.setStatus(DecryptionStatus.Complete);
			return jsonDecryption;
		}

		jsonDecryption.setStatus(DecryptionStatus.Pending);
		return jsonDecryption;
	}
}
