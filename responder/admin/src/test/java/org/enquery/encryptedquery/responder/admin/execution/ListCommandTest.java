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
package org.enquery.encryptedquery.responder.admin.execution;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.karaf.shell.api.console.Session;
import org.enquery.encryptedquery.responder.admin.BaseCommandTest;
import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.data.service.ExecutionRepository;
import org.junit.Test;

/**
 *
 */
public class ListCommandTest extends BaseCommandTest {

	private static final String LONG_ERROR_MSG = "The Naming of Cats is a difficult matter,\n" +
			"It isn't just one of your holiday games;\n" +
			"You may think at first I'm as mad as a hatter\n" +
			"When I tell you, a cat must have THREE DIFFERENT NAMES.";

	private ExecutionRepository executionRepo;

	@Test
	public void test() throws Exception {
		final Session mockSession = mock(Session.class);
		mockRepository();

		// mock data schema repository
		DataSchemaService dataSchemaMock = mock(DataSchemaService.class);
		Collection<DataSchema> dataSchemaList = new ArrayList<>();
		DataSchema dataSchema = new DataSchema();
		dataSchema.setId(1);
		dataSchema.setName("Test data Schema");
		dataSchemaList.add(dataSchema);
		when(dataSchemaMock.list()).thenReturn(dataSchemaList);

		ListCommand command = new ListCommand();
		command.setSession(mockSession);
		command.setExecutionRepo(executionRepo);
		command.execute();
	}

	int count = 0;

	private void mockRepository() {
		executionRepo = mock(ExecutionRepository.class);
		when(executionRepo.list(null, null, false)).then(invocation -> {
			List<Execution> result = new ArrayList<>();
			for (int i = 0; i < 15; ++i) {
				String dataSourceName = "Books";
				String errorMsg = ((count % 3) == 0) ? LONG_ERROR_MSG : null;

				Execution ex = new Execution();
				ex.setId(count++);
				ex.setUuid(UUID.randomUUID().toString().replace("-", ""));
				ex.setDataSourceName(dataSourceName);
				ex.setScheduleTime(new Date());
				ex.setStartTime(new Date());
				ex.setEndTime(new Date());
				ex.setReceivedTime(new Date());
				ex.setErrorMsg(errorMsg);
				ex.setCanceled(i % 2 == 0);
				result.add(ex);
			}
			return result;
		});
	}
}
