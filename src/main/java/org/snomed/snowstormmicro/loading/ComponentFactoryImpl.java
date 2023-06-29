package org.snomed.snowstormmicro.loading;

import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.domain.Concepts;
import org.snomed.snowstormmicro.domain.Description;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComponentFactoryImpl extends ImpotentComponentFactory {

	private final Map<Long, Concept> conceptMap;
	private final Concept dummyConcept;

	public ComponentFactoryImpl() {
		conceptMap = new HashMap<>();
		dummyConcept = new Concept();
	}

	@Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		conceptMap.put(Long.parseLong(conceptId), new Concept(conceptId, active.equals("1")));
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		if (active.equals("1") && (typeId.equals(Concepts.FSN) || typeId.equals(Concepts.SYNONYM))) {
			conceptMap.getOrDefault(Long.parseLong(conceptId), dummyConcept).addDescription(new Description(id, languageCode, typeId.equals(Concepts.FSN), term));
		}
	}

	@Override
	public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		if (active.equals("1") && typeId.equals(Concepts.IS_A) && characteristicTypeId.equals(Concepts.INFERRED_RELATIONSHIP)) {
			Concept parent = conceptMap.get(Long.parseLong(destinationId));
			if (parent != null) {
				conceptMap.getOrDefault(Long.parseLong(sourceId), dummyConcept).addParent(parent);
			}
		}
	}

	@Override
	public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		if (active.equals("1")) {
			if (fieldNames.length == 0) {
				// Active simple refset member
				conceptMap.getOrDefault(Long.parseLong(referencedComponentId), dummyConcept).addMembership(refsetId);
			} else if (fieldNames.length == 1 && fieldNames[0].equals("acceptabilityId")) {
				// Active lang refset member
				List<Description> descriptions = conceptMap.getOrDefault(Long.parseLong(referencedComponentId), dummyConcept).getDescriptions();
				for (Description description : descriptions) {
					if (description.getId().equals(referencedComponentId)) {
						description.getAcceptability().put(refsetId, otherValues[0]);
					}
				}
			}
		}
	}

	public Map<Long, Concept> getConceptMap() {
		return conceptMap;
	}
}
