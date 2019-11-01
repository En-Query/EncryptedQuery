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
package org.enquery.encryptedquery.querier.data.entity.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;
import javax.persistence.NamedSubgraph;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "queries",
		uniqueConstraints = {
				@UniqueConstraint(columnNames = {
						"queryschema_id", "name"
				})})

@NamedEntityGraphs({
		@NamedEntityGraph(name = Query.ALL_ENTITY_GRAPH,
				attributeNodes = {
						@NamedAttributeNode(value = "querySchema", subgraph = "querySchema")
				},
				subgraphs = @NamedSubgraph(
						name = "querySchema",
						type = QuerySchema.class,
						attributeNodes = @NamedAttributeNode("fields")))
})
public class Query {

	public static final String ALL_ENTITY_GRAPH = "Query.all";

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "sequences")
	private Integer id;

	@Column(nullable = false)
	private String name;

	@OneToOne()
	@JoinColumn(name = "queryschema_id", nullable = false)
	private QuerySchema querySchema;

	@Column(name = "parameters")
	@Lob
	private String parameters;

	@Column(name = "selector_values")
	@Lob
	private String selectorValues;

	@Column(name = "query_url")
	private String queryUrl;

	@Column(name = "query_key_url")
	private String queryKeyUrl;

	@Column(name = "error_msg")
	private String errorMessage;

	@Column(name = "filter_expression")
	private String filterExpression;

	public Query() {}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getQueryUrl() {
		return queryUrl;
	}

	public void setQueryUrl(String queryUrl) {
		this.queryUrl = queryUrl;
	}

	public String getQueryKeyUrl() {
		return queryKeyUrl;
	}

	public void setQueryKeyUrl(String queryKeysUrl) {
		this.queryKeyUrl = queryKeysUrl;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public QuerySchema getQuerySchema() {
		return querySchema;
	}

	public void setQuerySchema(QuerySchema querySchema) {
		this.querySchema = querySchema;
	}

	public String getParameters() {
		return parameters;
	}

	public void setParameters(String parameters) {
		this.parameters = parameters;
	}

	public void setSelectorValues(String selectorValues) {
		this.selectorValues = selectorValues;
	}

	public String getSelectorValues() {
		return selectorValues;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getFilterExpression() {
		return filterExpression;
	}

	public void setFilterExpression(String filterExpression) {
		this.filterExpression = filterExpression;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Query other = (Query) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Query [id=").append(id).append(", name=").append(name).append(", querySchema=").append(querySchema).append("]");
		return builder.toString();
	}
}
