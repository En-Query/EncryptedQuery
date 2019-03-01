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

/**
 * The status of an execution as reported by a QueryRunner.
 */
public class ExecutionStatus {

	private final Date endTime;
	private final String error;
	private final boolean canceled;

	public ExecutionStatus(Date endTime, String error, boolean canceled) {
		this.endTime = endTime;
		this.error = error;
		this.canceled = canceled;
	}

	public Date getEndTime() {
		return endTime;
	}

	public String getError() {
		return error;
	}

	public boolean isCanceled() {
		return canceled;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ExecutionStatus [endTime=").append(endTime).append(", error=").append(error).append(", canceled=").append(canceled).append("]");
		return builder.toString();
	}

}
