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
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import javax.inject.Inject;

import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchemaField;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.enquery.encryptedquery.querier.data.service.QuerySchemaRepository;
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
public class QuerySchemaRepoIT extends AbstractQuerierItest {

	private static final String DATA_SCHEMA_NAME = "test data schema";

	@Inject
	@Filter(timeout = 60_000)
	private QuerySchemaRepository querySchemaRepo;
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
		field.setDataType("string");
		field.setFieldName("field1");
		field.setIsArray(false);
		field.setPosition(0);
		ds.getFields().add(field);

		ds = dataSchemaSvc.add(ds);

		for (int i = 0; i < 3; ++i) {
			QuerySchema qs = new QuerySchema();
			qs.setDataSchema(ds);
			qs.setName("query-schema-" + i);
			qs.setSelectorField("field-1");

			for (int j = 0; j < 3; ++j) {
				QuerySchemaField qsf = new QuerySchemaField();
				qsf.setLengthType("fixed");
				qsf.setMaxArrayElements(12);
				qsf.setMaxSize(122);
				qsf.setName("field-" + j);
				qsf.setQuerySchema(qs);
				qs.getFields().add(qsf);
			}

			querySchemaRepo.add(qs);
		}

		Collection<QuerySchema> list = querySchemaRepo.withDataSchema(ds);
		assertEquals(3, list.size());

		for (QuerySchema qs : list) {
			assertNotNull(qs.getId());
			assertTrue(qs.getName().startsWith("query-schema-"));
			assertEquals("field-1", qs.getSelectorField());
			assertEquals(3, qs.getFields().size());
			for (QuerySchemaField qsf : qs.getFields()) {
				assertTrue(qsf.getName().startsWith("field-"));
			}
		}
	}

}
