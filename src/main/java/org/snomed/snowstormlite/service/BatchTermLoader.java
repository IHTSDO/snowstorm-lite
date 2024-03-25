package org.snomed.snowstormlite.service;

import org.snomed.snowstormlite.domain.LanguageDialect;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BatchTermLoader {

	private final Set<String> snomedCodes = new HashSet<>();
	private Map<String, String> snomedTerms;

	public void addSnomedTerm(String code) {
		snomedCodes.add(code);
	}

	public void loadAll(CodeSystemRepository codeSystemRepository, List<LanguageDialect> languageDialects) throws IOException {
		snomedTerms = codeSystemRepository.getTerms(snomedCodes, languageDialects);
	}

	public String get(String code) {
		return snomedTerms.get(code);
	}
}
