package org.snomed.snowstormlite.snomedimport;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.snowstormlite.domain.Concept;
import org.snomed.snowstormlite.domain.Concepts;
import org.snomed.snowstormlite.domain.Description;

import java.util.HashMap;
import java.util.Map;

public abstract class ComponentFactory extends ImpotentComponentFactory {

	protected final Map<Long, Concept> conceptMap;
	protected final Map<Long, Description> descriptionSynonymMap;
	protected final Concept dummyConcept;

	public ComponentFactory(Map<Long, Concept> conceptMap) {
		this.conceptMap = conceptMap;
		descriptionSynonymMap = new HashMap<>();
		dummyConcept = new Concept();
	}

	public ComponentFactory() {
		this(new Long2ObjectOpenHashMap<>());
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		if (isDescriptionInScope(Long.parseLong(conceptId))) {
			processDescription(id, active, conceptId, languageCode, typeId, term);
		}
	}

	abstract boolean isDescriptionInScope(Long conceptId);

	void processDescription(String id, String active, String conceptId, String languageCode, String typeId, String term) {
		if (active.equals("1") && (typeId.equals(Concepts.FSN) || typeId.equals(Concepts.SYNONYM))) {
			Description description = new Description(id, languageCode, typeId.equals(Concepts.FSN), term);
			getConceptMap().getOrDefault(Long.parseLong(conceptId), dummyConcept).addDescription(description);
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
		return conceptMap;
	}
}
