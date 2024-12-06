package org.snomed.snowstormlite.service;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.domain.graph.GraphNode;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

import static org.snomed.snowstormlite.fhir.FHIRHelper.exception;

@Service
public class CodeSystemService {

	@Autowired
	private CodeSystemRepository repository;

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
}
