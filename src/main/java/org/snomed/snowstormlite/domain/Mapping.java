package org.snomed.snowstormlite.domain;

import static java.lang.String.format;

public class Mapping {

	private String refsetId;
	private String code;
	private String correlation;
	private String message;

	public Mapping(String refsetId, String code, String correlation, String message) {
		this.refsetId = refsetId;
		this.code = code;
		this.correlation = correlation;
		this.message = message;
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

	public String toIndexString() {
		return format("%s|%s|%s|%s", refsetId, code.replace("|", "%7C"), stringToIndex(correlation), stringToIndex(message));
	}

	public static Mapping fromIndexString(String indexString) {
		String[] split = indexString.split("\\|");
		return new Mapping(split[0], split[1].replace("%7C", "|"), getStringFromIndex(split, 2), getStringFromIndex(split, 3));
	}

	private String stringToIndex(String value) {
		return value == null ? "_" : value;
	}

	private static String getStringFromIndex(String[] split, int i) {
		if (split.length > i) {
			String value = split[i];
			if (!value.equals("_")) {
				return value;
			}
		}
		return null;
	}
}
