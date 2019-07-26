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
package org.enquery.encryptedquery.responder.data.service;

import java.util.HashMap;
import java.util.Map;

import org.enquery.encryptedquery.responder.data.transformation.URIUtils;

/**
 *
 */
public class BaseResourceUriRegistryImpl implements ResourceUriRegistry {

	public static final String DATASCHEMA = "dataschema";
	public static final String DATASOURCE = "datasource";

	private final Map<String, String> registry = new HashMap<>();
	private String baseUri;

	/**
	 * 
	 */
	public BaseResourceUriRegistryImpl() {}

	/**
	 * @return the baseUri
	 */
	public String getBaseUri() {
		return baseUri;
	}

	/**
	 * @param baseUri the baseUri to set
	 */
	public void setBaseUri(String baseUri) {
		this.baseUri = baseUri;
		registry.put(DATASCHEMA, URIUtils.concat(baseUri, "dataschemas").toString());
	}

	private String findByName(String name) {
		return registry.get(name);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.enquery.encryptedquery.responder.data.service.RestServiceRegistry#dataSchemaUri()
	 */
	@Override
	public String dataSchemaUri() {
		return findByName(DATASCHEMA).toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.data.service.RestServiceRegistry#dataSchemaUri(java.lang
	 * .Integer)
	 */
	@Override
	public String dataSchemaUri(Integer dataSchemaId) {
		return URIUtils.concat(findByName(DATASCHEMA), dataSchemaId).toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.data.service.RestServiceRegistry#dataSourceUri(java.lang
	 * .Integer)
	 */
	@Override
	public String dataSourceUri(Integer dataSchemaId) {
		return URIUtils.concat(dataSchemaUri(dataSchemaId), "datasources").toString();
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.data.service.RestServiceRegistry#dataSourceUri(java.lang
	 * .Integer, java.lang.Integer)
	 */
	@Override
	public String dataSourceUri(Integer dataSchemaId, Integer dataSourceId) {
		return URIUtils.concat(dataSourceUri(dataSchemaId), dataSourceId).toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.data.service.RestServiceRegistry#executionsUri(java.lang
	 * .Integer, java.lang.Integer)
	 */
	@Override
	public String executionsUri(Integer dataSchemaId, Integer dataSourceId) {
		return URIUtils.concat(dataSourceUri(dataSchemaId, dataSourceId), "executions").toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.data.service.RestServiceRegistry#executionUri(java.lang.
	 * Integer, java.lang.Integer, java.lang.Integer)
	 */
	@Override
	public String executionUri(Integer dataSchemaId, Integer dataSourceId, Integer executionId) {
		return URIUtils.concat(executionsUri(dataSchemaId, dataSourceId), executionId).toString();
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.data.service.RestServiceRegistry#resultsUri(java.lang.
	 * Integer, java.lang.Integer, java.lang.Integer)
	 */
	@Override
	public String resultsUri(Integer dataSchemaId, Integer dataSourceId, Integer executionId) {
		return URIUtils.concat(executionUri(dataSchemaId, dataSourceId, executionId), "results").toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.enquery.encryptedquery.responder.data.service.RestServiceRegistry#resultUri(java.lang.
	 * Integer, java.lang.Integer, java.lang.Integer, java.lang.Integer)
	 */
	@Override
	public String resultUri(Integer dataSchemaId, Integer dataSourceId, Integer executionId, Integer resultId) {
		return URIUtils.concat(resultsUri(dataSchemaId, dataSourceId, executionId), resultId).toString();
	}

}
