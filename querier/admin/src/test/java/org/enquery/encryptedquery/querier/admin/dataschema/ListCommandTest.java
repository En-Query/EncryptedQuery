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
package org.enquery.encryptedquery.querier.admin.dataschema;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.karaf.shell.api.console.Session;
import org.enquery.encryptedquery.querier.admin.BaseCommandTest;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.junit.Test;

/**
 *
 */
public class ListCommandTest extends BaseCommandTest {

	@Test
	public void test() throws Exception {
		final Session mockSession = mock(Session.class);

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
		command.setDataSchemaRepo(dataSchemaMock);
		command.execute();

		assertTrue(getConsoleOutput().contains("Test data Schema"));
	}

}
