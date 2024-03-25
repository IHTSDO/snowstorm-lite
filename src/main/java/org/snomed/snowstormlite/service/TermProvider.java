package org.snomed.snowstormlite.service;

import org.snomed.snowstormlite.domain.LanguageDialect;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface TermProvider {

	Map<String, String> getTerms(Collection<String> codes, List<LanguageDialect> languageDialects) throws IOException;

}
