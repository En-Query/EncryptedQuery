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
package org.enquery.encryptedquery.querier.it.data;

import static org.junit.Assert.assertEquals;

import java.util.Collection;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.it.AbstractQuerierItest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ScheduleRepoIT extends AbstractQuerierItest {

	@Test
	public void basicOperations() throws Exception {

		DataSchema dataSchema = dataSchemaRepo.add(sampleData.createDataSchema());
		DataSource dataSource = dataSourceRepo.add(sampleData.createDataSource(dataSchema));
		QuerySchema qs = querySchemaRepo.add(sampleData.createJPAQuerySchema(dataSchema));
		Query query1 = queryRepo.add(sampleData.createQuery(qs));
		Query query2 = queryRepo.add(sampleData.createQuery(qs));
		Schedule schedule1 = scheduleRepo.add(sampleData.createSchedule(query1, dataSource));
		Schedule schedule2 = scheduleRepo.add(sampleData.createSchedule(query2, dataSource));

		Collection<Schedule> list = scheduleRepo.listForQuery(query1);
		assertEquals(1, list.size());
		assertEquals(schedule1.getId(), list.stream().findFirst().get().getId());
		assertEquals(schedule1.getStatus(), list.stream().findFirst().get().getStatus());

		list = scheduleRepo.listForQuery(query2);
		assertEquals(1, list.size());
		assertEquals(schedule2.getId(), list.stream().findFirst().get().getId());
		assertEquals(schedule2.getStatus(), list.stream().findFirst().get().getStatus());

		scheduleRepo.deleteAll();
		assertEquals(0, scheduleRepo.listForQuery(query1).size());
		assertEquals(0, scheduleRepo.listForQuery(query2).size());
	}

}
