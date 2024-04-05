package org.snomed.snowstormlite.domain;

import static java.lang.String.format;

public class FHIRMapping {

	private String refsetId;
	private String code;
	private String correlation;
	private String message;
	private boolean inverse;

	public FHIRMapping(String refsetId, String code, String correlation, String message, boolean inverse) {
		this.refsetId = refsetId;
		this.code = code;
		this.correlation = correlation;
		this.message = message;
		this.inverse = inverse;
	}

	public String getRefsetId() {
		return refsetId;
	}

	public String getCode() {
		return code;
	}

	public String getCorrelation() {
		return correlation;
	}

	public String getMessage() {
		return message;
	}

	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public void setCorrelation(String correlation) {
		this.correlation = correlation;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public boolean isInverse() {
		return inverse;
	}

	public String toIndexString() {
		return format("%s|%s|%s|%s|%s", refsetId, stringToIndex(code), stringToIndex(correlation), stringToIndex(message), inverse ? "1" : "0");
	}

	public static FHIRMapping fromIndexString(String indexString) {
		String[] split = indexString.split("\\|");
		return new FHIRMapping(split[0], getStringFromIndex(split, 1), getStringFromIndex(split, 2), getStringFromIndex(split, 3),
				"1".equals(split[4]));
	}

	private String stringToIndex(String value) {
		return value == null ? "_" : value.replace("|", "%7C");
	}

	private static String getStringFromIndex(String[] split, int i) {
		if (split.length > i) {
			String value = split[i];
			if (!value.equals("_")) {
				return value.replace("%7C", "|");
			}
		}
		return null;
	}
}
