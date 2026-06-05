package org.snomed.snowstormlite.service;

import org.hl7.fhir.r4.model.*;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.FHIRDescription;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.domain.graph.GraphNode;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;
import org.snomed.snowstormlite.service.ecl.constraint.SConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI;
import static org.snomed.snowstormlite.fhir.FHIRHelper.exception;

@Service
public class CodeSystemService {

	@Autowired
	private CodeSystemRepository repository;
	
	@Autowired
	private ExpressionConstraintLanguageService eclService;
	
	public Parameters lookup(FHIRCodeSystem codeSystem, String code, List<LanguageDialect> languageDialects) {
		try {
			FHIRConcept concept = repository.getConcept(code);
			return concept.toHapi(codeSystem, repository, languageDialects);
		} catch (IOException e) {
			throw exception("Failed to load concept.", OperationOutcome.IssueType.EXCEPTION, 500, e);
		}
	}

	public List<GraphNode> loadHierarchyPart(String system, String version, Collection<String> codes, boolean includeTerms) throws IOException {
		FHIRCodeSystem codeSystem = repository.getCodeSystem();
		if (!FHIRConstants.SNOMED_URI.equals(system)) {
			throw exception("System not found.", OperationOutcome.IssueType.NOTFOUND, 401);
		}
		if (version != null && !codeSystem.getVersionUri().equals(version)) {
			throw exception("System version not found.", OperationOutcome.IssueType.NOTFOUND, 401);
		}

		List<GraphNode> allGraphNodes = new ArrayList<>();
		Set<String> loaded = new HashSet<>();
		Set<String> remainingCodes = new HashSet<>(codes);
		do {
			List<GraphNode> graphNodes = repository.loadParents(remainingCodes, includeTerms);
			allGraphNodes.addAll(graphNodes);
			for (GraphNode graphNode : graphNodes) {
				loaded.add(graphNode.getCode());
			}

			remainingCodes.clear();
			for (GraphNode graphNode : graphNodes) {
				for (String parent : graphNode.getParents()) {
					if (!loaded.contains(parent)) {
						remainingCodes.add(parent);
					}
				}
			}
		} while (!remainingCodes.isEmpty());

		return allGraphNodes;
	}

	public Parameters validateCode(FHIRCodeSystem codeSystem, Set<Coding> codingsToValidate, List<LanguageDialect> languageDialects,
			String displayLanguage) throws IOException {
		Parameters response = new Parameters();

		if (codingsToValidate.size() == 1) {
			Coding coding = codingsToValidate.iterator().next();
			response.addParameter("code", coding.getCode());
			response.addParameter("system", coding.getSystem());
		}

		Set<Coding> codingsInCodeSystem = codingsToValidate.stream()
				.filter(coding -> SNOMED_URI.equals(coding.getSystem()) && versionsMatch(coding, codeSystem))
				.collect(Collectors.toCollection(LinkedHashSet::new));

		if (codingsInCodeSystem.isEmpty()) {
			response.addParameter("result", false);
			if (codingsToValidate.stream().anyMatch(coding -> SNOMED_URI.equals(coding.getSystem()))) {
				if (codingsToValidate.size() == 1) {
					Coding coding = codingsToValidate.iterator().next();
					response.addParameter("message", format("The system '%s' is known but the version '%s' is not.",
							coding.getSystem(), coding.getVersion()));
				} else {
					response.addParameter("message", "One or more codings use a known system but none of the versions match.");
				}
			} else if (codingsToValidate.size() == 1) {
				Coding coding = codingsToValidate.iterator().next();
				response.addParameter("message", format("The system '%s' is not known.", coding.getSystem()));
			} else {
				response.addParameter("message", "None of the codings use a known code system.");
			}
			return response;
		}

		if (codingsToValidate.size() == 1) {
			response.addParameter("version", codeSystem.getVersionUri());
		}

		for (Coding coding : codingsInCodeSystem) {
			FHIRConcept concept = repository.getConcept(coding.getCode());
			if (concept == null) {
				continue;
			}

			if (codingsToValidate.size() == 1) {
				response.addParameter("display", concept.getPT(languageDialects));
				response.addParameter("inactive", !concept.isActive());
			}

			String codingDisplay = coding.getDisplay();
			if (codingDisplay == null) {
				response.addParameter("result", true);
				return response;
			}

			FHIRDescription termMatch = null;
			for (FHIRDescription designation : concept.getDescriptions()) {
				if (codingDisplay.equalsIgnoreCase(designation.getTerm())) {
					termMatch = designation;
					if (designation.getLang() == null || languageDialects.isEmpty() || languageDialects.stream()
							.anyMatch(languageDialect -> designation.getLang().equals(languageDialect.getLanguageCode()))) {
						response.addParameter("result", true);
						response.addParameter("message", format("The code '%s' is valid and the display matched one of the designations.",
								coding.getCode()));
						return response;
					}
				}
			}
			if (termMatch != null) {
				response.addParameter("result", false);
				response.addParameter("message", format("The code '%s' is valid and the display matched the designation with term '%s', " +
								"however the language of the designation '%s' did not match any of the languages in the requested display language '%s'.",
						coding.getCode(), termMatch.getTerm(), termMatch.getLang(), displayLanguage));
				return response;
			}
			response.addParameter("result", false);
			response.addParameter("message", format("The code '%s' is valid, however the display '%s' did not match any designations.",
					coding.getCode(), codingDisplay));
			return response;
		}

		response.addParameter("result", false);
		if (codingsToValidate.size() == 1) {
			Coding coding = codingsToValidate.iterator().next();
			String codingVersion = coding.getVersion();
			response.addParameter("message", format("The code '%s' is not valid in CodeSystem '%s'%s.",
					coding.getCode(), coding.getSystem(), codingVersion != null ? format(" version '%s'", codingVersion) : ""));
		} else {
			response.addParameter("message", "None of the codings in the CodeableConcept are valid in this CodeSystem.");
		}
		return response;
	}

