package org.snomed.snowstormlite.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class FHIRDescription {

	private String id;
	private String term;
	private String lang;
	private boolean fsn;

	private Set<String> preferredLangRefsets;
	private Set<String> acceptableLangRefsets;
	private FHIRConcept concept;

	public FHIRDescription() {
		id = UUID.randomUUID().toString();
		preferredLangRefsets = new HashSet<>();
		acceptableLangRefsets = new HashSet<>();
	}

	public FHIRDescription(String id, String languageCode, boolean fsn, String term) {
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

	@JsonIgnore
	public int getTermLength() {
		return term.length();
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

	public Set<String> getAcceptableLangRefsets() {
		return acceptableLangRefsets;
	}

	public void setAcceptableLangRefsets(Set<String> acceptableLangRefsets) {
		this.acceptableLangRefsets = acceptableLangRefsets;
	}

	@JsonIgnore
	public FHIRConcept getConcept() {
		return concept;
	}

	public void setConcept(FHIRConcept concept) {
		this.concept = concept;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		FHIRDescription that = (FHIRDescription) o;
		return Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
