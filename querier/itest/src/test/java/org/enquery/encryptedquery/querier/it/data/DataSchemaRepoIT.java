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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Collection;

import javax.inject.Inject;

import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchemaField;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.enquery.encryptedquery.querier.it.AbstractQuerierItest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DataSchemaRepoIT extends AbstractQuerierItest {

	private static final String DATA_SCHEMA_NAME = "test data schema";

	@Inject
	@Filter(timeout = 60_000)
	private DataSchemaRepository dataSchemaSvc;

	@Before
	public void initService() throws Exception {
		truncateTables();
	}

	@Configuration
	public Option[] configuration() {
		return new Option[] {baseOptions()};
	}


	@Test
	public void basicOperations() throws Exception {
		DataSchema ds = new DataSchema();
		ds.setName(DATA_SCHEMA_NAME);
		DataSchemaField field = new DataSchemaField();
		field.setDataSchema(ds);
		field.setDataType(FieldType.STRING);
		field.setFieldName("field1");
		field.setPosition(0);
		ds.getFields().add(field);

		dataSchemaSvc.add(ds);
		DataSchema ds1 = dataSchemaSvc.find(ds.getId());
		assertNotNull(ds1);
		assertEquals(DATA_SCHEMA_NAME, ds1.getName());

		Collection<DataSchema> list = dataSchemaSvc.list();
		assertNotNull(list);
		assertEquals(1, list.size());
		ds1 = list.iterator().next();
		assertNotNull(ds1);
		assertEquals(DATA_SCHEMA_NAME, ds1.getName());

		dataSchemaSvc.delete(ds.getId());
		ds1 = dataSchemaSvc.find(ds.getId());
		assertNull(ds1);

		list = dataSchemaSvc.list();
		assertNotNull(list);
		assertEquals(0, list.size());
	}

	@Test
	public void deleteAll() throws Exception {
		DataSchema ds1 = new DataSchema();
		ds1.setName(DATA_SCHEMA_NAME + 1);
		DataSchemaField field1 = new DataSchemaField();
		field1.setDataSchema(ds1);
		field1.setDataType(FieldType.STRING);
		field1.setFieldName("field1");
		field1.setPosition(0);
		ds1.getFields().add(field1);

		dataSchemaSvc.add(ds1);

		DataSchema ds2 = new DataSchema();
		ds2.setName(DATA_SCHEMA_NAME + 2);
		DataSchemaField field2 = new DataSchemaField();
		field2.setDataSchema(ds2);
		field2.setDataType(FieldType.INT);
		field2.setFieldName("field2");
		field2.setPosition(0);
		ds2.getFields().add(field2);

		dataSchemaSvc.add(ds2);

		dataSchemaSvc.deleteAll();

		Collection<DataSchema> list = dataSchemaSvc.list();
		assertNotNull(list);
		assertEquals(0, list.size());
	}

}
