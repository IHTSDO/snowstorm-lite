package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.NotImplementedOperationException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.*;
import org.snomed.snowstormlite.config.FHIRConceptMapImplicitConfig;
import org.snomed.snowstormlite.domain.*;
import org.snomed.snowstormlite.domain.conceptmap.FHIRConceptMap;
import org.snomed.snowstormlite.domain.conceptmap.FHIRConceptMapGroup;
import org.snomed.snowstormlite.domain.conceptmap.FHIRMapElement;
import org.snomed.snowstormlite.domain.conceptmap.FHIRMapTarget;
import org.snomed.snowstormlite.service.BatchTermLoader;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.ConceptMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRConstants.IMPLICIT_EVERYTHING;
import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI;
import static org.snomed.snowstormlite.fhir.FHIRHelper.*;
import static org.snomed.snowstormlite.util.CollectionUtils.orEmpty;

@Component
public class ConceptMapProvider implements IResourceProvider {

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private FHIRConceptMapImplicitConfig implicitConfig;

	@Autowired
	private ConceptMapService conceptMapService;

	private Map<String, Enumerations.ConceptMapEquivalence> correlationToEquivalenceMap;

	@PostConstruct
	void loadCorrelationEquivalence() {
		correlationToEquivalenceMap = implicitConfig.getSnomedCorrelationToFhirEquivalenceMap();
	}

	@Search
	public List<ConceptMap> search(@OptionalParam(name = "url") String url) throws IOException {
		String normalizedFilter = FHIRHelper.normalizeImplicitConceptMapSearchUrl(url);
		List<ConceptMap> results = new ArrayList<>();
		if (codeSystemRepository.getCodeSystem() != null) {
			for (FHIRSnomedImplicitMap implicitMap : implicitConfig.getImplicitMaps()) {
				String mapUrl = SNOMED_URI + "?fhir_cm=" + implicitMap.refsetId();
				if (normalizedFilter != null && !normalizedFilter.equals(mapUrl)) {
					continue;
				}
				results.add(buildListedImplicitConceptMap(implicitMap));
			}
		}
		for (FHIRConceptMap stored : conceptMapService.findAllStored()) {
			if (normalizedFilter != null) {
				String u = FHIRHelper.normalizeImplicitConceptMapSearchUrl(stored.getUrl());
				if (!normalizedFilter.equals(u) && !normalizedFilter.equals(stored.getUrl())) {
					continue;
				}
			}
			ConceptMap hapi = stored.toHapi();
			hapi.setGroup(null);
			results.add(hapi);
		}
		return results;
	}

	@Read
	public ConceptMap getConceptMap(@IdParam IdType id) throws IOException {
		FHIRConceptMap map = conceptMapService.findById(id.getIdPart());
		return map != null ? map.toHapi() : null;
	}

	@Create
	public MethodOutcome createConceptMap(@IdParam IdType id, @ResourceParam ConceptMap conceptMap) throws IOException {
		MethodOutcome outcome = new MethodOutcome();
		if (id != null) {
			conceptMap.setId(id.getIdPart());
		}
		FHIRConceptMap saved = conceptMapService.createOrUpdateConceptMap(conceptMap);
		outcome.setId(new IdType("ConceptMap", saved.getId(), saved.getVersion()));
		return outcome;
	}

	@Update
	public MethodOutcome updateConceptMap(@IdParam IdType id, @ResourceParam ConceptMap conceptMap) throws IOException {
		if (id != null && conceptMap.getId() == null) {
			conceptMap.setId(id.getIdPart());
		}
		return createConceptMap(id, conceptMap);
	}

	@Delete
	public void deleteConceptMap(@IdParam IdType id) throws IOException {
		FHIRHelper.required("id", id);
		if (conceptMapService.findById(id.getIdPart()) == null) {
			throw FHIRHelper.exception("A ConceptMap with this id was not found.", OperationOutcome.IssueType.NOTFOUND, 404);
		}
		conceptMapService.deleteById(id.getIdPart());
	}

	private static ConceptMap buildListedImplicitConceptMap(FHIRSnomedImplicitMap implicitMap) {
		String sourceUri = implicitMap.sourceSystem() + IMPLICIT_EVERYTHING;
		String targetUri = implicitMap.targetSystem() + IMPLICIT_EVERYTHING;
		String refsetId = implicitMap.refsetId();
		String id = "snomed_implicit_map_" + refsetId;
		ConceptMap map = new ConceptMap();
		map.setId(id);
		map.setUrl(SNOMED_URI + "?fhir_cm=" + refsetId);
		map.setName(implicitMap.name());
		map.setStatus(Enumerations.PublicationStatus.ACTIVE);
		map.setSource(new UriType(sourceUri));
		map.setTarget(new UriType(targetUri));
		Narrative text = new Narrative();
		text.setStatus(Narrative.NarrativeStatus.GENERATED);
		text.setDivAsString(format(
				"This SNOMED CT Implicit Concept Map from %s to %s is generated using Reference Set %s.",
				sourceUri, targetUri, refsetId));
		map.setText(text);
		return map;
	}

