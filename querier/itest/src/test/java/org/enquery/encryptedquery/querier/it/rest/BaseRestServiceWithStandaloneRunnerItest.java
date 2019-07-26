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
package org.enquery.encryptedquery.querier.it.rest;

import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.replaceConfigurationFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.enquery.encryptedquery.querier.data.entity.RetrievalStatus;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.json.DataSchemaResponse;
import org.enquery.encryptedquery.querier.data.entity.json.DataSourceResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Query;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaField;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;
import org.enquery.encryptedquery.querier.data.entity.json.ResultResponse;
import org.enquery.encryptedquery.querier.data.entity.json.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.json.RetrievalResponse;
import org.enquery.encryptedquery.querier.data.entity.json.ScheduleResponse;
import org.enquery.encryptedquery.responder.ResponderProperties;
import org.enquery.encryptedquery.standalone.StandaloneConfigurationProperties;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BaseRestServiceWithStandaloneRunnerItest extends BaseRestServiceItest {

	private int querySchemaCount;

	@Override
	@Before
	public void init() throws Exception {
		log.info("Initializing");
		super.init();
		waitForFileConsumed(new File(responderInboxDir, "simple-data-schema.xml"));
		DataSchemaResponse jsonDataSchema = retrieveDataSchemaByName("Simple Data");
		assertNotNull(jsonDataSchema);
		DataSourceResponse jsonDataSource = retrieveDataSourceByName(jsonDataSchema, "Standalone-Simple-Name-Record");
		assertNotNull(jsonDataSource);
		log.info("Finished Initializing");
	}

	@Override
	protected Option baseOptions() {
		return CoreOptions.composite(
				super.baseOptions(),
				replaceConfigurationFile(
						"etc/org.enquery.encryptedquery.jpa.config.EMConfigurator.cfg",
						getResourceAsFile("/etc/org.enquery.encryptedquery.jpa.config.EMConfigurator.cfg")));
	}

	@Configuration
	public Option[] configuration() {
		return new Option[] {baseOptions()};
	}

	protected org.enquery.encryptedquery.querier.data.entity.json.Query createQuery() {
		org.enquery.encryptedquery.querier.data.entity.json.Query q = new org.enquery.encryptedquery.querier.data.entity.json.Query();
		q.setName("Simple Data Test Query " + ++queryCount);

		// TODO: replace params with specific properties
		Map<String, String> params = new HashMap<>();
		// params.put(QuerierProperties.DATA_CHUNK_SIZE, "1");
		// params.put(QuerierProperties.HASH_BIT_SIZE, "9");
		// params.put(QuerierProperties.CERTAINTY, "128");
		q.setParameters(params);

		List<String> selectorValues = new ArrayList<>();
		selectorValues.add("31");
		q.setSelectorValues(selectorValues);

		return q;
	}

	protected ResultResponse postScheduleAndWaitForResult(String dataSourceName) throws Exception {
		return waitForScheduleResult(postSchedule(dataSourceName));
	}

	protected Retrieval submitQueryAndRetrieveResult(String dataSourceName) throws Exception {
		ResultResponse resultResponse = postScheduleAndWaitForResult(dataSourceName);
		RetrievalResponse r = postRetrieval(resultResponse);
		Resource retrieval = r.getData();

		tryUntilTrue(60,
				2_000,
				"Timed out waiting for retrieval completion",
				uri -> retrieveRetrieval(uri).getData().getStatus() == RetrievalStatus.Complete,
				retrieval.getSelfUri());

		Retrieval fullRetrieval = retrieveRetrieval(retrieval.getSelfUri()).getData();
		return fullRetrieval;
	}


	protected ScheduleResponse postSchedule(String dataSourceName) throws Exception {

		DataSchemaResponse jsonDataSchema = retrieveDataSchemaByName("Simple Data");
		DataSourceResponse jsonDataSource = retrieveDataSourceByName(jsonDataSchema, dataSourceName);

		QuerySchema querySchema = createQuerySchema(jsonDataSchema.getData());
		Query query = createQueryAndWaitForEncryption(querySchema);

		return postSchedule(query.getSchedulesUri(), jsonDataSource.getData().getId(), dataSourceParams());
	}

	protected Query createQueryAndWaitForEncryption(QuerySchema querySchema) throws Exception {
		Query query = createQuery();
		// submit the Query and wait for its encryption
		return createQueryAndWaitForEncryption(querySchema.getQueriesUri(), query).getData();
	}

	protected QuerySchema createQuerySchema(DataSchema dataSchema) {
		QuerySchema result = new QuerySchema();

		result.setName("Age Query Schema " + querySchemaCount++);
		result.setSelectorField("age");

		Resource dsr = new Resource();
		dsr.setId(dataSchema.getId());
		result.setDataSchema(dsr);

		List<QuerySchemaField> fields = new ArrayList<>();
		result.setFields(fields);

		QuerySchemaField field1 = new QuerySchemaField();
		field1.setName("name");
		// field1.setSize(128);
		// field1.setMaxArrayElements(1);
		fields.add(field1);

		QuerySchemaField field2 = new QuerySchemaField();
		field2.setName("children");
		// field2.setSize(128);
		// field2.setMaxArrayElements(3);
		fields.add(field2);

		QuerySchemaField field3 = new QuerySchemaField();
		field3.setName("age");
		// field3.setSize(4);
		// field3.setMaxArrayElements(1);
		fields.add(field3);


		return createQuerySchema(dataSchema.getQuerySchemasUri(), result).getData();
	}

	protected Map<String, String> dataSourceParams() {
		Map<String, String> map = new HashMap<>();

		map.put(ResponderProperties.COMPUTE_THRESHOLD, "10000");
		map.put(StandaloneConfigurationProperties.MAX_QUEUE_SIZE, "100");
		map.put(StandaloneConfigurationProperties.PROCESSING_THREADS, "8");
		return map;
	}
}
