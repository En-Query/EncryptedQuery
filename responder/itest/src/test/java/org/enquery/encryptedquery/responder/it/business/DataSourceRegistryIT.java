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
package org.enquery.encryptedquery.responder.it.business;

import static org.junit.Assert.assertNull;

import java.util.Collection;

import javax.inject.Inject;

import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.service.DataSourceRegistry;
import org.enquery.encryptedquery.responder.it.AbstractResponderItest;
import org.enquery.encryptedquery.responder.it.util.FlinkDriver;
import org.enquery.encryptedquery.responder.it.util.FlinkJdbcRunnerConfigurator;
import org.enquery.encryptedquery.responder.it.util.ThrowingPredicate;
import org.junit.Assert;
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
public class DataSourceRegistryIT extends AbstractResponderItest {

	private static final String DATA_SOURCE_NAME = "phone-book-jdbc-flink";
	private static final String DESCRIPTION = "A phone book JDBC source queried with Flink";
	@Inject
	private DataSourceRegistry dsRegistry;
	@Inject
	private ConfigurationAdmin confAdmin;
	private FlinkDriver flinkDriver = new FlinkDriver();

	@Configuration
	public Option[] configuration() {
		return combineOptions(super.baseOptions(),
				flinkDriver.configuration());
	}

	@Override
	@Before
	public void init() throws Exception {
		super.init();
		installBooksDataSchema();
	}

	@Test
	public void addAndList() throws Exception {
		Assert.assertEquals(0, dsRegistry.list().size());

		FlinkJdbcRunnerConfigurator conf = new FlinkJdbcRunnerConfigurator(confAdmin);
		conf.create(DATA_SOURCE_NAME, BOOKS_DATA_SCHEMA_NAME, DESCRIPTION);

		waitUntilQueryRunnerCountAtLeast(dummy -> dsRegistry.list().size() >= 1);

		Collection<DataSource> sources = dsRegistry.list();
		Assert.assertEquals(1, sources.size());

		DataSource dataSource = sources.iterator().next();
		Assert.assertEquals(DATA_SOURCE_NAME, dataSource.getName());
		Assert.assertEquals(DESCRIPTION, dataSource.getDescription());
		Assert.assertNotNull(dataSource.getRunner());

		dataSource = dsRegistry.find(DATA_SOURCE_NAME);
		Assert.assertNotNull(dataSource);
		Assert.assertEquals(DATA_SOURCE_NAME, dataSource.getName());
		Assert.assertEquals(DESCRIPTION, dataSource.getDescription());
		Assert.assertNotNull(dataSource.getRunner());

		dataSource = dsRegistry.findForDataSchema(BOOKS_DATA_SCHEMA_NAME);
		Assert.assertNotNull(dataSource);
		Assert.assertEquals(DATA_SOURCE_NAME, dataSource.getName());
		Assert.assertEquals(DESCRIPTION, dataSource.getDescription());
		Assert.assertNotNull(dataSource.getRunner());

		conf.delete();
		waitUntilQueryRunnerCountAtLeast(dummy -> dsRegistry.list().size() == 0);

		sources = dsRegistry.list();
		Assert.assertEquals(0, sources.size());
		assertNull(dsRegistry.find(DATA_SOURCE_NAME));
		assertNull(dsRegistry.findForDataSchema(BOOKS_DATA_SCHEMA_NAME));
	}

	private <T> void waitUntilQueryRunnerCountAtLeast(ThrowingPredicate<T> operation) throws Exception {
		tryUntilTrue(100,
				2000,
				"Timeout waiting for Data Source Registry.",
				operation,
				null);
	}
}
