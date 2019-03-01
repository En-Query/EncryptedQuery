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
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedEntityGraphs;
import javax.persistence.NamedSubgraph;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "results")
@NamedEntityGraphs({
		@NamedEntityGraph(
				name = Result.ALL_ENTITY_GRAPH,
				attributeNodes = {
						@NamedAttributeNode(value = "schedule", subgraph = "schedule")
				},
				subgraphs = {
						@NamedSubgraph(
								name = "schedule",
								attributeNodes = @NamedAttributeNode(value = "query", subgraph = "query")),
						@NamedSubgraph(
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
public class Result {

	public static final String ALL_ENTITY_GRAPH = "Result.all";

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "sequences")
	private Integer id;

	@OneToOne()
	@JoinColumn(name = "schedule_id", nullable = false)
	private Schedule schedule;

	@Column(name = "responder_id", nullable = false)
	private Integer responderId;

	@Column(name = "responder_uri", nullable = false)
	private String responderUri;

	@Column(nullable = true, name = "window_start_ts")
	@Temporal(TemporalType.TIMESTAMP)
	private Date windowStartTime;

	@Column(nullable = true, name = "window_end_ts")
	@Temporal(TemporalType.TIMESTAMP)
	private Date windowEndTime;

	public Result() {}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	/**
	 * URI to the Responder Result corresponding to this result
	 * 
	 * @return the responderResultUri
	 */
	public String getResponderUri() {
		return responderUri;
	}

	/**
	 * @param responderResultUri the responderResultUri to set
	 */
	public void setResponderUri(String responderResultUri) {
		this.responderUri = responderResultUri;
	}

	/**
	 * @return the schedule
	 */
	public Schedule getSchedule() {
		return schedule;
	}

	/**
	 * @param schedule the schedule to set
	 */
	public void setSchedule(Schedule schedule) {
		this.schedule = schedule;
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
		Result other = (Result) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		return true;
	}

	/**
	 * Id of the Responder Result corresponding to this result
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

	public Date getWindowStartTime() {
		return windowStartTime;
	}

	public void setWindowStartTime(Date windowStartTime) {
		this.windowStartTime = windowStartTime;
	}

	public Date getWindowEndTime() {
		return windowEndTime;
	}

	public void setWindowEndTime(Date windowEndTime) {
		this.windowEndTime = windowEndTime;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Result [id=").append(id).append("]");
		return builder.toString();
	}

}
