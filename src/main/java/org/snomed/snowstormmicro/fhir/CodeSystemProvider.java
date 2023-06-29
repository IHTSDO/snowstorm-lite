package org.snomed.snowstormmicro.fhir;

import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.r4.model.CodeSystem;
import org.snomed.snowstormmicro.service.CodeSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class CodeSystemProvider implements IResourceProvider {

	@Autowired
	private CodeSystemService codeSystemService;

	@Search
	public List<CodeSystem> findCodeSystems() throws IOException {
		List<CodeSystem> codeSystems = new ArrayList<>();
		org.snomed.snowstormmicro.domain.CodeSystem codeSystem = codeSystemService.getCodeSystem();
		if (codeSystem != null) {
			codeSystems.add(codeSystem.toHapi());
		}
		return codeSystems;
	}

	@Override
	public Class<CodeSystem> getResourceType() {
		return CodeSystem.class;
	}
}
