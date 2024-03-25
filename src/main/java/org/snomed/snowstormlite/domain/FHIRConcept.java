package org.snomed.snowstormlite.domain;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.snomed.snowstormlite.service.NormalFormBuilder;
import org.snomed.snowstormlite.service.TermProvider;

import java.io.IOException;
import java.util.*;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRConstants.PREFERED_FOR_LANGUAGE_CODING;
import static org.snomed.snowstormlite.fhir.FHIRHelper.createProperty;

public class FHIRConcept {

	public static final String DOC_TYPE = "concept";

	public interface FieldNames {
		String ID = "id";
		String ACTIVE = "active";
		String EFFECTIVE_TIME = "effective_time";
		String MODULE = "module";
		String DEFINED = "defined";
		String ACTIVE_SORT = "active_sort";
		String REL_STORED = "rel_stored";
		String PARENTS = "parents";
		String ANCESTORS = "ancestors";
		String CHILDREN = "children";
		String ATTRIBUTE_PREFIX = "at_";
		String MEMBERSHIP = "membership";
		String MAPPING = "mapping";
		String TERM = "term";
		String TERM_STORED = "term_stored";
		String PT_AND_FSN_TERM_LENGTH = "pt_term_len";
	}
	private String conceptId;
	private boolean active;
	private String effectiveTime;
	private String moduleId;
	private boolean defined;
	private List<FHIRDescription> descriptions;
	private Set<FHIRConcept> parents;

	private Set<String> parentCodes;
	private Set<String> ancestorCodes;
	private Set<String> childCodes;
	private Set<String> membership;
	private final List<FHIRRelationship> relationships;
	private final List<FHIRMapping> mappings;

	public FHIRConcept() {
		descriptions = new ArrayList<>();
		parents = new HashSet<>();
		parentCodes = new HashSet<>();
		ancestorCodes = new HashSet<>();
		childCodes = new HashSet<>();
		membership = new HashSet<>();
		relationships = new ArrayList<>();
		mappings = new ArrayList<>();
	}

	public FHIRConcept(String conceptId, String effectiveTime, boolean active, String moduleId, boolean defined) {
		this();
		this.conceptId = conceptId;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.moduleId = moduleId;
		this.defined = defined;
	}

