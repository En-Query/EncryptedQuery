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
package org.enquery.encryptedquery.responder.data.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;


@Entity
@Table(name = "executions")
public class Execution {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "id_generator")
	private Integer id;

	@Column(nullable = false, name = "received_ts")
	@Temporal(TemporalType.TIMESTAMP)
	private Date receivedTime;

	@Column(nullable = false, name = "schedule_ts")
	@Temporal(TemporalType.TIMESTAMP)
	private Date scheduleTime;

	@Column(name = "start_ts")
	@Temporal(TemporalType.TIMESTAMP)
	private Date startTime;

	@Column(name = "end_ts")
	@Temporal(TemporalType.TIMESTAMP)
	private Date endTime;

	@Column(nullable = false, name = "data_source_name")
	private String dataSourceName;

	@Column(name = "query_url")
	private String queryLocation;

	@Column(name = "parameters")
	@Lob
	private String parameters;

	@ManyToOne()
	@JoinColumn(name = "dataschema_id")
	private DataSchema dataSchema;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Date getReceivedTime() {
		return receivedTime;
	}

	public void setReceivedTime(Date receivedTime) {
		this.receivedTime = receivedTime;
	}

	public Date getScheduleTime() {
		return scheduleTime;
	}

	public void setScheduleTime(Date scheduleTime) {
		this.scheduleTime = scheduleTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	public String getDataSourceName() {
		return dataSourceName;
	}

	public void setDataSourceName(String dataSourceName) {
		this.dataSourceName = dataSourceName;
	}

	public String getParameters() {
		return parameters;
	}

	public void setParameters(String parameters) {
		this.parameters = parameters;
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
		Execution other = (Execution) obj;
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
		builder.append("Execution [id=").append(id).append(", receivedTime=").append(receivedTime).append(", scheduleTime=").append(scheduleTime).append(", startTime=").append(startTime).append(", endTime=").append(endTime).append(", dataSourceName=")
				.append(dataSourceName).append(", queryLocation=").append(queryLocation).append(", parameters=").append(parameters).append(", dataSchema=").append(dataSchema).append("]");
		return builder.toString();
	}

	public DataSchema getDataSchema() {
		return dataSchema;
	}

	public void setDataSchema(DataSchema dataSchema) {
		this.dataSchema = dataSchema;
	}

	/**
	 * @return the queryLocation
	 */
	public String getQueryLocation() {
		return queryLocation;
	}

	/**
	 * @param queryLocation the queryLocation to set
	 */
	public void setQueryLocation(String queryLocation) {
		this.queryLocation = queryLocation;
	}

}
