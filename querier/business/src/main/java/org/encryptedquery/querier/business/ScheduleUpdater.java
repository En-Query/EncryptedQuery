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
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleStatus;
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

	public Schedule create(org.enquery.encryptedquery.querier.data.entity.json.Schedule json, Query query) {
		Schedule result = new Schedule();

		// JSON Dates are in UTC
		Instant instant = json.getStartTime().toInstant();
		ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
		java.util.Date localDateTime = Date.from(zonedDateTime.toInstant());
		result.setStartTime(localDateTime);
		result.setParameters(JSONConverter.toString(json.getParameters()));

		Integer dataSourceId = Integer.valueOf(json.getDataSource().getId());
		DataSource dataSource = dataSourceRepo.find(dataSourceId);
		Validate.notNull(dataSource, "Data source not found for id %d.", dataSourceId);
		result.setDataSource(dataSource);
		result.setStatus(ScheduleStatus.Pending);
		result.setQuery(query);
		return scheduleRepo.add(result);
	}

	public Schedule updateFromExecution(int scheduleId, ExecutionResource execution) throws JAXBException, IOException {
		Validate.notNull(execution);

		Schedule schedule = scheduleRepo.find(scheduleId);
		Validate.notNull(schedule, "Schedule with id %d not found.", scheduleId);

		schedule.setStatus(resolveStatus(execution.getExecution()));
		schedule.setResponderId(execution.getId());
		schedule.setResponderUri(execution.getSelfUri());
		schedule.setResponderResultsUri(execution.getResultsUri());
		return scheduleRepo.update(schedule);
	}

	private ScheduleStatus resolveStatus(Execution execution) {
		ScheduleStatus result = ScheduleStatus.Pending;
		if (execution.getStartedOn() != null) {
			if (execution.getCompletedOn() == null) {
				result = ScheduleStatus.InProgress;
			} else {
				result = ScheduleStatus.Complete;
			}
		}
		return result;
	}

}
