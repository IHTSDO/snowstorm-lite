package org.snomed.snowstormlite.service;

import org.apache.logging.log4j.util.Strings;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.config.FHIRConceptMapImplicitConfig;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRSnomedImplicitMap;
import org.snomed.snowstormlite.domain.conceptmap.FHIRConceptMap;
import org.snomed.snowstormlite.domain.conceptmap.FHIRConceptMapGroup;
import org.snomed.snowstormlite.domain.conceptmap.FHIRMapElement;
import org.snomed.snowstormlite.fhir.FHIRHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.snomed.snowstormlite.fhir.FHIRConstants.IMPLICIT_EVERYTHING;
import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI;
import static org.snomed.snowstormlite.util.CollectionUtils.orEmpty;

@Service
public class ConceptMapService {

	@Autowired
	private ConceptMapRepository conceptMapRepository;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private FHIRConceptMapImplicitConfig implicitConfig;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public FHIRConceptMap find(String url, String version) throws IOException {
		return conceptMapRepository.findConceptMap(url, version);
	}

	public FHIRConceptMap findById(String id) throws IOException {
		return conceptMapRepository.findConceptMapById(id);
	}

	public List<FHIRConceptMap> findAllStored() throws IOException {
		return conceptMapRepository.findAll().stream()
				.sorted(Comparator.comparing(FHIRConceptMap::getName, Comparator.nullsFirst(String::compareTo))
						.thenComparing(FHIRConceptMap::getUrl)
						.thenComparing(FHIRConceptMap::getVersion, Comparator.nullsFirst(Comparator.reverseOrder())))
				.toList();
	}

	public synchronized FHIRConceptMap createOrUpdateConceptMap(ConceptMap conceptMapUpdate) throws IOException {
		if (Strings.isBlank(conceptMapUpdate.getUrl()) || Strings.isBlank(conceptMapUpdate.getVersion())) {
			throw FHIRHelper.exception("ConceptMap url and version are mandatory", OperationOutcome.IssueType.INVARIANT, 400);
		}
		if (conceptMapUpdate.getUrl().contains("fhir_cm")) {
			throw FHIRHelper.exception("ConceptMap url must not contain 'fhir_cm', this is reserved for implicit concept maps.", OperationOutcome.IssueType.INVARIANT, 400);
		}
		if (conceptMapUpdate.getId() != null && conceptMapUpdate.getId().startsWith("ConceptMap/")) {
			conceptMapUpdate.setId(conceptMapUpdate.getId().replace("ConceptMap/", ""));
		}

		FHIRConceptMap incoming = new FHIRConceptMap(conceptMapUpdate);

		FHIRConceptMap existingByUrlVersion = conceptMapRepository.findConceptMap(incoming.getUrl(), incoming.getVersion());
		if (existingByUrlVersion != null) {
			if (incoming.getId() != null && !incoming.getId().equals(existingByUrlVersion.getId())) {
				throw FHIRHelper.exception("A ConceptMap with the same url and version already exists with a different id.", OperationOutcome.IssueType.INVARIANT, 400);
			}
		} else if (incoming.getId() != null) {
			FHIRConceptMap existingById = conceptMapRepository.findConceptMapById(incoming.getId());
			if (existingById != null) {
				throw FHIRHelper.exception("A ConceptMap with the same id already exists with a different url and version.", OperationOutcome.IssueType.INVARIANT, 400);
			}
		}

		if (existingByUrlVersion != null) {
			logger.info("Updating ConceptMap URL:'{}', version:'{}'", incoming.getUrl(), incoming.getVersion());
			incoming.setId(existingByUrlVersion.getId());
		} else {
			logger.info("Creating ConceptMap URL:'{}', version:'{}'", incoming.getUrl(), incoming.getVersion());
			if (incoming.getId() == null) {
				incoming.setId(UUID.randomUUID().toString());
			}
		}

		conceptMapRepository.save(incoming);
		return incoming;
	}

	public void deleteById(String id) throws IOException {
		conceptMapRepository.deleteById(id);
	}

