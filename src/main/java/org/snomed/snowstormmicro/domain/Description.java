package org.snomed.snowstormmicro.domain;

import java.util.HashMap;
import java.util.Map;

public class Description {

	private String id;
	private String term;
	private String lang;
	private boolean fsn;
	private Map<String, String> acceptability;

	public Description() {
	}

	public Description(String id, String languageCode, boolean fsn, String term) {
		this.id = id;
		this.lang = languageCode;
		this.fsn = fsn;
		this.term = term;
		acceptability = new HashMap<>();
	}

	public Description(String term) {
		this.term = term;
	}

	public String getId() {
		return id;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public boolean isFsn() {
		return fsn;
	}

	public void setFsn(boolean fsn) {
		this.fsn = fsn;
	}

	public Map<String, String> getAcceptability() {
		return acceptability;
	}

	public void setAcceptability(Map<String, String> acceptability) {
		this.acceptability = acceptability;
	}
}
