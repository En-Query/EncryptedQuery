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
package org.enquery.encryptedquery.json;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.data.DataSchema;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class JSONStringConverter {

	// private static final Logger log = LoggerFactory.getLogger(JSONStringConverter.class);

	private static final TypeReference<Map<String, Object>> typeStringObjectMapRef = new TypeReference<Map<String, Object>>() {};
	private static final TypeReference<Map<String, String>> typeStringStringMapRef = new TypeReference<Map<String, String>>() {};

	private static final TypeReference<List<String>> typeListRef = new TypeReference<List<String>>() {};
	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final TypeReference<MapWithSchema> mapWithSchematypeRef = new TypeReference<MapWithSchema>() {};

	private final ObjectMapper objectMapperWithDataSchema;

	/**
	 * 
	 */
	public JSONStringConverter(DataSchema dataSchema) {
		Validate.notNull(dataSchema);
		objectMapperWithDataSchema = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addValueInstantiator(MapWithSchema.class, new MapWithSchemaInstantiator(dataSchema));
		module.addAbstractTypeMapping(Map.class, MapWithSchema.class);
		// module.addValueInstantiator(Map.class, new MapWithSchemaInstantiator(dataSchema));
		objectMapperWithDataSchema.registerModule(module);
	}

	public static Map<String, Object> toStringObjectMap(String json) {
		Map<String, Object> returnMap = null;
		if (json == null) return null;
		try {
			returnMap = objectMapper.readValue(json, typeStringObjectMapRef);
		} catch (Exception e) {
			throw new RuntimeException(
					String.format("Exception converting %s to map.", json),
					e);
		}
		return returnMap;
	}

	private Map<String, Object> toStringObjectMapWithDataSchema(String json) {
		if (json == null) return null;

		try {
			MapWithSchema mapWithSchema = objectMapperWithDataSchema.readValue(json, mapWithSchematypeRef);
			return mapWithSchema.getData();
		} catch (Exception e) {
			throw new RuntimeException(
					String.format("Exception converting %s to map.", json),
					e);
		}
	}

	/**
	 * Creates a flat Map, where nested object keys added to the parent (flattened) by combining its
	 * attributes with the pipe "|" character to create a flat Map of all the values. <br/>
	 * For Example:<br/>
	 * json = {"id":12, "nested": {"name":"xxx"}} <br/>
	 * produces a Map with keys "id" with integer value of 12, and "nested|name" of type String with
	 * value "xxx"
	 * 
	 * Data types and nulls are preserved.
	 * 
	 * @param json
	 * @return
	 */
	public Map<String, Object> toStringObjectFlatMap(String json) {
		return toStringObjectMapWithDataSchema(json);
	}


	public static Map<String, String> toMap(String json) {
		Map<String, String> returnMap = null;
		if (json == null) return null;
		try {
			returnMap = objectMapper.readValue(json, typeStringStringMapRef);
		} catch (Exception e) {
			throw new RuntimeException(
					String.format("Exception converting %s to map.", json),
					e);
		}
		return returnMap;
	}

	public static List<String> toList(String json) {
		List<String> returnList = null;

		if (json == null) return null;
		try {
			returnList = objectMapper.readValue(json, typeListRef);
		} catch (Exception e) {
			throw new RuntimeException(
					String.format("Exception converting %s to list.", json),
					e);
		}
		return returnList;
	}

	public static String toString(List<String> list) throws JsonParseException, JsonMappingException, IOException {
		if (list == null) return null;
		try {
			return objectMapper.writeValueAsString(list);
		} catch (Exception e) {
			throw new RuntimeException(
					String.format("Exception converting list %s to string.", list),
					e);
		}

	}

	public static String toString(Map<String, String> map) {
		if (map == null) return null;
		try {
			return objectMapper.writeValueAsString(map);
		} catch (Exception e) {
			throw new RuntimeException("Exception converting map to string.", e);
		}
	}

	public static String toString(Object object) {
		if (object == null) return null;
		try {
			return objectMapper.writeValueAsString(object);
		} catch (Exception e) {
			throw new RuntimeException("Exception converting object to string.", e);
		}
	}

}
