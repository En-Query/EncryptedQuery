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

public class DataSchema extends Resource {

	public static final String TYPE = "DataSchema";

	@JsonView(Views.ListView.class)
	private String name;
	private String dataSourcesUri;
	private String querySchemasUri;
	private List<DataSchemaField> fields;


	public DataSchema() {
		setType(TYPE);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<DataSchemaField> getFields() {
		return fields;
	}

	public void setFields(List<DataSchemaField> elements) {
		this.fields = elements;
	}

	public String getDataSourcesUri() {
		return dataSourcesUri;
	}

	public void setDataSourcesUri(String dataSourcesUri) {
		this.dataSourcesUri = dataSourcesUri;
	}

	public String getQuerySchemasUri() {
		return querySchemasUri;
	}

	public void setQuerySchemasUri(String querySchemasUri) {
		this.querySchemasUri = querySchemasUri;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("DataSchema [name=").append(name).append(", dataSourcesUri=").append(dataSourcesUri).append(", querySchemasUri=").append(querySchemasUri).append("]");
		return builder.toString();
	}
}
