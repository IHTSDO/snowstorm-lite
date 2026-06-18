package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.param.StringAndListParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.*;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.CodeSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRConstants.ACCEPT_LANGUAGE_HEADER;
import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI;
import static org.snomed.snowstormlite.fhir.FHIRHelper.*;

@Component
public class CodeSystemProvider implements IResourceProvider {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private LanguageDialectParser languageDialectParser;

	@Search
	public List<CodeSystem> findCodeSystems(
			@OptionalParam(name="id") String id,
			@OptionalParam(name="url") String url,
			@OptionalParam(name="version") String version,
			@OptionalParam(name="_elements") StringAndListParam elementsParam
	) {

		List<String> elements = getMultiValueParam(elementsParam);
		List<CodeSystem> codeSystems = new ArrayList<>();
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		if (codeSystem != null) {
			CodeSystem hapi = codeSystem.toHapi(elements);
			if ((id == null || hapi.getId().equals(id)) &&
					(url == null || hapi.getUrl().equals(url)) &&
					(version == null || version.equals(hapi.getVersion()))
			) {
				codeSystems.add(hapi);
			}
		}
		return codeSystems;
	}

	@Read()
	public CodeSystem getCodeSystem(@IdParam IdType id) throws IOException {
		String idPart = id.getIdPart();
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		if (codeSystem != null) {
			CodeSystem hapi = codeSystem.toHapi(codeSystemRepository.getContentLanguageCodes());
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
		FHIRCodeSystem codeSystem = getCodeSystemVersionOrThrow(system, version, coding);
		List<LanguageDialect> languageDialects = languageDialectParser.parseDisplayLanguageWithDefaultFallback(displayLanguage, request.getHeader(ACCEPT_LANGUAGE_HEADER));
		return codeSystemService.lookup(codeSystem, recoverCode(code, coding), languageDialects);
	}

	@Operation(name="$subsumes", idempotent=true)
	public Parameters subsumes(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="codeA") CodeType codeA,
			@OperationParam(name="codeB") CodeType codeB,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="codingA") Coding codingA,
			@OperationParam(name="codingB") Coding codingB,
			@OperationParam(name="date") StringType date) {

		// Validate parameters
		requireExactlyOneOf("codeA", codeA, "codingA", codingA);
		requireExactlyOneOf("codeB", codeB, "codingB", codingB);
		notSupported("date", date);
		
		// Get code system
		FHIRCodeSystem codeSystem = getCodeSystemVersionOrThrow(system, version, codingA != null ? codingA : codingB);
		
		// Extract codes
		String codeAValue = recoverCode(codeA, codingA);
		String codeBValue = recoverCode(codeB, codingB);
		
		return codeSystemService.subsumes(codeSystem, codeAValue, codeBValue);
	}

	@Operation(name="$validate-code", idempotent=true)
	public Parameters validateCode(
			HttpServletRequest request,
			@IdParam(optional = true) IdType id,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="code") String code,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="version") String systemVersion,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="codeableConcept") CodeableConcept codeableConcept,
			@OperationParam(name="display") String display,
			@OperationParam(name="date") String date,
			@OperationParam(name="abstract") BooleanType abstractBool,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="system-version") String incorrectParamSystemVersion) throws IOException {

		notSupported("date", date);
		parameterNamingHint("system-version", incorrectParamSystemVersion, "version");

		requireExactlyOneOf("code", code, "coding", coding, "codeableConcept", codeableConcept);
		if (code != null && system == null && url == null && id == null) {
			throw exception("One of 'system', 'url', or the CodeSystem id must be supplied when using 'code'.",
					OperationOutcome.IssueType.INVARIANT, 400);
		}
		mutuallyRequired("display", display, "code", code, "coding", coding);

		Set<Coding> codingsToValidate = new HashSet<>();
		if (code != null) {
			String systemFromUrl = url != null ? FHIRHelper.toString(url) : SNOMED_URI;
			String codeSystemUrl = system != null ? FHIRHelper.toString(system) : systemFromUrl;
			codingsToValidate.add(new Coding(codeSystemUrl, code, display).setVersion(systemVersion));
		} else if (coding != null) {
			coding.setDisplay(display);
			codingsToValidate.add(coding);
		} else {
			codingsToValidate.addAll(codeableConcept.getCoding());
		}
		if (codingsToValidate.isEmpty()) {
			throw exception("No codings provided to validate.", OperationOutcome.IssueType.INVALID, 400);
		}

		FHIRCodeSystem loadedCodeSystem = codeSystemRepository.getCodeSystem();
		if (loadedCodeSystem == null) {
			throw exception("No CodeSystem is loaded on this server.", OperationOutcome.IssueType.NOTFOUND, 404);
		}

		Coding firstCoding = codingsToValidate.size() == 1 ? codingsToValidate.iterator().next() : null;
		Coding codingForParams = coding != null ? coding : firstCoding;
		CodeSystemVersionParams codeSystemVersionParams = getCodeSystemVersionParams(id, url != null ? url : system,
				systemVersion != null ? new StringType(systemVersion) : null, codingForParams);
		if (!codeSystemVersionParams.matchesCodeSystem(loadedCodeSystem)) {
			Parameters response = new Parameters();
			response.addParameter("result", false);
			response.addParameter("message", format("Code system not found for parameters %s.", codeSystemVersionParams));
			return response;
		}

		List<LanguageDialect> languageDialects = languageDialectParser.parseAcceptLanguageHeader(displayLanguage);
		return codeSystemService.validateCode(loadedCodeSystem, codingsToValidate, languageDialects, displayLanguage);
	}

	private FHIRCodeSystem getCodeSystemVersionOrThrow(UriType system, StringType version, Coding coding) {
		CodeSystemVersionParams codeSystemVersionParams = getCodeSystemVersionParams(system, version, coding);
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		if (codeSystemVersionParams.matchesCodeSystem(codeSystem)) {
			return codeSystem;
		}
		throw exception(format("Code system not found for parameters %s.", codeSystemVersionParams), OperationOutcome.IssueType.NOTFOUND, 404);
	}


	@Override
	public Class<CodeSystem> getResourceType() {
		return CodeSystem.class;
	}
}
