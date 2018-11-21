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
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.enquery.encryptedquery.querier.data.entity.DataSourceType;

@Entity
@Table(name = "datasources")
public class DataSource {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "id_generator")
	private Integer id;

	@Column(unique = true, nullable = false)
	private String name;

	@Column(nullable = false)
	private String description;

	@JoinColumn(name = "dataschema_id", nullable = false)
	@OneToOne(fetch = FetchType.EAGER)
	private DataSchema dataSchema;

	@Column(name = "responder_uri", nullable = false)
	private String responderUri;

	@Column(name = "executions_uri", nullable = false)
	private String executionsUri;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private DataSourceType type;

	public DataSource() {}

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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public DataSchema getDataSchema() {
		return dataSchema;
	}

	public void setDataSchema(DataSchema dataSchema) {
		this.dataSchema = dataSchema;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dataSchema == null) ? 0 : dataSchema.hashCode());
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
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
		DataSource other = (DataSource) obj;
		if (dataSchema == null) {
			if (other.dataSchema != null) {
				return false;
			}
		} else if (!dataSchema.equals(other.dataSchema)) {
			return false;
		}
		if (description == null) {
			if (other.description != null) {
				return false;
			}
		} else if (!description.equals(other.description)) {
			return false;
		}
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	public String getResponderUri() {
		return responderUri;
	}

	public void setResponderUri(String responderUri) {
		this.responderUri = responderUri;
	}

	public String getExecutionsUri() {
		return executionsUri;
	}

	public void setExecutionsUri(String executionsUri) {
		this.executionsUri = executionsUri;
	}

	/**
	 * @return the type
	 */
	public DataSourceType getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(DataSourceType type) {
		this.type = type;
	}
}
