package org.snomed.snowstormlite.service;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

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

}
