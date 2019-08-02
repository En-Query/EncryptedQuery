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
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.ScheduleStatus;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.ResultRepository;
import org.enquery.encryptedquery.querier.data.service.RetrievalRepository;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.enquery.encryptedquery.xml.schema.ResultResource;
import org.enquery.encryptedquery.xml.transformation.ResultExportReader;
import org.enquery.encryptedquery.xml.transformation.ResultReader;
import org.enquery.encryptedquery.xml.transformation.XMLFactories;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = ResultUpdater.class)
public class ResultUpdater {

	@Reference
	private ScheduleRepository scheduleRepo;
	@Reference
	private ResultRepository resultRepo;
	@Reference
	private ExecutorService threadPool;
	@Reference
	private RetrievalRepository retrievalRepo;

	public int importFromResultExportXMLStream(InputStream inputStream) throws IOException, XMLStreamException {
		Validate.notNull(inputStream);
		int count = 0;
		try (ResultExportReader reader = new ResultExportReader(threadPool);) {
			reader.parse(inputStream);
			while (reader.hasNextItem()) {
				processItem(reader);
				++count;
			}
		}
		return count;
	}

	/**
	 * @param reader
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	private void processItem(ResultExportReader reader) throws IOException, XMLStreamException {
		reader.nextItem();
		Schedule schedule = scheduleRepo.findByUUID(reader.getExecutionUUID());
		Validate.notNull(schedule, "Schedule not found for responder uuid '%s'.", reader.getExecutionUUID());

		schedule.setResponderId(reader.getExecutionId());
		schedule.setErrorMessage(reader.getExecutionErrorMsg());
		schedule.setResponderUri(reader.getExecutionSelfUri());
		schedule.setStatus(resolveStatus(reader));
		schedule.setResponderResultsUri(reader.getResultsUri());
		scheduleRepo.update(schedule);

		while (reader.hasNextResult()) {
			processResult(schedule, reader);
		}
	}

	private ScheduleStatus resolveStatus(ResultExportReader reader) {

		if (reader.getExecutionStartedOn() == null) {
			return ScheduleStatus.Pending;
		}

		if (reader.getExecutionErrorMsg() != null) {
			return ScheduleStatus.Failed;
		}

		if (reader.isExecutionCancelled()) {
			return ScheduleStatus.Cancelled;
		}

		if (reader.getExecutionCompletedOn() != null) {
			return ScheduleStatus.Complete;
		}

		return ScheduleStatus.InProgress;
	}

	/**
	 * @param schedule
	 * @param reader
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	private void processResult(Schedule schedule, ResultExportReader reader) throws IOException, XMLStreamException {
		Validate.notNull(schedule);
		Validate.notNull(reader);
		try (ResultReader resultReader = reader.nextResult();
				InputStream responseInputStream = resultReader.getResponseInputStream()) {
			Result result = new Result();
			result.setResponderId(resultReader.getResultId());
			result.setResponderUri(resultReader.getResultUri());
			result.setWindowStartTime(resultReader.getWindowStart());
			result.setWindowEndTime(resultReader.getWindowEnd());
			result.setSchedule(schedule);
			result = resultRepo.addOrUpdate(result);

			Retrieval retrieval = new Retrieval();
			retrieval.setResult(result);
			retrieval = retrievalRepo.add(retrieval);
			retrievalRepo.updatePayload(retrieval, responseInputStream);
		}
	}

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
		if (resultResource.getWindowStart() != null) {
			result.setWindowStartTime(XMLFactories.toUTCDate(resultResource.getWindowStart()));
		}
		if (resultResource.getWindowEnd() != null) {
			result.setWindowEndTime(XMLFactories.toUTCDate(resultResource.getWindowEnd()));
		}

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
