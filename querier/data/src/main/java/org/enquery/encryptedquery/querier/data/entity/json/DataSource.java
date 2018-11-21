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

import org.enquery.encryptedquery.querier.data.entity.DataSourceType;

import com.fasterxml.jackson.annotation.JsonView;

public class DataSource extends Resource {

	public static final String TYPE = "DataSource";

	@JsonView(Views.ListView.class)
	private String name;
	private String description;
	private Resource dataSchema;
	private DataSourceType processingMode;

	public DataSource() {
		setType(TYPE);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public DataSourceType getProcessingMode() {
		return processingMode;
	}

	public void setProcessingMode(DataSourceType processingMode) {
		this.processingMode = processingMode;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Resource getDataSchema() {
		return dataSchema;
	}

	public void setDataSchema(Resource dataSchema) {
		this.dataSchema = dataSchema;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("DataSource [name=").append(name).append(", description=").append(description).append(", dataSchema=").append(dataSchema.id).append(", processingMode=").append(processingMode).append("]");
		return builder.toString();
	}


}
