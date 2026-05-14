package org.snomed.snowstormlite.config;

import org.hl7.fhir.r4.model.Enumerations;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.domain.FHIRSnomedImplicitMap;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FHIRConceptMapImplicitConfig {

	private Map<String, String> snomedImplicit = new HashMap<>();

	private Map<String, String> snomedImplicitEquivalence = new HashMap<>();

	private List<FHIRSnomedImplicitMap> implicitMaps;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public List<FHIRSnomedImplicitMap> getImplicitMaps() {
		if (implicitMaps == null) {
			List<FHIRSnomedImplicitMap> maps = new ArrayList<>();
			for (Map.Entry<String, String> configEntry : snomedImplicit.entrySet()) {
				String refsetId = configEntry.getKey();
				String[] split = configEntry.getValue().split("\\|");
				if (split.length < 3 || split.length > 4) {
					logger.error("Value of configuration item 'fhir.conceptmap.snomed-implicit.{}' has an incorrect format. Expected 3 or four values separated by pipes, got '{}'.",
							refsetId, configEntry.getValue());
				}
				String name = split[0];
				String sourceSystem = split[1];
				String targetSystem = split[2];
				String equivalenceStr = split.length == 4 ? split[3] : null;
				Enumerations.ConceptMapEquivalence conceptMapEquivalence = null;
				if (StringUtils.hasText(equivalenceStr)) {
					conceptMapEquivalence = equivalenceCodeToEnumOrLogError(equivalenceStr);
				}
				maps.add(new FHIRSnomedImplicitMap(refsetId, name, sourceSystem, targetSystem, conceptMapEquivalence));
			}
			logger.info("{} implicit FHIR ConceptMaps configured for SNOMED CT.", maps.size());
			implicitMaps = maps;
		}
		return implicitMaps;
	}

	public Map<String, Enumerations.ConceptMapEquivalence> getSnomedCorrelationToFhirEquivalenceMap() {
		HashMap<String, Enumerations.ConceptMapEquivalence> map = new HashMap<>();

		for (Map.Entry<String, String> entry : snomedImplicitEquivalence.entrySet()) {
			String equivalenceString = entry.getValue();
			Enumerations.ConceptMapEquivalence equivalenceEnum = equivalenceCodeToEnumOrLogError(equivalenceString);
			map.put(entry.getKey(), equivalenceEnum);
		}

		return map;
	}

	@Nullable
	private Enumerations.ConceptMapEquivalence equivalenceCodeToEnumOrLogError(String equivalenceString) {
		Enumerations.ConceptMapEquivalence equivalenceEnum = Enumerations.ConceptMapEquivalence.fromCode(equivalenceString);
		if (equivalenceEnum == null) {
			logger.error("Configured FHIR MAP equivalence value '{}' not recognised in R4. Please check configuration.", equivalenceString);
		}
		return equivalenceEnum;
	}

	public Map<String, String> getSnomedImplicit() {
		return snomedImplicit;
	}

	public Map<String, String> getSnomedImplicitEquivalence() {
		return snomedImplicitEquivalence;
	}

}
