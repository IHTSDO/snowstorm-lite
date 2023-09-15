package org.snomed.snowstormlite.snomedimport;

import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.snowstormlite.domain.Concept;
import org.snomed.snowstormlite.domain.Concepts;
import org.snomed.snowstormlite.domain.Description;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ComponentFactoryWithDescriptionBatch extends ImpotentComponentFactory {

	private final ComponentFactoryWithoutDescriptions baseComponents;
	private final Collection<Long> conceptIdBatch;
	private final Map<Long, Description> descriptionSynonymMap;
	private final Concept dummyConcept;

	public ComponentFactoryWithDescriptionBatch(ComponentFactoryWithoutDescriptions baseComponents, Collection<Long> conceptIdBatch) {
		this.baseComponents = baseComponents;
		descriptionSynonymMap = new HashMap<>();
		dummyConcept = new Concept();
		this.conceptIdBatch = conceptIdBatch;
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		if (active.equals("1") && (typeId.equals(Concepts.FSN) || typeId.equals(Concepts.SYNONYM)) && conceptIdBatch.contains(Long.parseLong(conceptId))) {
			Description description = new Description(id, languageCode, typeId.equals(Concepts.FSN), term);
			baseComponents.getConceptMap().getOrDefault(Long.parseLong(conceptId), dummyConcept).addDescription(description);
			if (typeId.equals(Concepts.SYNONYM)) {
				descriptionSynonymMap.put(Long.parseLong(id), description);
			}
		}
	}

	@Override
	public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		if (active.equals("1")) {
			if (fieldNames.length == 7 && fieldNames[6].equals("acceptabilityId") && otherValues[0].equals(Concepts.PREFERRED)) {
				// Active lang refset member
				Description description = descriptionSynonymMap.get(Long.parseLong(referencedComponentId));
				if (description != null) {
					description.getPreferredLangRefsets().add(refsetId);
				}
			}
		}
	}

	public Map<Long, Concept> getConceptMap() {
		return baseComponents.getConceptMap();
	}

	public Collection<Long> getConceptIdBatch() {
		return conceptIdBatch;
	}
}
