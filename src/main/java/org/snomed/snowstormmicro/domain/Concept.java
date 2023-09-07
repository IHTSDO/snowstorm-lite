package org.snomed.snowstormmicro.domain;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.snomed.snowstormmicro.fhir.FHIRConstants;

import java.util.*;

import static java.lang.String.format;
import static org.snomed.snowstormmicro.fhir.FHIRConstants.PREFERED_FOR_LANGUAGE_CODING;
import static org.snomed.snowstormmicro.fhir.FHIRHelper.createProperty;

public class Concept {

	public static final String DOC_TYPE = "concept";

	public interface FieldNames {
		String ID = "id";
		String ACTIVE = "active";
		String EFFECTIVE_TIME = "effective_time";
		String MODULE = "module";
		String DEFINED = "defined";
		String ACTIVE_SORT = "active_sort";
		String PARENTS = "parents";
		String ANCESTORS = "ancestors";
		String MEMBERSHIP = "membership";
		String TERM = "term";
		String TERM_STORED = "term_stored";
		String PT_AND_FSN_TERM_LENGTH = "pt_term_len";
	}
	private String conceptId;
	private boolean active;
	private String effectiveTime;
	private String moduleId;
	private boolean defined;
	private List<Description> descriptions;

	private Set<Concept> parents;
	private Set<String> parentCodes;
	private Set<String> ancestorCodes;
	private Set<String> membership;

	public Concept() {
		descriptions = new ArrayList<>();
		parents = new HashSet<>();
		parentCodes = new HashSet<>();
		ancestorCodes = new HashSet<>();
		membership = new HashSet<>();
	}

	public Concept(String conceptId, String effectiveTime, boolean active, String moduleId, boolean defined) {
		this();
		this.conceptId = conceptId;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.moduleId = moduleId;
		this.defined = defined;
	}

	public Parameters toHapi(CodeSystem codeSystem) {
		Parameters parameters = new Parameters();
		parameters.addParameter("code", getConceptId());
		parameters.addParameter("display", getPT());
		parameters.addParameter("name", codeSystem.getTitle());
		parameters.addParameter("system", FHIRConstants.SNOMED_URI);
		parameters.addParameter("version", codeSystem.getVersionUri());
		parameters.addParameter(createProperty("moduleId", getModuleId(), true));
		parameters.addParameter(createProperty("inactive", !active, false));
		parameters.addParameter(createProperty("effectiveTime", getEffectiveTime(), false));
		parameters.addParameter(createProperty("sufficientlyDefined", defined, false));

		for (Description description : descriptions) {
			Parameters.ParametersParameterComponent designation = new Parameters.ParametersParameterComponent().setName("designation");
			designation.addPart().setName("language").setValue(new CodeType(description.getLang()));
			if (description.isFsn()) {
				designation.addPart().setName("use").setValue(FHIRConstants.FSN_CODING);
			} else if (!description.getPreferredLangRefsets().isEmpty()) {
				designation.addPart().setName("use").setValue(FHIRConstants.FOR_DISPLAY_CODING);
			} else {
				designation.addPart().setName("use").setValue(FHIRConstants.SYNONYM_CODING);
			}
			designation.addPart().setName("value").setValue(new StringType(description.getTerm()));
			parameters.addParameter(designation);
			for (String preferredLangRefset : description.getPreferredLangRefsets()) {
				Parameters.ParametersParameterComponent preferredDesignation = new Parameters.ParametersParameterComponent().setName("designation");
				preferredDesignation.addPart().setName("language").setValue(new CodeType(getLangAndRefsetCode(description.getLang(), preferredLangRefset)));
				preferredDesignation.addPart().setName("use").setValue(PREFERED_FOR_LANGUAGE_CODING);
				preferredDesignation.addPart().setName("value").setValue(new StringType(description.getTerm()));
				parameters.addParameter(preferredDesignation);
			}
		}

		return parameters;
	}

	private String getLangAndRefsetCode(String lang, String preferredLangRefset) {
		StringBuilder builder = new StringBuilder();
		int eightMax = 0;
		for (int i = 0; i < preferredLangRefset.length(); i++) {
			if (eightMax == 8) {
				builder.append("-");
				eightMax = 0;
			}
			builder.append(preferredLangRefset.charAt(i));
			eightMax++;
		}
		return format("%s-x-sctlang-%s", lang, builder);
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

	public void addParentCode(String parentCode) {
		parentCodes.add(parentCode);
	}

	public void addAncestorCode(String ancestorCode) {
		ancestorCodes.add(ancestorCode);
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

	public Set<Concept> getParents() {
		return parents;
	}

	public void setParents(Set<Concept> parents) {
		this.parents = parents;
	}

	public Set<String> getAncestorCodes() {
		return ancestorCodes;
	}

	public void setAncestorCodes(Set<String> ancestorCodes) {
		this.ancestorCodes = ancestorCodes;
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