	private static boolean versionsMatch(Coding coding, FHIRCodeSystem codeSystem) {
		return coding.getVersion() == null || coding.getVersion().equals(codeSystem.getEditionUri())
				|| coding.getVersion().equals(codeSystem.getVersionUri());
	}

	public Parameters subsumes(FHIRCodeSystem codeSystem, String codeA, String codeB) {
		try {
			FHIRConcept conceptA = repository.getConcept(codeA);
			FHIRConcept conceptB = repository.getConcept(codeB);
			
			if (conceptA == null) {
				throw exception("Code A not found: " + codeA, OperationOutcome.IssueType.NOTFOUND, 404);
			}
			if (conceptB == null) {
				throw exception("Code B not found: " + codeB, OperationOutcome.IssueType.NOTFOUND, 404);
			}
			
			// Use ECL to check if codeA is an ancestor of codeB
			// ECL: "codeA AND >>codeB" returns codeA if it is the same as codeB or an ancestor of codeB
			String result;
			if (codeA.equals(codeB)) {
				result = "equivalent";
			} else if (isAncestorUsingECL(codeA, codeB)) {
				result = "subsumes";
			} else if (isAncestorUsingECL(codeB, codeA)) {
				result = "subsumed-by";
			} else {
				result = "not-subsumed";
			}

			Parameters parameters = new Parameters();
			parameters.addParameter("outcome", new CodeType(result));
			parameters.addParameter("codeA", new CodeType(codeA));
			parameters.addParameter("codeB", new CodeType(codeB));
			parameters.addParameter("system", new UriType(FHIRConstants.SNOMED_URI));
			parameters.addParameter("version", new StringType(codeSystem.getVersionUri()));
			
			return parameters;
		} catch (IOException e) {
			throw exception("Failed to perform subsumption check.", OperationOutcome.IssueType.EXCEPTION, 500, e);
		}
	}
	
	private boolean isAncestorUsingECL(String ancestorCode, String descendantCode) throws IOException {
		// Use ECL to check if ancestorCode is an ancestor of descendantCode
		String eclQuery = "%s AND >>%s".formatted(ancestorCode, descendantCode);
		SConstraint constraint = eclService.getEclConstraintRaw(eclQuery);
		Set<Long> results = eclService.getConceptIds(constraint);
		return results.contains(Long.parseLong(ancestorCode));
	}
}
