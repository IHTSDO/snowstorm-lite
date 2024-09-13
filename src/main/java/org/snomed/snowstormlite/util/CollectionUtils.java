package org.snomed.snowstormlite.util;

import java.util.*;

public class CollectionUtils {

	public static <T> Set<T> orEmpty(Set<T> collection) {
		return collection != null ? collection : Collections.emptySet();
	}

	public static <T> List<T> orEmpty(List<T> collection) {
		return collection != null ? collection : Collections.emptyList();
	}

	/**
	 * Build a map of any size (JDK 17 builder limited to 10 pairs)
	 *
	 * @param <A> the {@code Map}'s key type
	 * @param <B> the {@code Map}'s value type
	 * @param firstKey the mapping's key
	 * @param firstValue the mapping's value
	 * @param additionalKeyValuePairs additional key value pairs
	 * @return A map created using the first and addition key value pairs
	 */
	public static <A, B> Map<A, B> mapOf(A firstKey, B firstValue, Object... additionalKeyValuePairs) {
		Map<A, B> map = new HashMap<>();
		map.put(firstKey, firstValue);
		A key = null;
		for (Object more : additionalKeyValuePairs) {
			if (key == null) {
				//noinspection unchecked
				key = (A) more;
			} else {
				//noinspection unchecked
				map.put(key, (B) more);
				key = null;
			}
		}
		return map;
	}
}
