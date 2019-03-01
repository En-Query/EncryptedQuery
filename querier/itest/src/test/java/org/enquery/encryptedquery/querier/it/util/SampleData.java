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
package org.enquery.encryptedquery.querier.it.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.enquery.encryptedquery.querier.data.entity.DataSourceType;
import org.enquery.encryptedquery.querier.data.entity.ScheduleStatus;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchema;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSchemaField;
import org.enquery.encryptedquery.querier.data.entity.jpa.DataSource;
import org.enquery.encryptedquery.querier.data.entity.jpa.Decryption;
import org.enquery.encryptedquery.querier.data.entity.jpa.Query;
import org.enquery.encryptedquery.querier.data.entity.jpa.Result;
import org.enquery.encryptedquery.querier.data.entity.jpa.Retrieval;
import org.enquery.encryptedquery.querier.data.entity.jpa.Schedule;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchema;
import org.enquery.encryptedquery.querier.data.entity.json.QuerySchemaField;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SampleData {
	public static final String DATA_SOURCE_DESCRIPTION = "Data source description";
	public static final String DATA_SOURCE_NAME = "Data Source 1";
	public static final String DATA_SCHEMA_NAME = "Test Data Schema 1";

	private int dataSourceCount;
	private int dataSchemaCount;
	private int querySchemaCount;
	private int queryCount;

	public DataSource createDataSource(DataSchema dataSchema) {
		DataSource src = new DataSource();
		src.setName(DATA_SOURCE_NAME + dataSourceCount++);
		src.setDescription(DATA_SOURCE_DESCRIPTION);
		src.setDataSchema(dataSchema);
		src.setResponderUri("responder-uri");
		src.setExecutionsUri("executions-uri");
		src.setType(DataSourceType.Batch);
		return src;
	}

	public DataSchema createDataSchema() {
		String name = DATA_SCHEMA_NAME + dataSchemaCount++;
		return createDataSchema(name);
	}

	public DataSchema createDataSchema(String name) {
		DataSchema dataSchema = new DataSchema();
		DataSchemaField dsf = new DataSchemaField();
		dsf.setDataSchema(dataSchema);
		dsf.setDataType("int");
		dsf.setFieldName("field1");
		dsf.setPosition(0);
		dsf.setIsArray(false);
		List<DataSchemaField> fields = new ArrayList<>();
		fields.add(dsf);
		dataSchema.setFields(fields);
		dataSchema.setName(name);
		return dataSchema;
	}

	public QuerySchema createJSONQuerySchema(DataSchema dataSchema) {
		return createJSONQuerySchema(dataSchema.getId().toString());
	}

	public QuerySchema createJSONQuerySchema(String dataSchemaId) {
		QuerySchema result = new QuerySchema();
		result.setName("Query Schema " + querySchemaCount++);
		result.setSelectorField("field1");
		Resource dsr = new Resource();
		dsr.setId(dataSchemaId);
		dsr.setSelfUri("querier/api/rest/queryschemas/" + dataSchemaId);
		result.setDataSchema(dsr);

		QuerySchemaField field = new QuerySchemaField();
		field.setLengthType("fixed");
		field.setName("field1");
		field.setSize(10);
		field.setMaxArrayElements(1);
		List<QuerySchemaField> fields = new ArrayList<>();
		fields.add(field);
		result.setFields(fields);
		return result;
	}

	public org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema createJPAQuerySchema(DataSchema ds) {
		org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema qs = new org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema();
		qs.setDataSchema(ds);
		qs.setName("Query schema " + ++querySchemaCount);

		ds.getFields()
				.stream()
				.forEach(dsf -> {
					org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField qsf = new org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchemaField();
					qsf.setQuerySchema(qs);
					qsf.setName(dsf.getFieldName());
					qsf.setLengthType("Fixed");
					qsf.setMaxSize(88);
					qsf.setMaxArrayElements(221);
					qs.getFields().add(qsf);
				});

		qs.setSelectorField(ds.getFields().get(0).getFieldName());
		return qs;
	}

	public Query createQuery(org.enquery.encryptedquery.querier.data.entity.jpa.QuerySchema qs) {
		Query query = new Query();
		query.setName("Query " + ++queryCount);
		query.setQuerySchema(qs);
		return query;
	}

	public Schedule createSchedule(Query query, DataSource dataSource) throws JsonProcessingException {
		Schedule result = new Schedule();
		result.setStartTime(new Date());
		result.setQuery(query);
		result.setDataSource(dataSource);
		result.setStatus(ScheduleStatus.Pending);
		Map<String, Object> map = createScheduleSampleParameters();
		result.setParameters(new ObjectMapper().writeValueAsString(map));
		return result;
	}

	public Map<String, Object> createScheduleSampleParameters() {
		Map<String, Object> map = new HashMap<>();
		// map.put("param1", "value1");
		// map.put("param2", "value2");
		// map.put("windowSizeInSeconds", 1000);
		// map.put("windowIterations", 30);
		// map.put(ResponderProperties.COMPUTE_THRESHOLD, 30000);
		// map.put(QuerierProperties.METHOD, "basic");
		// map.put("pauseTimeForQueueCheck", 1);
		// map.put("maxQueueSize", 10000);
		// map.put("processingThreads", 1);
		return map;
	}

	public Result createResult(Schedule jpaSchedule) {
		Result result = new Result();
		result.setSchedule(jpaSchedule);
		result.setResponderId(10);
		result.setResponderUri("bad-uri");
		return result;
	}

	public Retrieval createRetrieval(Result result) {
		Retrieval r = new Retrieval();
		r.setResult(result);
		return r;
	}

	public Decryption addDecryption(Retrieval retrieval) {
		Decryption d = new Decryption();
		d.setRetrieval(retrieval);
		return d;
	}

}
