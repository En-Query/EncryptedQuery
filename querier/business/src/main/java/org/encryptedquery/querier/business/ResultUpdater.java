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

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.ResultRepository;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.enquery.encryptedquery.xml.schema.ResultResource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = ResultUpdater.class)
public class ResultUpdater {

	@Reference
	private ScheduleRepository scheduleRepo;
	@Reference
	private ResultRepository resultRepo;

	public Result update(org.enquery.encryptedquery.xml.schema.ResultResource resultResource) {
		Validate.notNull(resultResource);

		Result prev = resultRepo.findByResponderId(resultResource.getId());
		if (prev != null) return updateExisting(prev, resultResource);

		return createNew(resultResource);
	}

	private Result createNew(ResultResource resultResource) {

		int scheduleResponderId = resultResource.getExecution().getId();
		Schedule schedule = scheduleRepo.findByResponderId(scheduleResponderId);
		Validate.notNull(schedule, "Schedule with Responder id %d not found.", scheduleResponderId);

		Result result = new Result();
		result.setResponderId(resultResource.getId());
		result.setResponderUri(resultResource.getSelfUri());
		result.setSchedule(schedule);
		// result.setStatus(ResultStatus.Ready.toString());

		return resultRepo.add(result);
	}

	/**
	 * Update local Result in case the URI of the Responder Result changes. Nothing else is updated.
	 * 
	 * @param result
	 * @param resultResource
	 * @return
	 */
	private Result updateExisting(Result result, ResultResource resultResource) {
		result.setResponderUri(resultResource.getSelfUri());
		return resultRepo.update(result);
	}
}
