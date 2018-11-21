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
package org.enquery.encryptedquery.querier.data.entity.json;

import org.enquery.encryptedquery.querier.data.entity.RetrievalStatus;

import com.fasterxml.jackson.annotation.JsonView;

public class Retrieval extends Resource {

	public static final String TYPE = "Retrieval";

	@JsonView(Views.ListView.class)
	private RetrievalStatus status;
	private Resource result;
	private String decryptionsUri;

	public Retrieval() {
		setType(TYPE);
	}

	public RetrievalStatus getStatus() {
		return status;
	}

	public void setStatus(RetrievalStatus status) {
		this.status = status;
	}

	/**
	 * @return the result
	 */
	public Resource getResult() {
		return result;
	}

	/**
	 * @param result the result to set
	 */
	public void setResult(Resource result) {
		this.result = result;
	}

	/**
	 * @return the decryptionsUri
	 */
	public String getDecryptionsUri() {
		return decryptionsUri;
	}

	/**
	 * @param decryptionsUri the decryptionsUri to set
	 */
	public void setDecryptionsUri(String decryptionsUri) {
		this.decryptionsUri = decryptionsUri;
	}

}
