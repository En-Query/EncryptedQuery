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

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@NamedEntityGraphs({
		@NamedEntityGraph(name = QuerySchema.DATASCHEMA_ENTITY_GRAPH,
				attributeNodes = @NamedAttributeNode("dataSchema")),

		@NamedEntityGraph(name = QuerySchema.FIELDS_ENTITY_GRAPH,
				attributeNodes = @NamedAttributeNode("fields")),

		@NamedEntityGraph(name = QuerySchema.ALL_ENTITY_GRAPH,
				attributeNodes = {@NamedAttributeNode("fields"),
						@NamedAttributeNode("dataSchema")})
})
@Table(name = "queryschemas")
public class QuerySchema {

	public static final String DATASCHEMA_ENTITY_GRAPH = "QuerySchema.dataschema";
	public static final String FIELDS_ENTITY_GRAPH = "QuerySchema.fields";
	public static final String ALL_ENTITY_GRAPH = "QuerySchema.all";

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "id_generator")
	private Integer id;

	@Column(name = "queryschemaname", nullable = false)
	private String name;

	@JoinColumn(name = "dataschema_id", nullable = false)
	@OneToOne()
	private DataSchema dataSchema;

	@Column(name = "selectorfield", nullable = false)
	private String selectorField;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "querySchema", orphanRemoval = true)
	private List<QuerySchemaField> fields = new ArrayList<>();

	public QuerySchema() {}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
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

	public DataSchema getDataSchema() {
		return dataSchema;
	}

	public void setDataSchema(DataSchema dataSchema) {
		this.dataSchema = dataSchema;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("QuerySchema [name=").append(name).append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		QuerySchema other = (QuerySchema) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}
}
