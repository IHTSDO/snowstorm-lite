package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.r4.model.*;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.CodeSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRHelper.*;
import static org.snomed.snowstormlite.service.Constants.ACCEPT_LANGUAGE_HEADER;

@Component
public class CodeSystemProvider implements IResourceProvider {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Search
	public List<CodeSystem> findCodeSystems(
			@OptionalParam(name="id") String id,
			@OptionalParam(name="url") String url) throws IOException {

		List<CodeSystem> codeSystems = new ArrayList<>();
		org.snomed.snowstormlite.domain.CodeSystem codeSystem = codeSystemRepository.getCodeSystem();
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
		org.snomed.snowstormlite.domain.CodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		if (codeSystem != null) {
			CodeSystem hapi = codeSystem.toHapi();
			if (hapi.getId().equals(idPart)) {
				return hapi;
			}
		}
		return null;
	}

	@Operation(name="$lookup", idempotent=true)
	public Parameters lookupImplicit(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="date") StringType date,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="property") List<CodeType> propertiesType ) {

		mutuallyExclusive("code", code, "coding", coding);
		notSupported("date", date);
		org.snomed.snowstormlite.domain.CodeSystem codeSystem = getCodeSystemVersionOrThrow(system, version, coding);
		return codeSystemService.lookup(codeSystem, recoverCode(code, coding), displayLanguage, request.getHeader(ACCEPT_LANGUAGE_HEADER), propertiesType);
	}

	private org.snomed.snowstormlite.domain.CodeSystem getCodeSystemVersionOrThrow(UriType system, StringType version, Coding coding) {
		try {
			CodeSystemVersionParams codeSystemVersionParams = getCodeSystemVersionParams(system, version, coding);
			org.snomed.snowstormlite.domain.CodeSystem codeSystem = codeSystemRepository.getCodeSystem();
			if (codeSystemVersionParams.matchesCodeSystem(codeSystem)) {
				return codeSystem;
			}
			throw exception(format("Code system not found for parameters %s.", codeSystemVersionParams), OperationOutcome.IssueType.NOTFOUND, 400);
		} catch (IOException e) {
			throw exception("Failed to load code system.", OperationOutcome.IssueType.EXCEPTION, 500);
		}
	}


	@Override
	public Class<CodeSystem> getResourceType() {
		return CodeSystem.class;
	}
}
