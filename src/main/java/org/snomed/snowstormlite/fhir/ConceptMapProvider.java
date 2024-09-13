package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.NotImplementedOperationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.*;
import org.snomed.snowstormlite.config.FHIRConceptMapImplicitConfig;
import org.snomed.snowstormlite.domain.*;
import org.snomed.snowstormlite.service.BatchTermLoader;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.util.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRHelper.*;

@Component
public class ConceptMapProvider implements IResourceProvider {

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private FHIRConceptMapImplicitConfig implicitConfig;

	private final Map<String, Enumerations.ConceptMapEquivalence> correlationToEquivalenceMap = CollectionUtils.mapOf(
			"1193552004", Enumerations.ConceptMapEquivalence.RELATEDTO,
			"1193551006", Enumerations.ConceptMapEquivalence.DISJOINT,
			"1193550007", Enumerations.ConceptMapEquivalence.INEXACT,
			"1193549007", Enumerations.ConceptMapEquivalence.WIDER,
			"1193548004", Enumerations.ConceptMapEquivalence.EQUAL,
			"1193547009", Enumerations.ConceptMapEquivalence.NARROWER,
			"447561005", Enumerations.ConceptMapEquivalence.RELATEDTO,
			"447560006", Enumerations.ConceptMapEquivalence.INEXACT,
			"447559001", Enumerations.ConceptMapEquivalence.NARROWER,
			"447558009", Enumerations.ConceptMapEquivalence.WIDER,
			"447557004", Enumerations.ConceptMapEquivalence.EQUAL,
			"447556008", Enumerations.ConceptMapEquivalence.DISJOINT
	);

	@Search
	public List<ConceptMap> search() throws IOException {
		return Collections.emptyList();
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
		notSupported("conceptMap", conceptMap);
		notSupported("conceptMapVersion", conceptMapVersion);
		notSupported("sourceValueSet", sourceValueSet);
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
		String refsetId = getRefsetId(url);

		Map<FHIRSnomedImplicitMap, List<FHIRMapping>> mappings = new HashMap<>();

		List<FHIRSnomedImplicitMap> implicitMaps = implicitConfig.getImplicitMaps();
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		boolean fromSNOMED = isSnomedUri(coding.getSystem());
		BatchTermLoader termLoader = new BatchTermLoader();
		if (fromSNOMED) {
			Map<String, FHIRSnomedImplicitMap> refsetToMap = implicitMaps.stream().collect(Collectors.toMap(FHIRSnomedImplicitMap::refsetId, Function.identity()));
			String codingVersion = coding.getVersion();
			if (codingVersion != null && !codeSystem.getVersionUri().equals(codingVersion)) {
				throw FHIRHelper.exception("Map not found. The requested version of SNOMED CT is not loaded.", OperationOutcome.IssueType.NOTFOUND, 404);
			}
			loadMapping(targetSystem, coding.getCode(), refsetToMap, refsetId, termLoader, mappings);
		} else if (isSnomedUri(targetSystem)) {
			// TODO
			throw new NotImplementedOperationException("Mapping to SNOMED CT is not yet implemented.");
		}

		Parameters parameters = new Parameters();
		List<Parameters.ParametersParameterComponent> matches = new ArrayList<>();
		boolean result = false;
		termLoader.loadAll(codeSystemRepository, Concepts.DEFAULT_LANGUAGE);
		for (Map.Entry<FHIRSnomedImplicitMap, List<FHIRMapping>> mappingsOfType : mappings.entrySet()) {
			FHIRSnomedImplicitMap type = mappingsOfType.getKey();
			if (!type.isFromSnomed()) {
				continue;
			}
			List<FHIRMapping> value = mappingsOfType.getValue();
			for (FHIRMapping mapping : value) {
				String message = mapping.getMessage();
				if (message != null) {
					matches.add(new Parameters.ParametersParameterComponent(new StringType("message")).setValue(new StringType(message)));
				}
				Parameters.ParametersParameterComponent match = new Parameters.ParametersParameterComponent(new StringType("match"));
				Enumerations.ConceptMapEquivalence equivalence = getEquivalence(mapping, type);
				if (equivalence != null) {
					if (equivalence != Enumerations.ConceptMapEquivalence.UNMATCHED && equivalence != Enumerations.ConceptMapEquivalence.DISJOINT) {
						result = true;
					}
					match.addPart().setName("equivalence").setValue(new CodeType(equivalence.toCode()));
				}
				match.addPart()
						.setName("concept")
						.setValue(new Coding(type.targetSystem(), mapping.getCode(), type.isToSnomed() ? termLoader.get(mapping.getCode()) : null));

				match.addPart()
						.setName("source")
						.setValue(new StringType(type.getUrl(codeSystem)));

				matches.add(match);
			}
		}

		parameters.addParameter("result", result);
		if (mappings.isEmpty()) {
			parameters.addParameter("message", format("No mappings could be found for %s (%s)", coding.getCode(), coding.getSystem()));
		}
		for (Parameters.ParametersParameterComponent match : matches) {
			parameters.addParameter(match);
		}

		return parameters;
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

	private String getRefsetId(String url) {
		if (FHIRHelper.isSnomedUri(url)) {
			Pattern compile = Pattern.compile(".*?fhir_cm=(\\d+).*");
			Matcher matcher = compile.matcher(url);
			if (matcher.matches()) {
				return matcher.group(1);
			}
		}
		return null;
	}

	@Override
	public Class<ConceptMap> getResourceType() {
		return ConceptMap.class;
	}
}
