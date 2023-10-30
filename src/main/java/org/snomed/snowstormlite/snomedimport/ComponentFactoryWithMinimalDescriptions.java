package org.snomed.snowstormlite.snomedimport;

import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.Concepts;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComponentFactoryWithMinimalDescriptions extends ComponentFactory {

	private ConceptMapBuilderFactory conceptMapBuilderFactory;
	private Integer maxDate = null;
	private Set<Long> conceptsInScope;

	public ComponentFactoryWithMinimalDescriptions() {
	}

	@Override
	boolean isDescriptionInScope(Long conceptId) {
		if (conceptsInScope == null) {
			FHIRConcept concept = conceptMap.get(Concepts.REFERENCE_SET_ATTRIBUTE);
			if (concept != null) {
				conceptsInScope = concept.getDescendants(conceptMap);
			} else {
				conceptsInScope = new HashSet<>();
			}
		}
		return conceptsInScope.contains(conceptId);
	}

	@Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		conceptMap.put(Long.parseLong(conceptId), new FHIRConcept(conceptId, effectiveTime, active.equals("1"), moduleId, Concepts.DEFINED.equals(definitionStatusId)));
		collectMaxEffectiveTime(effectiveTime);
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		super.newDescriptionState(id, effectiveTime, active, moduleId, conceptId, languageCode, typeId, term, caseSignificanceId);
		collectMaxEffectiveTime(effectiveTime);
	}

	@Override
	public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		if (active.equals("1") && !characteristicTypeId.equals(Concepts.STATED_RELATIONSHIP)) {
			if (typeId.equals(Concepts.IS_A)) {
				FHIRConcept parent = conceptMap.get(Long.parseLong(destinationId));
				if (parent != null) {
					conceptMap.getOrDefault(Long.parseLong(sourceId), dummyConcept).addParent(parent);
				}
			} else {
				conceptMap.getOrDefault(Long.parseLong(sourceId), dummyConcept)
						.addRelationship(Integer.parseInt(relationshipGroup), Long.parseLong(typeId), Long.parseLong(destinationId), null);
			}
		}
		collectMaxEffectiveTime(effectiveTime);
	}

	@Override
	public void newConcreteRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String value, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		if (active.equals("1") && !characteristicTypeId.equals(Concepts.STATED_RELATIONSHIP)) {
			conceptMap.getOrDefault(Long.parseLong(sourceId), dummyConcept)
					.addRelationship(Integer.parseInt(relationshipGroup), Long.parseLong(typeId), null, value);
		}
	}

	@Override
	public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		super.newReferenceSetMemberState(fieldNames, id, effectiveTime, active, moduleId, refsetId, referencedComponentId, otherValues);
		if (active.equals("1")) {
			if (fieldNames.length == 6) {
				// Active simple refset member
				conceptMap.getOrDefault(Long.parseLong(referencedComponentId), dummyConcept).addMembership(refsetId);
			} else {
				String fieldSixName = fieldNames[6];
				if (fieldSixName.equals("targetComponentId") || fieldSixName.contains("map")) {
					if (conceptMapBuilderFactory == null) {
						conceptMapBuilderFactory = new ConceptMapBuilderFactory(conceptMap);
					}
					long refsetIdLong = Long.parseLong(refsetId);
					ConceptMapBuilderFactory.ConceptMapBuilder mapBuilder = conceptMapBuilderFactory.getMapBuilder(refsetIdLong);
					if (mapBuilder != null) {
						mapBuilder.addMapping(refsetId, referencedComponentId, otherValues);
					}
				}
			}
		}
		collectMaxEffectiveTime(effectiveTime);
	}

	public void clearDescriptions() {
		conceptMap.values().forEach(concept -> concept.getDescriptions().clear());
	}

	private void collectMaxEffectiveTime(String effectiveTime) {
		if (maxDate == null || (effectiveTime != null && Integer.parseInt(effectiveTime) > maxDate)) {
			maxDate = Integer.parseInt(effectiveTime);
		}
	}

	public Map<Long, FHIRConcept> getConceptMap() {
		return conceptMap;
	}

	public Integer getMaxDate() {
		return maxDate;
	}
}
