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
package org.enquery.encryptedquery.responder.it.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.inject.Inject;

import org.enquery.encryptedquery.responder.it.QueryRunnerConfigurator;
import org.enquery.encryptedquery.xml.schema.DataSource;
import org.enquery.encryptedquery.xml.schema.DataSourceResource;
import org.enquery.encryptedquery.xml.schema.DataSourceResources;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.cm.ConfigurationAdmin;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DataSourceRestServiceIT extends BaseRestServiceItest {

	private static final String DATA_SOURCE_NAME = "test-name";
	private static final String DESCRIPTION = "test-desc";

	@Inject
	private ConfigurationAdmin confAdmin;

	@Configuration
	public Option[] configuration() {
		return super.baseOptions();
	}

	@Before
	@Override
	public void init() throws Exception {
		super.init();
		installBooksDataSchema();
	}


	@Test
	public void list() throws Exception {

		// First run without any QueryRunner, should return no data sources empty
		DataSourceResources dataSources = retrieveDataSources("/responder/api/rest/datasources");
		assertEquals(0, dataSources.getDataSourceResource().size());

		// Now add a QueryRunner, service should return one data source for it
		QueryRunnerConfigurator runnerConfigurator = new QueryRunnerConfigurator(confAdmin);
		runnerConfigurator.create(DATA_SOURCE_NAME, BOOKS_DATA_SCHEMA_NAME, DESCRIPTION);

		// give enough time for the QueryRunner to be registered
		waitUntilQueryRunnerRegistered(DATA_SOURCE_NAME);

		dataSources = retrieveDataSources("/responder/api/rest/datasources");
		assertEquals(1, dataSources.getDataSourceResource().size());
		DataSource dataSource = dataSources.getDataSourceResource().get(0).getDataSource();
		assertEquals(DATA_SOURCE_NAME, dataSource.getName());
		assertEquals(BOOKS_DATA_SCHEMA_NAME, dataSource.getDataSchemaName());
		assertEquals(DESCRIPTION, dataSource.getDescription());

		DataSourceResource resource = dataSources.getDataSourceResource().get(0);
		DataSourceResource retrieved = retrieveDataSource(resource.getSelfUri());
		assertNotNull(retrieved);
	}
}
