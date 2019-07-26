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

import java.util.Date;
import java.util.Map;

import org.enquery.encryptedquery.querier.data.entity.ScheduleStatus;

import com.fasterxml.jackson.annotation.JsonView;

public class Schedule extends Resource {
	public static final String TYPE = "Schedule";
	@JsonView(Views.ListView.class)
	private Date startTime;
	@JsonView(Views.ListView.class)
	private ScheduleStatus status;
	private Map<String, String> parameters;
	private String resultsUri;
	private String uuid;
	private Resource query;
	private Resource dataSource;
	private String errorMsg;

	public Schedule() {
		setType(TYPE);
	}

	/**
	 * This time stamp is in UTC time zone
	 * 
	 * @return
	 */
	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public ScheduleStatus getStatus() {
		return status;
	}

	public void setStatus(ScheduleStatus status) {
		this.status = status;
	}

	public Map<String, String> getParameters() {
		return parameters;
	}

	public void setParameters(Map<String, String> parameters) {
		this.parameters = parameters;
	}

	public String getResultsUri() {
		return resultsUri;
	}

	public void setResultsUri(String resultsUri) {
		this.resultsUri = resultsUri;
	}

	public Resource getQuery() {
		return query;
	}

	public void setQuery(Resource query) {
		this.query = query;
	}

	public Resource getDataSource() {
		return dataSource;
	}

	public void setDataSource(Resource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Schedule [startTime=").append(startTime).append(", status=").append(status).append(", resultsUri=").append(resultsUri)
				.append(", query=").append(query).append(", dataSource=").append(dataSource).append("]");
		return builder.toString();
	}

	public String getErrorMsg() {
		return errorMsg;
	}

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
}
