package org.snomed.snowstormlite.snomedimport;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.Concepts;
import org.snomed.snowstormlite.domain.FHIRDescription;

import java.util.HashMap;
import java.util.Map;

public abstract class ComponentFactory extends ImpotentComponentFactory {

	protected final Map<Long, FHIRConcept> conceptMap;
	protected final Map<Long, FHIRDescription> descriptionSynonymMap;
	protected final FHIRConcept dummyConcept;

	public ComponentFactory(Map<Long, FHIRConcept> conceptMap) {
		this.conceptMap = conceptMap;
		descriptionSynonymMap = new HashMap<>();
		dummyConcept = new FHIRConcept();
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
			FHIRDescription description = new FHIRDescription(id, languageCode, typeId.equals(Concepts.FSN), term);
			getConceptMap().getOrDefault(Long.parseLong(conceptId), dummyConcept).addDescription(description);
			if (typeId.equals(Concepts.SYNONYM)) {
				descriptionSynonymMap.put(Long.parseLong(id), description);
			}
		}
	}

	@Override
	public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		if (active.equals("1")) {
			if (fieldNames.length == 7 && fieldNames[6].equals("acceptabilityId")) {
				// Active lang refset member
				FHIRDescription description = descriptionSynonymMap.get(Long.parseLong(referencedComponentId));
				if (description != null) {
					if (otherValues[0].equals(Concepts.PREFERRED)) {
						description.getPreferredLangRefsets().add(refsetId);
					} else if (otherValues[0].equals(Concepts.ACCEPTABLE)) {
						description.getAcceptableLangRefsets().add(refsetId);
					}
				}
			}
		}
	}

	public Map<Long, FHIRConcept> getConceptMap() {
		return conceptMap;
	}
}
