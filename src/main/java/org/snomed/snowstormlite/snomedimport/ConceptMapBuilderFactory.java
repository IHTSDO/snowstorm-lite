package org.snomed.snowstormlite.snomedimport;

import org.snomed.snowstormlite.domain.Concepts;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.FHIRMapping;

import java.util.*;

import static java.lang.String.format;

public class ConceptMapBuilderFactory {

	private static final Long SIMPLE_MAP_TO_SNOMED_TYPE = 1187636009L;
	private static final Long ASSOCIATION_MAP_TYPE = 900000000000521006L;
	private static final Long EXTENDED_MAP_FROM_SNOMED_TYPE = 609331003L;

	private final Map<Long, ConceptMapBuilder> mapTypeToBuilder =
			Map.of(SIMPLE_MAP_TO_SNOMED_TYPE, new SimpleMapBuilder(),
					ASSOCIATION_MAP_TYPE, new SimpleMapBuilder(),
					EXTENDED_MAP_FROM_SNOMED_TYPE, new ComplexMapBuilder());

	private final Map<Set<Long>, ConceptMapBuilder> mapsToBuilder = new HashMap<>();
	private final Map<Long, FHIRConcept> concepts;
	private final Set<Long> notMap = new HashSet<>();

	public ConceptMapBuilderFactory(Map<Long, FHIRConcept> concepts) {
		this.concepts = concepts;
		for (Long mapType : mapTypeToBuilder.keySet()) {
			FHIRConcept mapTypeConcept = concepts.get(mapType);
			if (mapTypeConcept != null) {
				Set<Long> mapsOfType = mapTypeConcept.getDescendants(concepts);
				mapsOfType.add(mapType);
				mapsToBuilder.put(mapsOfType, mapTypeToBuilder.get(mapType));
			}
		}
	}

	public ConceptMapBuilder getMapBuilder(Long conceptId) {
		if (notMap.contains(conceptId)) {
			return null;
		}
		for (Map.Entry<Set<Long>, ConceptMapBuilder> entry : mapsToBuilder.entrySet()) {
			if (entry.getKey().contains(conceptId)) {
				return entry.getValue();
			}
		}
		notMap.add(conceptId);
		return null;
	}

	public interface ConceptMapBuilder {
		void addMapping(String refsetId, String referencedComponentId, String[] otherValues);
	}

	public class SimpleMapBuilder implements ConceptMapBuilder {
		@Override
		public void addMapping(String refsetId, String referencedComponentId, String[] otherValues) {
			FHIRConcept concept = concepts.get(Long.parseLong(referencedComponentId));
			if (concept != null) {
				String targetCode = otherValues[0];
				concept.addMapping(new FHIRMapping(refsetId, targetCode, null, null));
			}
		}
	}

	private class ComplexMapBuilder implements ConceptMapBuilder {

		@Override
		public void addMapping(String refsetId, String referencedComponentId, String[] otherValues) {
			// mapGroup	mapPriority	mapRule	mapAdvice	mapTarget	correlation	mapCategory?
			// 0		1			2		3			4			5			6

			String mapCategoryMessage = "";

			// mapCategoryId null for complex map, only used in extended map
			if (otherValues.length == 7) {
				Long mapCategory = Long.parseLong(otherValues[6]);
				FHIRConcept mapCategoryConcept = concepts.get(mapCategory);
				String mapCategoryLabel = null;
				if (mapCategoryConcept != null) {
					mapCategoryLabel = mapCategoryConcept.getPT(Concepts.DEFAULT_LANGUAGE);
				}
				if (mapCategoryLabel == null) {
					mapCategoryLabel = mapCategory.toString();
				}
				mapCategoryMessage = format(", Map Category:'%s'", mapCategoryLabel);
			}

			String correlation = otherValues[5];
			String message = format("Please observe the following map advice. Group:%s, Priority:%s, Rule:%s, Advice:'%s'%s.",
					otherValues[0], otherValues[1], otherValues[2], otherValues[3], mapCategoryMessage);

			FHIRConcept concept = concepts.get(Long.parseLong(referencedComponentId));
			if (concept != null) {
				String targetCode = otherValues[4];
				concept.addMapping(new FHIRMapping(refsetId, targetCode, correlation, message));
			}
		}
	}
}
