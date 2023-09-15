package org.snomed.snowstormmicro.snomedimport;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.domain.Concepts;

import java.util.HashMap;
import java.util.Map;

public class ComponentFactoryWithoutDescriptions extends ImpotentComponentFactory {

	private final Map<Long, Concept> conceptMap;
	private final Concept dummyConcept;
	private Integer maxDate = null;

	public ComponentFactoryWithoutDescriptions() {
		conceptMap = new Long2ObjectOpenHashMap<>();
		dummyConcept = new Concept();
	}

	@Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		conceptMap.put(Long.parseLong(conceptId), new Concept(conceptId, effectiveTime, active.equals("1"), moduleId, Concepts.DEFINED.equals(definitionStatusId)));
		collectMaxEffectiveTime(effectiveTime);
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		collectMaxEffectiveTime(effectiveTime);
	}

	@Override
	public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		if (active.equals("1") && !characteristicTypeId.equals(Concepts.STATED_RELATIONSHIP)) {
			if (typeId.equals(Concepts.IS_A)) {
				Concept parent = conceptMap.get(Long.parseLong(destinationId));
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
		if (active.equals("1")) {
			if (fieldNames.length == 6) {
				// Active simple refset member
				conceptMap.getOrDefault(Long.parseLong(referencedComponentId), dummyConcept).addMembership(refsetId);
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

	public Map<Long, Concept> getConceptMap() {
		return conceptMap;
	}

	public Integer getMaxDate() {
		return maxDate;
	}
}
