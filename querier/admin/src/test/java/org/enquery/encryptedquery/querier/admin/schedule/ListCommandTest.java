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
package org.enquery.encryptedquery.querier.admin.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.karaf.shell.api.console.Session;
import org.enquery.encryptedquery.querier.admin.BaseCommandTest;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.enquery.encryptedquery.querier.data.service.ScheduleRepository;
import org.junit.Test;

/**
 *
 */
public class ListCommandTest extends BaseCommandTest {

	private static final String LONG_ERROR_MSG = "The Naming of Cats is a difficult matter,\n" +
			"It isn't just one of your holiday games;\n" +
			"You may think at first I'm as mad as a hatter\n" +
			"When I tell you, a cat must have THREE DIFFERENT NAMES.";

	private ScheduleRepository scheduleRepo;

	@Test
	public void test() throws Exception {
		final Session mockSession = mock(Session.class);
		mockRepository();

		// mock data schema repository
		DataSchemaRepository dataSchemaMock = mock(DataSchemaRepository.class);
		Collection<DataSchema> dataSchemaList = new ArrayList<>();
		DataSchema dataSchema = new DataSchema();
		dataSchema.setId(1);
		dataSchema.setName("Test data Schema");
		dataSchemaList.add(dataSchema);
		when(dataSchemaMock.list()).thenReturn(dataSchemaList);

		ListCommand command = new ListCommand();
		command.setSession(mockSession);
		command.setScheduleRepo(scheduleRepo);
		command.execute();
	}

	int count = 0;

	private void mockRepository() {
		DataSource dataSource = new DataSource();
		dataSource.setName("Books");
		Query query = new Query();
		query.setId(101);
		query.setName("Test Query");

		scheduleRepo = mock(ScheduleRepository.class);
		when(scheduleRepo.list(null, null)).then(invocation -> {
			List<Schedule> result = new ArrayList<>();
			for (int i = 0; i < 15; ++i) {
				String errorMsg = ((count % 3) == 0) ? LONG_ERROR_MSG : null;

				Schedule ex = new Schedule();
				ex.setId(count++);
				ex.setUuid(UUID.randomUUID().toString().replace("-", ""));
				ex.setDataSource(dataSource);
				ex.setStartTime(new Date());
				ex.setQuery(query);
				ex.setErrorMessage(errorMsg);
				result.add(ex);
			}
			return result;
		});
	}
}
