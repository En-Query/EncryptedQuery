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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonView;

public class Query extends Resource {

	public static final String TYPE = "Query";
	@JsonView(Views.ListView.class)
	private String name;
	@JsonView(Views.ListView.class)
	private QueryStatus status;
	private Resource querySchema;
	private String schedulesUri;
	private Map<String, String> parameters;
	private List<String> selectorValues;
	private String filterExpression;

	public Query() {
		setType(TYPE);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public QueryStatus getStatus() {
		return status;
	}

	public void setStatus(QueryStatus status) {
		this.status = status;
	}

	public String getSchedulesUri() {
		return schedulesUri;
	}

	public void setSchedulesUri(String schedulesUri) {
		this.schedulesUri = schedulesUri;
	}

	public Map<String, String> getParameters() {
		return parameters;
	}

	public void setParameters(Map<String, String> parameters) {
		this.parameters = parameters;
	}

	public List<String> getSelectorValues() {
		return selectorValues;
	}

	public void setSelectorValues(List<String> selectorValues) {
		this.selectorValues = selectorValues;
	}

	public Resource getQuerySchema() {
		return querySchema;
	}

	public void setQuerySchema(Resource querySchema) {
		this.querySchema = querySchema;
	}

	public String getFilterExpression() {
		return filterExpression;
	}

	public void setFilterExpression(String filterExpression) {
		this.filterExpression = filterExpression;
	}

	@Override
	public String toString() {
		final int maxLen = 10;
		StringBuilder builder = new StringBuilder();
		builder.append("Query [name=")
				.append(name)
				.append(", status=").append(status)
				.append(", querySchema=").append(querySchema)
				.append(", schedulesUri=").append(schedulesUri)
				.append(", filterExpression=").append(filterExpression)
				.append(", parameters=")
				.append(parameters != null ? toString(parameters.entrySet(), maxLen) : null).append(", selectorValues=").append(selectorValues != null ? toString(selectorValues, maxLen) : null).append("]");
		return builder.toString();
	}

	private String toString(Collection<?> collection, int maxLen) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		int i = 0;
		for (Iterator<?> iterator = collection.iterator(); iterator.hasNext() && i < maxLen; i++) {
			if (i > 0) builder.append(", ");
			builder.append(iterator.next());
		}
		builder.append("]");
		return builder.toString();
	}


}
