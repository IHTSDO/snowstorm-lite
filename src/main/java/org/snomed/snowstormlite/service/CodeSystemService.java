package org.snomed.snowstormlite.service;

import org.hl7.fhir.r4.model.*;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.domain.graph.GraphNode;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;
import org.snomed.snowstormlite.service.ecl.constraint.SConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

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

	public List<GraphNode> loadHierarchyPart(String system, String version, Collection<String> codes) throws IOException {
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
			List<GraphNode> graphNodes = repository.loadParents(remainingCodes);
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
