package org.snomed.snowstormmicro.loading;

import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.domain.Concepts;
import org.snomed.snowstormmicro.domain.Description;

import java.util.HashMap;
import java.util.Map;

public class ComponentFactoryImpl extends ImpotentComponentFactory {

	private final Map<Long, Concept> conceptMap;
	private final Map<Long, Description> descriptionSynonymMap;
	private final Concept dummyConcept;
	private Integer maxDate = null;

	public ComponentFactoryImpl() {
		conceptMap = new HashMap<>();
		descriptionSynonymMap = new HashMap<>();
		dummyConcept = new Concept();
	}

	@Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		conceptMap.put(Long.parseLong(conceptId), new Concept(conceptId, effectiveTime, active.equals("1"), moduleId, Concepts.DEFINED.equals(definitionStatusId)));
		collectMaxEffectiveTime(effectiveTime);
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		if (active.equals("1") && (typeId.equals(Concepts.FSN) || typeId.equals(Concepts.SYNONYM))) {
			Description description = new Description(id, languageCode, typeId.equals(Concepts.FSN), term);
			conceptMap.getOrDefault(Long.parseLong(conceptId), dummyConcept).addDescription(description);
			if (typeId.equals(Concepts.SYNONYM)) {
				descriptionSynonymMap.put(Long.parseLong(id), description);
			}
		}
		collectMaxEffectiveTime(effectiveTime);
	}

	@Override
	public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		if (active.equals("1") && typeId.equals(Concepts.IS_A) && characteristicTypeId.equals(Concepts.INFERRED_RELATIONSHIP)) {
			Concept parent = conceptMap.get(Long.parseLong(destinationId));
			if (parent != null) {
				conceptMap.getOrDefault(Long.parseLong(sourceId), dummyConcept).addParent(parent);
			}
		}
		collectMaxEffectiveTime(effectiveTime);
	}

	@Override
	public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		if (active.equals("1")) {
			if (fieldNames.length == 0) {
				// Active simple refset member
				conceptMap.getOrDefault(Long.parseLong(referencedComponentId), dummyConcept).addMembership(refsetId);
			} else if (fieldNames.length == 7 && fieldNames[6].equals("acceptabilityId") && otherValues[0].equals(Concepts.PREFERRED)) {
				// Active lang refset member
				Description description = descriptionSynonymMap.get(Long.parseLong(referencedComponentId));
				if (description != null) {
					description.getPreferredLangRefsets().add(refsetId);
				}
			}
		}
		collectMaxEffectiveTime(effectiveTime);
	}

	private void collectMaxEffectiveTime(String effectiveTime) {
		if (maxDate == null || (effectiveTime != null && Integer.parseInt(effectiveTime) > maxDate)) {
			maxDate = Integer.parseInt(effectiveTime);
		}
	}

	public Map<Long, Concept> getConceptMap() {
		return conceptMap;
	}

	public Integer getMaxDate() {
		return maxDate;
	}
}
