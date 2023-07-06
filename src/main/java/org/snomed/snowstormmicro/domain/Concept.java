package org.snomed.snowstormmicro.domain;

import java.util.*;

public class Concept {

	public static final String DOC_TYPE = "concept";

	public interface FieldNames {
		String ID = "id";
		String ACTIVE = "active";
		String ACTIVE_SORT = "active_sort";
		String ANCESTORS = "ancestors";
		String MEMBERSHIP = "membership";
		String TERM = "term";
		String TERM_STORED = "term_stored";
		String PT_TERM_LENGTH = "pt_term_score";
		String PT_WORD_COUNT = "pt_word_count";
		String PT_STORED = "pt_stored";
		String PT = "pt";
	}
	private String conceptId;
	private boolean active;

	private List<Description> descriptions;
	private Set<Concept> parents;
	private Set<String> membership;

	public Concept() {
		descriptions = new ArrayList<>();
		parents = new HashSet<>();
		membership = new HashSet<>();
	}

	public Concept(String conceptId, boolean active) {
		this();
		this.conceptId = conceptId;
		this.active = active;
	}

	public String getPT() {
		for (Description description : descriptions) {
			if (!description.isFsn() && !description.getPreferredLangRefsets().isEmpty()) {
				return description.getTerm();
			}
		}
		return null;
	}

	public void addDescription(Description description) {
		descriptions.add(description);
	}

	public void addParent(Concept parent) {
		parents.add(parent);
	}

	public void addMembership(String refsetId) {
		membership.add(refsetId);
	}

	public Set<String> getAncestors() {
		Set<String> ancestors = new HashSet<>();
		return getAncestors(ancestors);
	}

	private Set<String> getAncestors(Set<String> ancestors) {
		for (Concept parent : parents) {
			ancestors.add(parent.getConceptId());
			parent.getAncestors(ancestors);
		}
		return ancestors;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public List<Description> getDescriptions() {
		return descriptions;
	}

	public void setDescriptions(List<Description> descriptions) {
		this.descriptions = descriptions;
	}

	public Set<Concept> getParents() {
		return parents;
	}

	public void setParents(Set<Concept> parents) {
		this.parents = parents;
	}

	public Set<String> getMembership() {
		return membership;
	}

	public void setMembership(Set<String> membership) {
		this.membership = membership;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Concept concept = (Concept) o;
		return Objects.equals(conceptId, concept.conceptId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(conceptId);
	}
}
