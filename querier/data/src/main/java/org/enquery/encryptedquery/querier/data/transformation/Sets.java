package org.enquery.encryptedquery.querier.data.transformation;

import java.util.HashSet;
import java.util.Set;

import org.enquery.encryptedquery.querier.data.entity.json.Resource;

public class Sets {

	public static Set<Resource> unionOf(Set<Resource> set, Resource... resources) {
		Set<Resource> result = new HashSet<>();
		for (Resource r : resources) {
			if (r != null) result.add(r);
		}
		if (set != null) result.addAll(set);
		return result.isEmpty() ? null : result;
	}

	public static Set<Resource> union(Set<Resource> set1, Set<Resource> set2) {
		if (set1 == null && set2 == null) return null;

		Set<Resource> result = new HashSet<>();
		if (set1 != null) result.addAll(set1);
		if (set2 != null) result.addAll(set2);

		return result.isEmpty() ? null : result;
	}
}
