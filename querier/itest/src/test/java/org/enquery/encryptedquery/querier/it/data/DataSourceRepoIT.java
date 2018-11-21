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

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.it.AbstractQuerierItest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DataSourceRepoIT extends AbstractQuerierItest {

	@Before
	public void initService() throws Exception {
		truncateTables();
	}

	@Configuration
	public Option[] configuration() {
		return new Option[] {baseOptions()};
	}

	@Test
	public void addOrUpdateExisting() throws Exception {

		DataSchema dataSchema = dataSchemaRepo.add(sampleData.createDataSchema());
		DataSource dataSource = dataSourceRepo.add(sampleData.createDataSource(dataSchema));

		DataSource detached = sampleData.createDataSource(dataSchema);
		detached.setName(dataSource.getName());
		detached.setDescription("updated description");

		dataSourceRepo.addOrUpdate(detached);

		assertEquals(detached.getDescription(), dataSourceRepo.findByName(dataSource.getName()).getDescription());
	}

	@Test
	public void addOrUpdateNew() throws Exception {

		DataSchema dataSchema = dataSchemaRepo.add(sampleData.createDataSchema());

		// create a detached, never before saved DataSource
		DataSource dataSource = sampleData.createDataSource(dataSchema);
		dataSource.setDescription("updated description");
		dataSource.setResponderUri("uri");
		dataSource.setExecutionsUri("uri");
		dataSourceRepo.addOrUpdate(dataSource);

		assertEquals(dataSource.getDescription(), dataSourceRepo.findByName(dataSource.getName()).getDescription());
	}


	@Test
	public void update() throws Exception {

		DataSchema dataSchema = dataSchemaRepo.add(sampleData.createDataSchema());
		DataSource dataSource = dataSourceRepo.add(sampleData.createDataSource(dataSchema));

		// DataSource previousInstance = dataSourceRepo.findByName(DATA_SOURCE_NAME);

		DataSource detached = sampleData.createDataSource(dataSchema);
		detached.setId(dataSource.getId());
		detached.setDescription("updated description");
		dataSourceRepo.update(detached);

		assertEquals(detached.getDescription(), dataSourceRepo.find(dataSource.getId()).getDescription());
	}

}
