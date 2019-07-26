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
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "queryschemafields")
public class QuerySchemaField {

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "sequences")
	private Integer id;

	@ManyToOne()
	@JoinColumn(name = "queryschema_id")
	private QuerySchema querySchema;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "max_size")
	private Integer maxSize;

	@Column(name = "max_array_elements")
	private Integer maxArrayElements;

	public QuerySchemaField() {}

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

	public Integer getMaxSize() {
		return maxSize;
	}

	public void setMaxSize(Integer maxsize) {
		this.maxSize = maxsize;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getMaxArrayElements() {
		return maxArrayElements;
	}

	public void setMaxArrayElements(Integer maxArrayElements) {
		this.maxArrayElements = maxArrayElements;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
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
		QuerySchemaField other = (QuerySchemaField) obj;
		if (id != other.id) {
			return false;
		}
		return true;
	}

}
