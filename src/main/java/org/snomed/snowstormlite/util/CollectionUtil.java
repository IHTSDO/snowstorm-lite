package org.snomed.snowstormlite.util;

import java.util.HashMap;
import java.util.Map;

public class CollectionUtil {

	/**
	 * Build a map of any size (JDK 17 builder limited to 10 pairs)
	 * @param firstKey
	 * @param firstValue
	 * @param additionalKeyValuePairs
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