	public Parameters toHapi(FHIRCodeSystem codeSystem, TermProvider termProvider, List<LanguageDialect> languageDialects) throws IOException {
		Parameters parameters = new Parameters();
		parameters.addParameter(new Parameters.ParametersParameterComponent().setName("code").setValue(new CodeType(getConceptId())));
		parameters.addParameter("display", getPT(languageDialects));
		parameters.addParameter("name", codeSystem.getTitle());
		parameters.addParameter(new Parameters.ParametersParameterComponent().setName("system").setValue(new UriType(FHIRConstants.SNOMED_URI)));
		parameters.addParameter(new Parameters.ParametersParameterComponent().setName("version").setValue(new StringType(codeSystem.getVersionUri())));
		for (String parentCode : parentCodes) {
			parameters.addParameter(createProperty("parent", parentCode, true));
		}
		for (String childCode : childCodes) {
			parameters.addParameter(createProperty("child", childCode, true));
		}
		parameters.addParameter(createProperty("moduleId", getModuleId(), true));
		parameters.addParameter(createProperty("inactive", !active, false));
		parameters.addParameter(createProperty("sufficientlyDefined", defined, false));
		parameters.addParameter(createProperty("effectiveTime", getEffectiveTime(), false));

		for (FHIRDescription description : descriptions) {
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

		if (active) {
			parameters.addParameter(createProperty("normalFormTerse", getNormalFormTerse(), false));
			parameters.addParameter(createProperty("normalForm", getNormalForm(termProvider, languageDialects), false));
		}

		return parameters;
	}

	public void addRelationship(int group, Long type, Long target, String concreteValue) {
		relationships.add(new FHIRRelationship(group, type, target, concreteValue));
	}

	public void addRelationship(int group, String type, String targetOrValue) {
		if (targetOrValue.startsWith("#")) {
			addRelationship(group, Long.parseLong(type), null, targetOrValue);
		} else {
			addRelationship(group, Long.parseLong(type), Long.parseLong(targetOrValue), null);
		}
	}

	public void addMapping(FHIRMapping mapping) {
		mappings.add(mapping);
	}

	public String getNormalFormTerse() throws IOException {
		return getNormalForm(null, null);
	}

	public String getNormalForm(TermProvider termProvider, List<LanguageDialect> languageDialects) throws IOException {
		return NormalFormBuilder.getNormalForm(this, termProvider, languageDialects);
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

	public String getPT(List<LanguageDialect> displayLanguages) {
		for (LanguageDialect displayLanguage : displayLanguages) {
			for (FHIRDescription description : descriptions) {
				Long displayLangRefset = displayLanguage.getLanguageReferenceSet();
				if (!description.isFsn() && description.getLang().equals(displayLanguage.getLanguageCode())
					&& (displayLangRefset == null || description.getPreferredLangRefsets().contains(displayLangRefset.toString()))) {
					return description.getTerm();
				}
			}
		}
		for (FHIRDescription description : descriptions) {
			if (!description.isFsn() && !description.getPreferredLangRefsets().isEmpty()) {
				return description.getTerm();
			}
		}
		return null;
	}

	public void addDescription(FHIRDescription description) {
		descriptions.add(description);
	}

	public void addParentCode(String parentCode) {
		parentCodes.add(parentCode);
	}

	public void addAncestorCode(String ancestorCode) {
		ancestorCodes.add(ancestorCode);
	}

	public void addChildCode(String code) {
		childCodes.add(code);
	}

	public void addParent(FHIRConcept parent) {
		parents.add(parent);
		parent.addChildCode(conceptId);
		addParentCode(parent.getConceptId());
	}

	public void addMembership(String refsetId) {
		membership.add(refsetId);
	}

	public Set<String> getAncestors() {
		return getAncestors(new HashSet<>());
	}

	private Set<String> getAncestors(Set<String> ancestors) {
		for (FHIRConcept parent : parents) {
			ancestors.add(parent.getConceptId());
			parent.getAncestors(ancestors);
		}
		return ancestors;
	}

	public Set<Long> getDescendants(Map<Long, FHIRConcept> allConceptsMap) {
		return getDescendants(new HashSet<>(), allConceptsMap);
	}

	private Set<Long> getDescendants(Set<Long> descendants, Map<Long, FHIRConcept> concepts) {
		for (String childCode : childCodes) {
			long childId = Long.parseLong(childCode);
			descendants.add(childId);
			concepts.get(childId).getDescendants(descendants, concepts);
		}
		return descendants;
	}


	public Map<Integer, Set<FHIRRelationship>> getRelationships() {
		Map<Integer, Set<FHIRRelationship>> relationshipMap = new HashMap<>();
		for (FHIRRelationship relationship : relationships) {
			relationshipMap.computeIfAbsent(relationship.getGroup(), i -> new TreeSet<>()).add(relationship);
		}
		return relationshipMap;
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

	public List<FHIRDescription> getDescriptions() {
		return descriptions;
	}

	public void setDescriptions(List<FHIRDescription> descriptions) {
		this.descriptions = descriptions;
	}

	public Set<String> getParentCodes() {
		return parentCodes;
	}

	public void setParentCodes(Set<String> parentCodes) {
		this.parentCodes = parentCodes;
	}

	public Set<FHIRConcept> getParents() {
		return parents;
	}

	public void setParents(Set<FHIRConcept> parents) {
		this.parents = parents;
	}

	public Set<String> getAncestorCodes() {
		return ancestorCodes;
	}

	public void setAncestorCodes(Set<String> ancestorCodes) {
		this.ancestorCodes = ancestorCodes;
	}

	public Set<String> getChildCodes() {
		return childCodes;
	}

	public void setChildCodes(Set<String> childCodes) {
		this.childCodes = childCodes;
	}

	public Set<String> getMembership() {
		return membership;
	}

	public void setMembership(Set<String> membership) {
		this.membership = membership;
	}

	public List<FHIRMapping> getMappings() {
		return mappings;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		FHIRConcept concept = (FHIRConcept) o;
		return Objects.equals(conceptId, concept.conceptId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(conceptId);
	}
}
