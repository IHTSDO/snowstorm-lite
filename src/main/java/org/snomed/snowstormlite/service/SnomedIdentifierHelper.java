package org.snomed.snowstormlite.service;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

public class SnomedIdentifierHelper {

	public static final Pattern SCTID_PATTERN = Pattern.compile("\\d{6,18}");

	public static boolean isConceptId(String sctid) {
		return sctid != null && SCTID_PATTERN.matcher(sctid).matches() && "0".equals(getPartitionIdPart(sctid));
	}

	private static String getPartitionIdPart(String sctid) {
		return !StringUtils.isEmpty(sctid) && sctid.length() > 4 ? sctid.substring(sctid.length() - 2, sctid.length() - 1) : null;
	}
}
