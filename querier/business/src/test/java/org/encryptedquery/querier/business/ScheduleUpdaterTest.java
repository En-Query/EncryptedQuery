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

import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.GregorianCalendar;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.enquery.encryptedquery.querier.data.entity.ScheduleStatus;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.DataSourceRepository;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.enquery.encryptedquery.xml.schema.Execution;
import org.enquery.encryptedquery.xml.schema.ExecutionResource;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *
 */
public class ScheduleUpdaterTest {

	private static DatatypeFactory factory;
	private static ScheduleRepository scheduleRepo;
	private static ScheduleUpdater updater;

	@BeforeClass
	public static void beforeClass() throws DatatypeConfigurationException {
		factory = DatatypeFactory.newInstance();
		scheduleRepo = mockShceduleRepo();
		DataSourceRepository dataSourceRepo = Mockito.mock(DataSourceRepository.class);

		updater = new ScheduleUpdater();
		updater.setScheduleRepo(scheduleRepo);
		updater.setDataSourceRepo(dataSourceRepo);
	}


	@Test
	public void testFailedStatus() throws JAXBException, IOException {

		ExecutionResource executionRes = new ExecutionResource();
		Execution execution = new Execution();
		execution.setErrorMessage("Some error");
		execution.setStartedOn(xmlDate());
		executionRes.setExecution(execution);

		Schedule updatedExecution = updater.updateFromExecution(1, executionRes);
		assertThat(updatedExecution, Matchers.notNullValue());
		assertThat(updatedExecution.getStatus(), Matchers.is(ScheduleStatus.Failed));
	}

	@Test
	public void testCancelledStatus() throws JAXBException, IOException {

		ExecutionResource executionRes = new ExecutionResource();
		Execution execution = new Execution();
		execution.setCancelled(true);
		execution.setStartedOn(xmlDate());
		executionRes.setExecution(execution);

		Schedule updatedExecution = updater.updateFromExecution(1, executionRes);
		assertThat(updatedExecution, Matchers.notNullValue());
		assertThat(updatedExecution.getStatus(), Matchers.is(ScheduleStatus.Cancelled));
	}


	@Test
	public void testPendingStatus() throws JAXBException, IOException {

		ExecutionResource executionRes = new ExecutionResource();
		Execution execution = new Execution();
		executionRes.setExecution(execution);

		Schedule updatedExecution = updater.updateFromExecution(1, executionRes);
		assertThat(updatedExecution, Matchers.notNullValue());
		assertThat(updatedExecution.getStatus(), Matchers.is(ScheduleStatus.Pending));
	}


	@Test
	public void testInProgressStatus() throws JAXBException, IOException {

		ExecutionResource executionRes = new ExecutionResource();
		Execution execution = new Execution();
		execution.setStartedOn(xmlDate());
		executionRes.setExecution(execution);

		Schedule updatedExecution = updater.updateFromExecution(1, executionRes);
		assertThat(updatedExecution, Matchers.notNullValue());
		assertThat(updatedExecution.getStatus(), Matchers.is(ScheduleStatus.InProgress));
	}

	@Test
	public void testCompleteStatus() throws JAXBException, IOException {

		ExecutionResource executionRes = new ExecutionResource();
		Execution execution = new Execution();
		execution.setStartedOn(xmlDate());
		execution.setCompletedOn(xmlDate());
		executionRes.setExecution(execution);

		Schedule updatedExecution = updater.updateFromExecution(1, executionRes);
		assertThat(updatedExecution, Matchers.notNullValue());
		assertThat(updatedExecution.getStatus(), Matchers.is(ScheduleStatus.Complete));
	}


	private XMLGregorianCalendar xmlDate() {
		return factory.newXMLGregorianCalendar(new GregorianCalendar());
	}

	private static ScheduleRepository mockShceduleRepo() {
		ScheduleRepository scheduleRepo = Mockito.mock(ScheduleRepository.class);

		Schedule persistedSchedule = new Schedule();
		persistedSchedule.setId(1);

		Mockito.when(scheduleRepo.find(1)).thenReturn(persistedSchedule);
		Mockito.when(scheduleRepo
				.update(Mockito.any(Schedule.class)))
				.thenAnswer(i -> i.getArguments()[0]);
		return scheduleRepo;
	}

}
