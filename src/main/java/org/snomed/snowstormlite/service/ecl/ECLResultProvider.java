package org.snomed.snowstormlite.service.ecl;

import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.service.ecl.constraint.SConstraint;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public interface ECLResultProvider {

	Set<Long> getConceptIds(SConstraint expressionConstraint) throws IOException;

	<T> Set<T> extractFromConcepts(Collection<String> conceptIds, Function<FHIRConcept, Set<T>> mappingExtractor) throws IOException;

}
