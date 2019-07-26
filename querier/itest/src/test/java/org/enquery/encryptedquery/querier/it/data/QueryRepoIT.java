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

import java.io.ByteArrayInputStream;
import java.util.Collection;

import javax.inject.Inject;

import org.apache.sshd.common.util.io.IoUtils;
import org.enquery.encryptedquery.core.FieldType;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchemaField;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField;
import org.enquery.encryptedquery.querier.data.service.DataSchemaRepository;
import org.enquery.encryptedquery.querier.data.service.QueryRepository;
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
public class QueryRepoIT extends AbstractQuerierItest {

	private static final String QUERY_NAME = "test query";

	@Inject
	@Filter(timeout = 60_000)
	private QueryRepository queryRepo;
	@Inject
	@Filter(timeout = 60_000)
	private DataSchemaRepository dataSchemaRepo;
	@Inject
	@Filter(timeout = 60_000)
	private QuerySchemaRepository querySchemaRepo;

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
		DataSchemaField dsf = new DataSchemaField();
		dsf.setDataSchema(ds);
		dsf.setDataType(FieldType.INT);
		dsf.setFieldName("field1");
		dsf.setPosition(0);
		ds.getFields().add(dsf);
		ds.setName("Test Data Schema 1");
		dataSchemaRepo.add(ds);

		QuerySchema qs = new QuerySchema();
		qs.setDataSchema(ds);
		qs.setName("query schema 1");
		qs.setSelectorField("title");

		QuerySchemaField qsf = new QuerySchemaField();
		qsf.setQuerySchema(qs);
		qsf.setName("field1");
		qsf.setMaxSize(88);
		qsf.setMaxArrayElements(221);
		qs.getFields().add(qsf);
		querySchemaRepo.add(qs);

		Query query = new Query();
		query.setName(QUERY_NAME);
		query.setQuerySchema(qs);
		queryRepo.add(query);

		queryRepo.updateQueryBytes(query.getId(), new ByteArrayInputStream("test-query".getBytes()));
		queryRepo.updateQueryKeyBytes(query.getId(), new ByteArrayInputStream("test-query-key".getBytes()));

		assertEquals("test-query", new String(IoUtils.toByteArray(queryRepo.loadQueryBytes(query.getId()))));
		assertEquals("test-query-key", new String(IoUtils.toByteArray(queryRepo.loadQueryKeyBytes(query.getId()))));

		Collection<Query> list = queryRepo.list();
		assertEquals(1, list.size());
		assertEquals(QUERY_NAME, list.iterator().next().getName());

		Query query1 = queryRepo.findByName(QUERY_NAME);
		assertNotNull(query1);
		assertEquals(QUERY_NAME, query1.getName());

		query1 = queryRepo.find(query.getId());
		assertNotNull(query1);
		assertEquals(QUERY_NAME, query1.getName());

		queryRepo.deleteAll();
		list = queryRepo.list();
		assertEquals(0, list.size());
	}

}
