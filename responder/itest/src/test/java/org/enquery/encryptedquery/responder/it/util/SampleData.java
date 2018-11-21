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
package org.enquery.encryptedquery.responder.it.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.enquery.encryptedquery.responder.data.entity.DataSchema;
import org.enquery.encryptedquery.responder.data.entity.DataSchemaField;
import org.enquery.encryptedquery.responder.data.entity.DataSource;
import org.enquery.encryptedquery.responder.data.entity.Execution;
import org.enquery.encryptedquery.responder.data.entity.Result;

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


	public Map<String, Object> createSampleParameters() {
		Map<String, Object> map = new HashMap<>();
		map.put("param1", "value1");
		map.put("param2", "value2");
		map.put("windowSizeInSeconds", 1000);
		map.put("windowIterations", 30);
		return map;
	}

	public Result createResult(Execution jpaExecution) {
		Result result = new Result();
		result.setExecution(jpaExecution);
		result.setCreationTime(new Date());
		return result;
	}

	public Execution createExecution(DataSchema dataSchema, DataSource dataSource) throws JsonProcessingException {
		Execution result = new Execution();
		result.setDataSchema(dataSchema);
		result.setDataSourceName(dataSource.getName());
		result.setScheduleTime(new Date());
		result.setParameters(new ObjectMapper().writeValueAsString(createSampleParameters()));
		result.setReceivedTime(new Date());
		return result;
	}
}
