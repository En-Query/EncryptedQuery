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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JSONStringConverter {

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

	// public static String toString(Map<String, Object> map) {
	// if (map == null) return null;
	// try {
	// return objectMapper.writeValueAsString(map);
	// } catch (Exception e) {
	// throw new RuntimeException(
	// String.format("Exception converting map %s to string.", map),
	// e);
	// }
	// }

}
