package org.enquery.encryptedquery.querier.data.transformation;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.enquery.encryptedquery.querier.data.entity.json.Resource;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JSONConverter {

	private static final TypeReference<Map<String, Object>> MAP_STRING_OBJ_TYPE_REF = new TypeReference<Map<String, Object>>() {};
	private static final TypeReference<Map<String, String>> MAP_STRING_STRING_TYPE_REF = new TypeReference<Map<String, String>>() {};
	private static final TypeReference<List<String>> LIST_STRING_TYPE_REF = new TypeReference<List<String>>() {};
	private static final ObjectMapper objectMapper = new ObjectMapper();
	{
		objectMapper.setSerializationInclusion(Include.NON_NULL);
	}

	public static Collection<Map<String, Object>> objectsToCollectionOfMaps(Resource... objects) throws UnsupportedEncodingException, URISyntaxException {
		Validate.notNull(objects);
		if (objects.length <= 0) return null;
		if (Arrays.stream(objects).allMatch(o -> o == null)) return null;

		final Set<Resource> set = new HashSet<>(Arrays.asList(objects));
		return objectsToCollectionOfMaps(set);
	}

	public static Collection<Map<String, Object>> objectsToCollectionOfMaps(final Set<Resource> set) {
		if (set == null) return null;

		Collection<Map<String, Object>> result = new ArrayList<>();
		set.stream()
				.filter(o -> o != null)
				.forEach(o -> result.add(toMap(o)));

		return result.isEmpty() ? null : result;
	}

	public static Map<String, Object> toMap(Object json) {
		Map<String, Object> returnMap = null;
		if (json == null) return null;
		try {
			returnMap = objectMapper.convertValue(json, MAP_STRING_OBJ_TYPE_REF);
		} catch (Exception e) {
			throw new RuntimeException("Exception converting " + json + " to map.", e);
		}
		return returnMap;
	}

	public static Map<String, String> toMapStringString(String json) {
		Map<String, String> returnMap = null;
		if (json == null) return null;
		try {
			returnMap = objectMapper.readValue(json, MAP_STRING_STRING_TYPE_REF);
		} catch (Exception e) {
			throw new RuntimeException("Exception converting " + json + " to map.", e);
		}
		return returnMap;
	}

	public static List<String> toList(String json) {
		if (json == null) return null;
		try {
			return objectMapper.readValue(json, LIST_STRING_TYPE_REF);
		} catch (Exception e) {
			throw new RuntimeException("Exception converting " + json + " to list.", e);
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

	public static String toString(List<String> list) {
		if (list == null) return null;
		try {
			return objectMapper.writeValueAsString(list);
		} catch (Exception e) {
			throw new RuntimeException("Exception converting list to string.", e);
		}

	}

}