	public List<FHIRConceptMap> findMapsForTranslate(String url, Coding coding, String targetSystem) throws IOException {
		List<FHIRConceptMap> maps = new ArrayList<>();
		String normalizedUrlFilter = FHIRHelper.normalizeImplicitConceptMapSearchUrl(url);

		for (FHIRConceptMap m : conceptMapRepository.findAll()) {
			if (matchesStoredMapForTranslate(m, normalizedUrlFilter, coding, targetSystem)) {
				maps.add(m);
			}
		}
		for (FHIRConceptMap implicit : buildImplicitConceptMapsForTranslate()) {
			if (matchesImplicitMapForTranslate(implicit, normalizedUrlFilter, coding, targetSystem)) {
				maps.add(implicit);
			}
		}
		return maps;
	}

	public List<FHIRMapElement> findMapElements(FHIRConceptMap map, Coding coding, String targetSystem) {
		List<FHIRMapElement> results = new ArrayList<>();
		for (FHIRConceptMapGroup g : orEmpty(map.getGroup())) {
			if (!coding.getSystem().equals(g.getSource())) {
				continue;
			}
			if (targetSystem != null && !targetSystem.equals(g.getTarget())) {
				continue;
			}
			for (FHIRMapElement el : orEmpty(g.getElement())) {
				if (coding.getCode().equals(el.getCode())) {
					results.add(el);
				}
			}
		}
		return results;
	}

	private List<FHIRConceptMap> buildImplicitConceptMapsForTranslate() {
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		if (codeSystem == null) {
			return List.of();
		}
		List<FHIRConceptMap> out = new ArrayList<>();
		for (FHIRSnomedImplicitMap implicit : implicitConfig.getImplicitMaps()) {
			FHIRConceptMap m = new FHIRConceptMap();
			String refsetId = implicit.refsetId();
			m.setId("snomed_implicit_map_" + refsetId);
			m.setUrl(SNOMED_URI + "?fhir_cm=" + refsetId);
			m.setName(implicit.name());
			m.setStatus(Enumerations.PublicationStatus.ACTIVE.toCode());
			m.setSourceUri(implicit.sourceSystem() + IMPLICIT_EVERYTHING);
			m.setTargetUri(implicit.targetSystem() + IMPLICIT_EVERYTHING);
			m.setImplicitSnomedMap(true);
			m.setSnomedRefsetId(refsetId);
			if (implicit.equivalence() != null) {
				m.setSnomedRefsetEquivalence(implicit.equivalence().toCode());
			}
			out.add(m);
		}
		return out;
	}

	private boolean matchesStoredMapForTranslate(FHIRConceptMap m, String normalizedUrlFilter, Coding coding, String targetSystem) {
		if (normalizedUrlFilter != null) {
			String mapUrlNorm = FHIRHelper.normalizeImplicitConceptMapSearchUrl(m.getUrl());
			if (!normalizedUrlFilter.equals(mapUrlNorm) && !normalizedUrlFilter.equals(m.getUrl())) {
				return false;
			}
		}
		boolean groupMatchesSystem = false;
		for (FHIRConceptMapGroup g : orEmpty(m.getGroup())) {
			if (coding.getSystem().equals(g.getSource())) {
				if (targetSystem == null || targetSystem.equals(g.getTarget())) {
					groupMatchesSystem = true;
					break;
				}
			}
		}
		return groupMatchesSystem;
	}

	private boolean matchesImplicitMapForTranslate(FHIRConceptMap map, String normalizedUrlFilter, Coding coding, String targetSystem) {
		if (normalizedUrlFilter != null) {
			String mapUrlNorm = FHIRHelper.normalizeImplicitConceptMapSearchUrl(map.getUrl());
			if (!normalizedUrlFilter.equals(mapUrlNorm) && !normalizedUrlFilter.equals(map.getUrl())) {
				return false;
			}
		}
		String sourceUri = map.getSourceUri();
		String normalizedCodingSystem = coding.getSystem().replace("/xsct", "/sct");
		if (sourceUri != null && !sourceUri.startsWith(normalizedCodingSystem)) {
			return false;
		}
		if (targetSystem != null) {
			String expectedTarget = targetSystem + IMPLICIT_EVERYTHING;
			if (!expectedTarget.equals(map.getTargetUri())) {
				return false;
			}
		}
		return true;
	}
}
