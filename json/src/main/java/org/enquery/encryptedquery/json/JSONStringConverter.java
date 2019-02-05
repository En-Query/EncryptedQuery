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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class JSONStringConverter {

	private static final Logger logger = LoggerFactory.getLogger(JSONStringConverter.class);

	private static final TypeReference<Map<String, Object>> typeStringObjectMapRef = new TypeReference<Map<String, Object>>() {};
	private static final TypeReference<Map<String, String>> typeStringStringMapRef = new TypeReference<Map<String, String>>() {};

	private static final TypeReference<List<String>> typeListRef = new TypeReference<List<String>>() {};
	private static final ObjectMapper objectMapper = new ObjectMapper();

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

	public static Map<String, Object> toStringObjectMapFromList(Collection<String> fields, String jsonString) {
		Map<String, Object> returnMap = new HashMap<>();
		try {
			JsonNode jsonNode = objectMapper.readValue(jsonString, JsonNode.class);
			if (jsonNode == null) {
				return null;
			}
			for (String field : fields) {
				Object fieldValue = getJsonValue(jsonNode, field);
				returnMap.put(field, fieldValue);
			}
			return returnMap;
		} catch (Exception e) {
			throw new RuntimeException(String.format("Exception converting %s to map.", jsonString), e);
		}
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

	private static Object getArrayNestedValue(String searchField, int field_ndx, ArrayNode arrayNode) {
		List<Object> returnValue = new ArrayList<>();
		JsonNode tempNode = null;
		try {
			if (arrayNode.size() < 1) {
				// No Array Elements so return null
				return null;
			}

			for (int i = 0; i < arrayNode.size(); i++) {
				tempNode = arrayNode.get(i);
				if (tempNode == null) {
					return returnValue;
				} else if (tempNode.getClass() == ObjectNode.class) {
					returnValue.add(getJsonValue(tempNode, searchField, field_ndx));
				} else {
					if (tempNode.getClass() == TextNode.class) {
						returnValue.add(tempNode.asText());
					} else {
						returnValue.add(tempNode.toString());
					}
				}
			}

			return returnValue;
		} catch (Exception e) {
			logger.warn("getArrayNestedValue - Exception getting Json value from field ({}), exception: {}", searchField, e.getMessage());
			return null;
		}
	}

	private static Object getJsonValue(JsonNode jsonNode, String fieldName) {
		return getJsonValue(jsonNode, fieldName, 0);
	}

	private static Object getJsonValue(JsonNode jsonNode, String fieldName, int field_ndx) {

		Object returnValue = null;
		String[] fields = fieldName.split("\\|");
		JsonNode tempNode = jsonNode;
		JsonNode foundNode = null;
		try {
			for (int i = field_ndx; i < fields.length; i++) {
				foundNode = tempNode.get(fields[i]);
				if (foundNode == null) {
					logger.debug("No element found for {}", fields.toString());
				} else if (foundNode.getClass() == ObjectNode.class) {
					tempNode = foundNode;
				} else if (foundNode.getClass() == ArrayNode.class) {
					returnValue = getArrayNestedValue(fieldName, i + 1, (ArrayNode) foundNode);
					if (returnValue == null) {
						logger.debug("No element found for {}", fields.toString());
					}
					return returnValue;
				} else {
					if (foundNode.getClass() == TextNode.class) {
						return foundNode.asText();
					} else {
						return foundNode.toString();
					}
				}
			}
			if (foundNode != null) {
				returnValue = foundNode.toString();
			}
			return returnValue;
		} catch (Exception e) {
			logger.warn("getJsonValue - Exception getting Json value from field ({}), exception: {}", fieldName, e.getMessage());
			logger.warn(" field_ndx {},  jsonNode {}", field_ndx, jsonNode.asText());
			return null;
		}
	}

}
