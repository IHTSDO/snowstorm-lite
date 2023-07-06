package org.snomed.snowstormmicro.domain;

import java.util.HashSet;
import java.util.Set;

public class Description {

	private String id;
	private String term;
	private String lang;
	private boolean fsn;
	private Set<String> preferredLangRefsets;

	public Description() {
		preferredLangRefsets = new HashSet<>();
	}

	public Description(String id, String languageCode, boolean fsn, String term) {
		this();
		this.id = id;
		this.lang = languageCode;
		this.fsn = fsn;
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

	public Set<String> getPreferredLangRefsets() {
		return preferredLangRefsets;
	}

	public void setPreferredLangRefsets(Set<String> preferredLangRefsets) {
		this.preferredLangRefsets = preferredLangRefsets;
	}
}