	@Operation(name="$translate", idempotent=true)
	public Parameters lookupImplicit(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") UriType urlType,
			@OperationParam(name="conceptMap") ConceptMap conceptMap,
			@OperationParam(name="conceptMapVersion") String conceptMapVersion,
			@OperationParam(name="code") String code,
			@OperationParam(name="system") String system,
			@OperationParam(name="version") String version,
			@OperationParam(name="source") String sourceValueSet,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="codeableConcept") CodeableConcept codeableConcept,
			@OperationParam(name="target") String targetValueSet,
			@OperationParam(name="targetsystem") String targetSystem,
			@OperationParam(name="reverse") BooleanType reverse) throws IOException {

		String url = urlType != null ? urlType.getValueAsString() : null;
		notSupported("conceptMapVersion", conceptMapVersion);
		notSupported("source", sourceValueSet);
		notSupported("reverse", reverse);

		mutuallyExclusive("target", targetValueSet, "targetsystem", targetSystem);
		if (targetSystem == null && targetValueSet != null && targetValueSet.endsWith("?fhir_vs")) {
			targetSystem = targetValueSet.substring(0, targetValueSet.length() - "?fhir_vs".length());
		}

		requireExactlyOneOf("code", code, "coding", coding, "codeableConcept", codeableConcept);
		mutuallyRequired("code", code, "system", system);
		if (coding == null) {
			if (code != null) {
				coding = new Coding(system, code, null).setVersion(version);
			} else {
				if (codeableConcept.getCoding().size() > 1) {
					throw exception("Translation of CodeableConcept with multiple codes is not supported.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
				}
				if (codeableConcept.getCoding().isEmpty()) {
					throw exception("CodeableConcept contains no coding.", OperationOutcome.IssueType.INVARIANT, 400);
				}
				coding = codeableConcept.getCoding().get(0);
			}
		}

		if (url != null && url.startsWith("http://snomed.info/sct/") && coding.getVersion() == null) {
			coding.setVersion(url.substring(0, url.indexOf("?")));
		}

		boolean fromSNOMED = isSnomedUri(coding.getSystem());
		if (!fromSNOMED && isSnomedUri(targetSystem)) {
			throw new NotImplementedOperationException("Mapping to SNOMED CT is not yet implemented.");
		}

		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		if (fromSNOMED && codeSystem != null) {
			String codingVersion = coding.getVersion();
			if (codingVersion != null && !codeSystem.getVersionUri().equals(codingVersion)) {
				throw FHIRHelper.exception("Map not found. The requested version of SNOMED CT is not loaded.", OperationOutcome.IssueType.NOTFOUND, 404);
			}
		}

		List<FHIRConceptMap> maps;
		if (conceptMap != null) {
			maps = Collections.singletonList(new FHIRConceptMap(conceptMap));
		} else {
			maps = conceptMapService.findMapsForTranslate(url, coding, targetSystem);
		}

		if (maps.isEmpty()) {
			throw exception("No suitable map found.", OperationOutcome.IssueType.NOTFOUND, 404);
		}

		Parameters parameters = new Parameters();
		List<Parameters.ParametersParameterComponent> matches = new ArrayList<>();
		AtomicBoolean aggregateResult = new AtomicBoolean(false);

		Map<String, FHIRSnomedImplicitMap> refsetToMap = implicitConfig.getImplicitMaps().stream()
				.collect(Collectors.toMap(FHIRSnomedImplicitMap::refsetId, Function.identity()));

		BatchTermLoader termLoader = new BatchTermLoader();
		Map<FHIRSnomedImplicitMap, List<FHIRMapping>> allImplicitMappings = new LinkedHashMap<>();
		if (fromSNOMED) {
			for (FHIRConceptMap map : maps) {
				if (map.isImplicitSnomedMap()) {
					Map<FHIRSnomedImplicitMap, List<FHIRMapping>> part = new HashMap<>();
					loadMapping(targetSystem, coding.getCode(), refsetToMap, map.getSnomedRefsetId(), termLoader, part);
					allImplicitMappings.putAll(part);
				}
			}
		}

		if (codeSystem != null) {
			termLoader.loadAll(codeSystemRepository, Concepts.DEFAULT_LANGUAGE);
		}

		for (Map.Entry<FHIRSnomedImplicitMap, List<FHIRMapping>> mappingsOfType : allImplicitMappings.entrySet()) {
			FHIRSnomedImplicitMap type = mappingsOfType.getKey();
			if (!type.isFromSnomed()) {
				continue;
			}
			for (FHIRMapping mapping : mappingsOfType.getValue()) {
				String message = mapping.getMessage();
				if (message != null) {
					matches.add(new Parameters.ParametersParameterComponent(new StringType("message")).setValue(new StringType(message)));
				}
				Parameters.ParametersParameterComponent match = new Parameters.ParametersParameterComponent(new StringType("match"));
				Enumerations.ConceptMapEquivalence equivalence = getEquivalence(mapping, type);
				if (equivalence != null) {
					match.addPart().setName("equivalence").setValue(new CodeType(equivalence.toCode()));
				}
				aggregateResult.set(true);

				match.addPart()
						.setName("concept")
						.setValue(new Coding(type.targetSystem(), mapping.getCode(), type.isToSnomed() ? termLoader.get(mapping.getCode()) : null));

				String sourceUrl = codeSystem != null ? type.getUrl(codeSystem) : (SNOMED_URI + "?fhir_cm=" + type.refsetId());
				match.addPart()
						.setName("source")
						.setValue(new StringType(sourceUrl));

				matches.add(match);
			}
		}

		for (FHIRConceptMap map : maps) {
			if (!map.isImplicitSnomedMap()) {
				translateStoredConceptMap(map, coding, targetSystem, matches, aggregateResult);
			}
		}

		parameters.addParameter("result", aggregateResult.get());
		if (matches.isEmpty()) {
			parameters.addParameter("message", format("No mappings could be found for %s (%s)", coding.getCode(), coding.getSystem()));
		}
		for (Parameters.ParametersParameterComponent match : matches) {
			parameters.addParameter(match);
		}

		return parameters;
	}

	private void translateStoredConceptMap(FHIRConceptMap map, Coding coding, String targetSystem,
			List<Parameters.ParametersParameterComponent> matches, AtomicBoolean aggregateResult) {
		for (FHIRConceptMapGroup g : orEmpty(map.getGroup())) {
			if (!coding.getSystem().equals(g.getSource())) {
				continue;
			}
			if (targetSystem != null && !targetSystem.equals(g.getTarget())) {
				continue;
			}
			for (FHIRMapElement element : orEmpty(g.getElement())) {
				if (!coding.getCode().equals(element.getCode())) {
					continue;
				}
				for (FHIRMapTarget mapTarget : orEmpty(element.getTarget())) {
					Parameters.ParametersParameterComponent matchParam = new Parameters.ParametersParameterComponent(new StringType("match"));
					if (mapTarget.getEquivalence() != null) {
						matchParam.addPart(new Parameters.ParametersParameterComponent(new StringType("equivalence"))
								.setValue(new CodeType(mapTarget.getEquivalence())));
					}
					matchParam.addPart(new Parameters.ParametersParameterComponent(new StringType("concept"))
							.setValue(new Coding(g.getTarget(), mapTarget.getCode(), mapTarget.getDisplay())));
					matchParam.addPart(new Parameters.ParametersParameterComponent(new StringType("source"))
							.setValue(new StringType(map.getUrl())));
					matches.add(matchParam);
					aggregateResult.set(true);
				}
			}
		}
	}

	private void loadMapping(String targetSystem, String codingCode, Map<String, FHIRSnomedImplicitMap> refsetToMap, String refsetId,
			BatchTermLoader termLoader, Map<FHIRSnomedImplicitMap, List<FHIRMapping>> mappings) throws IOException {

		FHIRConcept concept = codeSystemRepository.getConcept(codingCode);
		if (concept != null) {
			for (FHIRMapping mapping : concept.getMappings()) {
				FHIRSnomedImplicitMap implicitMap = refsetToMap.get(mapping.getRefsetId());
				if (!mapping.isInverse() && implicitMap != null &&
						(targetSystem == null || targetSystem.equals(implicitMap.targetSystem())) &&
						(refsetId == null || refsetId.equals(mapping.getRefsetId()))
				) {
					if (implicitMap.isToSnomed()) {
						termLoader.addSnomedTerm(mapping.getCode());
					}
					mappings.computeIfAbsent(implicitMap, i -> new ArrayList<>()).add(mapping);
				}
			}
		}
	}

	private Enumerations.ConceptMapEquivalence getEquivalence(FHIRMapping mapping, FHIRSnomedImplicitMap type) {
		Enumerations.ConceptMapEquivalence equivalence = null;
		String correlation = mapping.getCorrelation();
		if (correlation != null) {
			equivalence = correlationToEquivalenceMap.get(correlation);
		}
		if (equivalence == null) {
			equivalence = type.equivalence();
		}
		return equivalence;
	}

	@Override
	public Class<ConceptMap> getResourceType() {
		return ConceptMap.class;
	}
}
