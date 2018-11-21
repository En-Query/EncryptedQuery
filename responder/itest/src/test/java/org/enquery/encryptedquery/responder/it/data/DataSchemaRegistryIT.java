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
package org.enquery.encryptedquery.responder.it.data;

import static org.junit.Assert.assertNotNull;

import java.util.Collection;

import javax.inject.Inject;

import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSchemaField;
import org.enquery.encryptedquery.responder.data.service.DataSchemaService;
import org.enquery.encryptedquery.responder.it.AbstractResponderItest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DataSchemaRegistryIT extends AbstractResponderItest {

	@Inject
	private DataSchemaService dataSchemaRegistry;

	@Configuration
	public Option[] configuration() {
		return super.baseOptions();
	}

	// @Before
	// public void initService() throws SQLException {
	// truncateTables();
	// }

	@Test
	public void addAndList() throws BundleException {
		Assert.assertEquals(0, dataSchemaRegistry.list().size());
		DataSchema ds = new DataSchema();
		ds.setName("sample");

		int max = 5;
		for (int i = 0; i < max; ++i) {
			DataSchemaField dsf = new DataSchemaField();
			dsf.setPosition(i);
			dsf.setFieldName("field" + i);
			dsf.setIsArray((i % max == 0) ? true : false);
			dsf.setDataType((i % max == 0) ? "int" : "string");
			dsf.setDataSchema(ds);
			ds.getFields().add(dsf);
		}

		dataSchemaRegistry.add(ds);

		Collection<DataSchema> tasks = dataSchemaRegistry.list();
		Assert.assertEquals(1, tasks.size());

		DataSchema retrievedDataSchema = dataSchemaRegistry.findByName("sample");
		assertNotNull(retrievedDataSchema);

		Assert.assertEquals("sample", retrievedDataSchema.getName());
		Assert.assertEquals(max, retrievedDataSchema.getFields().size());

		for (int i = 0; i < max; ++i) {
			DataSchemaField dsf = retrievedDataSchema.getFields().get(i);
			Assert.assertEquals("field" + i, dsf.getFieldName());
			Assert.assertEquals((i % max == 0) ? true : false, dsf.getIsArray());
			Assert.assertEquals((i % max == 0) ? "int" : "string", dsf.getDataType());
		}
	}

	/*--
	@Test
	public void addDelete() throws BundleException {
		Assert.assertEquals(0, dataSchemaRegistry.list().size());
		DataSource dataSource = new DataSource();
		dataSource.setName("Oracle");
		dataSource.setConnectionURL("oracle_url");
		dataSource.setType("RelationalDB_type");
		dataSchemaRegistry.add(dataSource);
	
		DataSource dataSource1 = dataSchemaRegistry.find(dataSource.getName());
		Assert.assertNotNull(dataSource1);
		Assert.assertEquals("Oracle", dataSource1.getName());
		Assert.assertEquals("oracle_url", dataSource1.getConnectionURL());
		Assert.assertEquals("RelationalDB_type", dataSource1.getType());
	
		dataSchemaRegistry.delete(dataSource.getName());
		Assert.assertEquals(0, dataSchemaRegistry.list().size());
	}*/

}
