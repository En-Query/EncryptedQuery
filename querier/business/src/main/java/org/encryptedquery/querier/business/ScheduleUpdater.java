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
package org.encryptedquery.querier.business;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.ScheduleStatus;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.DataSourceRepository;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.enquery.encryptedquery.querier.data.transformation.JSONConverter;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = ScheduleUpdater.class)
public class ScheduleUpdater {

	@Reference
	private ScheduleRepository scheduleRepo;
	@Reference
	private DataSourceRepository dataSourceRepo;

	ScheduleRepository getScheduleRepo() {
		return scheduleRepo;
	}

	void setScheduleRepo(ScheduleRepository scheduleRepo) {
		this.scheduleRepo = scheduleRepo;
	}

	DataSourceRepository getDataSourceRepo() {
		return dataSourceRepo;
	}

	void setDataSourceRepo(DataSourceRepository dataSourceRepo) {
		this.dataSourceRepo = dataSourceRepo;
	}

	public Schedule create(org.enquery.encryptedquery.querier.data.entity.json.Schedule json, Query query) {
		Schedule result = new Schedule();

		// JSON Dates are in UTC, database timestamps also in UTC
		result.setStartTime(json.getStartTime());
		result.setParameters(JSONConverter.toString(json.getParameters()));

		Integer dataSourceId = Integer.valueOf(json.getDataSource().getId());
		DataSource dataSource = dataSourceRepo.find(dataSourceId);
		Validate.notNull(dataSource, "Data source not found for id %d.", dataSourceId);
		result.setDataSource(dataSource);
		result.setStatus(ScheduleStatus.Pending);
		result.setQuery(query);
		return scheduleRepo.add(result);
	}

	public Schedule updateWithError(int scheduleId, Exception exception) {
		Validate.notNull(exception);

		Schedule schedule = scheduleRepo.find(scheduleId);
		Validate.notNull(schedule, "Schedule with id %d not found.", scheduleId);

		schedule.setStatus(ScheduleStatus.Failed);
		schedule.setResponderId(null);
		schedule.setResponderUri(null);
		schedule.setResponderResultsUri(null);
		schedule.setErrorMessage(exceptionToString(exception));

		return scheduleRepo.update(schedule);
	}

	/**
	 * @param exception
	 * @return
	 */
	private String exceptionToString(Exception exception) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		exception.printStackTrace(pw);
		return sw.toString();
	}

	public Schedule updateFromExecution(int scheduleId, ExecutionResource execution) throws JAXBException, IOException {
		Validate.notNull(execution);

		Schedule schedule = scheduleRepo.find(scheduleId);
		Validate.notNull(schedule, "Schedule with id %d not found.", scheduleId);

		schedule.setStatus(resolveStatus(execution.getExecution()));
		schedule.setResponderId(execution.getId());
		schedule.setResponderUri(execution.getSelfUri());
		schedule.setResponderResultsUri(execution.getResultsUri());
		schedule.setErrorMessage(execution.getExecution().getErrorMessage());

		return scheduleRepo.update(schedule);
	}

	private ScheduleStatus resolveStatus(Execution execution) {

		if (execution.getStartedOn() == null) {
			return ScheduleStatus.Pending;
		}

		if (execution.getErrorMessage() != null) {
			return ScheduleStatus.Failed;
		}

		if (Boolean.TRUE.equals(execution.isCancelled())) {
			return ScheduleStatus.Cancelled;
		}

		if (execution.getCompletedOn() != null) {
			return ScheduleStatus.Complete;
		}

		return ScheduleStatus.InProgress;
	}
}
