package org.snomed.snowstormmicro.service;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface TermProvider {

	Map<String, String> getTerms(Collection<String> codes) throws IOException;

}
