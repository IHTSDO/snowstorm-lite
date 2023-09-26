package org.snomed.snowstormlite.config;

import org.hl7.fhir.r4.model.Enumerations;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.domain.SnomedImplicitMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FHIRConceptMapImplicitConfig {

	private Map<String, String> snomedImplicit = new HashMap<>();
	private List<SnomedImplicitMap> implicitMaps;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public List<SnomedImplicitMap> getImplicitMaps() {
		if (implicitMaps == null) {
			List<SnomedImplicitMap> maps = new ArrayList<>();
			for (Map.Entry<String, String> configEntry : snomedImplicit.entrySet()) {
				String refsetId = configEntry.getKey();
				String[] split = configEntry.getValue().split("\\|");
				if (split.length < 3 || split.length > 4) {
					logger.error("Value of configuration item 'fhir.conceptmap.snomed-implicit.{}' has an incorrect format. Expected 3 or four values separated by pipes, got '{}'.",
							refsetId, configEntry.getValue());
				}
				String sourceSystem = split[1];
				String targetSystem = split[2];
				String equivalence = split.length == 4 ? split[3] : null;
				Enumerations.ConceptMapEquivalence conceptMapEquivalence = null;
				if (equivalence != null) {
					conceptMapEquivalence = equivalenceCodeToEnumOrLogError(equivalence);
				}
				maps.add(new SnomedImplicitMap(refsetId, sourceSystem, targetSystem, conceptMapEquivalence));
			}
			logger.info("{} implicit FHIR ConceptMaps configured for SNOMED CT.", maps.size());
			implicitMaps = maps;
		}
		return implicitMaps;
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

}
