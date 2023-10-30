package org.snomed.snowstormlite.snomedimport;

import org.snomed.snowstormlite.domain.FHIRConcept;

import java.util.Collection;
import java.util.Map;

public class ComponentFactoryWithDescriptionBatch extends ComponentFactory {

	private final Collection<Long> conceptIdBatch;

	public ComponentFactoryWithDescriptionBatch(Map<Long, FHIRConcept> conceptMap, Collection<Long> conceptIdBatch) {
		super(conceptMap);
		this.conceptIdBatch = conceptIdBatch;
	}

	boolean isDescriptionInScope(Long conceptId) {
		return conceptIdBatch.contains(conceptId);
	}

	public Collection<Long> getConceptIdBatch() {
		return conceptIdBatch;
	}
}
