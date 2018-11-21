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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonView;

public class QuerySchema extends Resource {

	public static final String TYPE = "QuerySchema";

	@JsonView(Views.ListView.class)
	private String name;
	private String selectorField;
	private List<QuerySchemaField> fields;
	private Resource dataSchema;
	private String queriesUri;

	public QuerySchema() {
		setType(TYPE);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<QuerySchemaField> getFields() {
		return fields;
	}

	public void setFields(List<QuerySchemaField> fields) {
		this.fields = fields;
	}


	public void setSelectorField(String selectorField) {
		this.selectorField = selectorField;
	}

	public String getSelectorField() {
		return selectorField;
	}

	public String getQueriesUri() {
		return queriesUri;
	}

	public void setQueriesUri(String queriesUri) {
		this.queriesUri = queriesUri;
	}

	public Resource getDataSchema() {
		return dataSchema;
	}

	public void setDataSchema(Resource dataSchema) {
		this.dataSchema = dataSchema;
	}

}
