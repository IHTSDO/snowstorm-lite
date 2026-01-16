package org.snomed.snowstormlite.mcp.model;

import java.util.List;
import java.util.Set;

public class ConceptDetails {
	private String conceptId;
	private String display;            // PT in requested language
	private String fsn;                // Fully Specified Name
	private boolean active;
	private String effectiveTime;
	private String moduleId;
	private boolean defined;
	private List<Description> descriptions;
	private Set<String> parentCodes;
	private Set<String> childCodes;
	private Set<String> ancestorCodes;
	private String normalForm;

	public ConceptDetails() {
	}

	private ConceptDetails(Builder builder) {
		this.conceptId = builder.conceptId;
		this.display = builder.display;
		this.fsn = builder.fsn;
		this.active = builder.active;
		this.effectiveTime = builder.effectiveTime;
		this.moduleId = builder.moduleId;
		this.defined = builder.defined;
		this.descriptions = builder.descriptions;
		this.parentCodes = builder.parentCodes;
		this.childCodes = builder.childCodes;
		this.ancestorCodes = builder.ancestorCodes;
		this.normalForm = builder.normalForm;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String conceptId;
		private String display;
		private String fsn;
		private boolean active;
		private String effectiveTime;
		private String moduleId;
		private boolean defined;
		private List<Description> descriptions;
		private Set<String> parentCodes;
		private Set<String> childCodes;
		private Set<String> ancestorCodes;
		private String normalForm;

		public Builder conceptId(String conceptId) {
			this.conceptId = conceptId;
			return this;
		}

		public Builder display(String display) {
			this.display = display;
			return this;
		}

		public Builder fsn(String fsn) {
			this.fsn = fsn;
			return this;
		}

		public Builder active(boolean active) {
			this.active = active;
			return this;
		}

		public Builder effectiveTime(String effectiveTime) {
			this.effectiveTime = effectiveTime;
			return this;
		}

		public Builder moduleId(String moduleId) {
			this.moduleId = moduleId;
			return this;
		}

		public Builder defined(boolean defined) {
			this.defined = defined;
			return this;
		}

		public Builder descriptions(List<Description> descriptions) {
			this.descriptions = descriptions;
			return this;
		}

		public Builder parentCodes(Set<String> parentCodes) {
			this.parentCodes = parentCodes;
			return this;
		}

		public Builder childCodes(Set<String> childCodes) {
			this.childCodes = childCodes;
			return this;
		}

		public Builder ancestorCodes(Set<String> ancestorCodes) {
			this.ancestorCodes = ancestorCodes;
			return this;
		}

		public Builder normalForm(String normalForm) {
			this.normalForm = normalForm;
			return this;
		}

		public ConceptDetails build() {
			return new ConceptDetails(this);
		}
	}

	public static class Description {
		private String term;
		private String language;
		private String type;  // "FSN", "PT", "SYNONYM"

		public Description() {
		}

		public Description(String term, String language, String type) {
			this.term = term;
			this.language = language;
			this.type = type;
		}

		public String getTerm() {
			return term;
		}

		public void setTerm(String term) {
			this.term = term;
		}

		public String getLanguage() {
			return language;
		}

		public void setLanguage(String language) {
			this.language = language;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}
	}

	// Getters and setters
	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public String getDisplay() {
		return display;
	}

	public void setDisplay(String display) {
		this.display = display;
	}

	public String getFsn() {
		return fsn;
	}

	public void setFsn(String fsn) {
		this.fsn = fsn;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public boolean isDefined() {
		return defined;
	}

	public void setDefined(boolean defined) {
		this.defined = defined;
	}

	public List<Description> getDescriptions() {
		return descriptions;
	}

	public void setDescriptions(List<Description> descriptions) {
		this.descriptions = descriptions;
	}

	public Set<String> getParentCodes() {
		return parentCodes;
	}

	public void setParentCodes(Set<String> parentCodes) {
		this.parentCodes = parentCodes;
	}

	public Set<String> getChildCodes() {
		return childCodes;
	}

	public void setChildCodes(Set<String> childCodes) {
		this.childCodes = childCodes;
	}

	public Set<String> getAncestorCodes() {
		return ancestorCodes;
	}

	public void setAncestorCodes(Set<String> ancestorCodes) {
		this.ancestorCodes = ancestorCodes;
	}

	public String getNormalForm() {
		return normalForm;
	}

	public void setNormalForm(String normalForm) {
		this.normalForm = normalForm;
	}
}
