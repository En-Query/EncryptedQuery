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

import com.fasterxml.jackson.annotation.JsonView;

public class Result extends Resource {
	public static final String TYPE = "Result";

	@JsonView(Views.ListView.class)
	private ResultStatus status;
	private String retrievalsUri;
	private Resource schedule;
	@JsonView(Views.ListView.class)
	private Date windowStartTime;
	@JsonView(Views.ListView.class)
	private Date windowEndTime;

	public Result() {
		setType(TYPE);
	}

	public ResultStatus getStatus() {
		return status;
	}

	public void setStatus(ResultStatus status) {
		this.status = status;
	}


	public String getRetrievalsUri() {
		return retrievalsUri;
	}

	public void setRetrievalsUri(String retrievalsUri) {
		this.retrievalsUri = retrievalsUri;
	}

	/**
	 * @return the schedule
	 */
	public Resource getSchedule() {
		return schedule;
	}

	/**
	 * @param schedule the schedule to set
	 */
	public void setSchedule(Resource schedule) {
		this.schedule = schedule;
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
		builder.append("Result [status=").append(status)
				.append(", retrievalsUri=").append(retrievalsUri)
				.append(", schedule=").append(schedule)
				.append(", windowStartTime=").append(windowStartTime)
				.append(", windowEndTime=").append(windowEndTime)
				.append("]");
		return builder.toString();
	}

}
