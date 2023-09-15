package org.snomed.snowstormmicro.service;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.snomed.snowstormmicro.domain.CodeSystem;
import org.snomed.snowstormmicro.domain.Concept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static org.snomed.snowstormmicro.fhir.FHIRHelper.exception;

@Service
public class CodeSystemService {

	@Autowired
	private CodeSystemRepository repository;

	public Parameters lookup(CodeSystem codeSystem, String code, String displayLanguage, String acceptLanguageHeader, List<CodeType> properties) {
		try {
			Concept concept = repository.getConcept(code);
			return concept.toHapi(codeSystem, repository);
		} catch (IOException e) {
			throw exception("Failed to load concept.", OperationOutcome.IssueType.EXCEPTION, 500, e);
		}
	}

}
