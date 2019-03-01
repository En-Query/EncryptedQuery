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

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.enquery.encryptedquery.querier.data.entity.ScheduleStatus;

@Entity
@Table(name = "schedules")
@NamedEntityGraphs({
		@NamedEntityGraph(
				name = Schedule.ALL_ENTITY_GRAPH,
				attributeNodes = {
						@NamedAttributeNode(value = "query", subgraph = "query")
				},
				subgraphs = {@NamedSubgraph(
						name = "query",
						type = Query.class,
						attributeNodes = @NamedAttributeNode(value = "querySchema", subgraph = "qschema")),
						@NamedSubgraph(
								name = "qschema",
								type = QuerySchema.class,
								attributeNodes = {
										@NamedAttributeNode(value = "dataSchema"),
										@NamedAttributeNode("fields")
								})
				})})
public class Schedule {

	public static final String ALL_ENTITY_GRAPH = "Schedule.all";

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "sequences")
	private Integer id;

	@Column(nullable = false, name = "start_time")
	@Temporal(TemporalType.TIMESTAMP)
	private Date startTime;

	@OneToOne()
	@JoinColumn(name = "query_id", nullable = false)
	private Query query;

	@Column(nullable = false, name = "status")
	@Enumerated(EnumType.STRING)
	private ScheduleStatus status;

	@Column(name = "parameters")
	@Lob
	private String parameters;

	@OneToOne()
	@JoinColumn(name = "datasource_id", nullable = false)
	private DataSource dataSource;

	@Column(name = "responder_id", unique = true)
	private Integer responderId;

	@Column(name = "responder_uri", unique = true)
	private String responderUri;

	@Column(name = "responder_results_uri")
	private String responderResultsUri;

	@Column(name = "error_msg")
	private String errorMessage;

	public Schedule() {}

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

	public Query getQuery() {
		return query;
	}

	public void setQuery(Query query) {
		this.query = query;
	}

	public ScheduleStatus getStatus() {
		return status;
	}

	public void setStatus(ScheduleStatus status) {
		this.status = status;
	}

	public String getParameters() {
		return parameters;
	}

	public void setParameters(String parameters) {
		this.parameters = parameters;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * URI to the associated execution in the Responder
	 * 
	 * @return the responderUri
	 */
	public String getResponderUri() {
		return responderUri;
	}

	/**
	 * @param responderUri the responderUri to set
	 */
	public void setResponderUri(String responderUri) {
		this.responderUri = responderUri;
	}

	/**
	 * @return the responderResultsUri
	 */
	public String getResponderResultsUri() {
		return responderResultsUri;
	}

	/**
	 * @param responderResultsUri the responderResultsUri to set
	 */
	public void setResponderResultsUri(String responderResultsUri) {
		this.responderResultsUri = responderResultsUri;
	}

	/**
	 * Id of the Responder Execution associated to this Schedule
	 * 
	 * @return the responderId
	 */
	public Integer getResponderId() {
		return responderId;
	}

	/**
	 * @param responderId the responderId to set
	 */
	public void setResponderId(Integer responderId) {
		this.responderId = responderId;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
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
		Schedule other = (Schedule) obj;
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
		builder.append("Schedule [id=").append(id).append("]");
		return builder.toString();
	}

}
