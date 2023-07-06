package org.snomed.snowstormmicro.fhir;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.IdType;
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
	public List<CodeSystem> findCodeSystems(
			@OptionalParam(name="id") String id,
			@OptionalParam(name="url") String url) throws IOException {
		List<CodeSystem> codeSystems = new ArrayList<>();
		org.snomed.snowstormmicro.domain.CodeSystem codeSystem = codeSystemService.getCodeSystem();
		if (codeSystem != null) {
			CodeSystem hapi = codeSystem.toHapi();
			if ((id == null || hapi.getId().equals(id)) && (url == null || hapi.getUrl().equals(url))) {
				codeSystems.add(hapi);
			}
		}
		return codeSystems;
	}

	@Read()
	public CodeSystem getCodeSystem(@IdParam IdType id) throws IOException {
		String idPart = id.getIdPart();
		org.snomed.snowstormmicro.domain.CodeSystem codeSystem = codeSystemService.getCodeSystem();
		if (codeSystem != null) {
			CodeSystem hapi = codeSystem.toHapi();
			if (hapi.getId().equals(idPart)) {
				return hapi;
			}
		}
		return null;
	}

	@Override
	public Class<CodeSystem> getResourceType() {
		return CodeSystem.class;
	}
}
