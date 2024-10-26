package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.*;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.FHIRDescription;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSet;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.ValueSetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRConstants.ACCEPT_LANGUAGE_HEADER;
import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI;
import static org.snomed.snowstormlite.fhir.FHIRHelper.*;

@Component
public class ValueSetProvider implements IResourceProvider {

	@Autowired
	private ValueSetService valueSetService;

	@Autowired
	private FhirContext fhirContext;

	@Autowired
	private LanguageDialectParser languageDialectParser;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Search
	public List<ValueSet> search(
			@OptionalParam(name="url") UriType url,
			@OptionalParam(name="version") String version) throws IOException {

		return valueSetService.findAll().stream()
				.filter(vs -> url == null || url.equals(vs.getUrl()))
				.filter(vs -> version == null || version.equals(vs.getVersion()))
				.peek(vs -> vs.setCompose(null))
				.map(FHIRValueSet::toHapi)
				.toList();
	}

	@Read()
	public ValueSet getValueSet(@IdParam IdType id) throws IOException {
		FHIRValueSet internalValueSet = valueSetService.findById(id.getIdPart());
		if (internalValueSet != null) {
			return internalValueSet.toHapi();
		}
		return null;
	}

	@Create()
	public MethodOutcome createValueSet(@IdParam IdType id, @ResourceParam ValueSet valueSet) throws IOException {
		MethodOutcome outcome = new MethodOutcome();
		if (id != null) {
			valueSet.setId(id);
		}
		FHIRValueSet savedValueSet = valueSetService.createOrUpdateValueset(valueSet);
		outcome.setId(new IdType("ValueSet", savedValueSet.getId()));
		return outcome;
	}

	@Update
	public MethodOutcome updateValueSet(@IdParam IdType id, @ResourceParam ValueSet vs) {
		try {
			return createValueSet(id, vs);
		} catch (IOException e) {
			throw exception("Failed to update/create valueset '" + vs.getId() + "'", OperationOutcome.IssueType.EXCEPTION, 400, e);
		}
	}

	@Delete
	public void deleteValueSet(
			@IdParam IdType id,
			@OptionalParam(name="url") UriType url,
			@OptionalParam(name="version") String version) throws IOException {

		if (id != null) {
			if (valueSetService.findById(id.getIdPart()) == null) {
				throw FHIRHelper.exception("A ValueSet with this id was not found.", OperationOutcome.IssueType.NOTFOUND, 404);
			}
			valueSetService.deleteById(id.getIdPart());
		} else {
			FHIRHelper.required("url", url);
			FHIRHelper.required("version", version);
			FHIRValueSet internalValueSet = valueSetService.find(url.getValueAsString(), version);
			if (internalValueSet == null) {
				throw FHIRHelper.exception("A ValueSet with this url and version was not found.", OperationOutcome.IssueType.NOTFOUND, 404);
			} else {
				valueSetService.deleteById(internalValueSet.getId());
			}
		}
	}

	@Operation(name = "$expand", idempotent = true)
	public ValueSet expand(
			HttpServletRequest request,
			HttpServletResponse response,
			@ResourceParam String rawBody,
			@IdParam(optional = true) IdType id,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="valueSetVersion") String valueSetVersion,
			@OperationParam(name="context") String context,
			@OperationParam(name="contextDirection") String contextDirection,
			@OperationParam(name="filter") String filter,
			@OperationParam(name="date") String date,
			@OperationParam(name="offset") IntegerType offset,
			@OperationParam(name="count") IntegerType countType,
			@OperationParam(name="includeDesignations") BooleanType includeDesignationsType,
			@OperationParam(name="designation") List<String> designations,
			@OperationParam(name="includeDefinition") BooleanType includeDefinition,
			@OperationParam(name="activeOnly") BooleanType activeType,
			@OperationParam(name="excludeNested") BooleanType excludeNested,
			@OperationParam(name="excludeNotForUI") BooleanType excludeNotForUI,
			@OperationParam(name="excludePostCoordinated") BooleanType excludePostCoordinated,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="exclude-system") StringType excludeSystem,
			@OperationParam(name="system-version") StringType systemVersion,
			@OperationParam(name="check-system-version") StringType checkSystemVersion,
			@OperationParam(name="force-system-version") StringType forceSystemVersion,
			@OperationParam(name="version") StringType version)// Invalid parameter
	{

		notSupported("valueSetVersion", valueSetVersion);
		notSupported("context", context);
		notSupported("contextDirection", contextDirection);
		notSupported("date", date);
		notSupported("designation", designations);
		notSupported("excludeNested", excludeNested);
		notSupported("excludeNotForUI", excludeNotForUI);
		notSupported("excludePostCoordinated", excludePostCoordinated);
		parameterNamingHint("version", version, "system-version");

		int count = countType != null ? countType.getValue() : 100;

		return doExpand(request, rawBody, id, url, filter, offset, includeDesignationsType, displayLanguage, count, null).getFirst();
	}

	@Operation(name="$validate-code", idempotent=true)
	public Parameters validateCodeExplicit(
			HttpServletRequest request,
			@ResourceParam String rawBody,
			@IdParam(optional = true) IdType id,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="context") String context,
			@OperationParam(name="valueSet") ValueSet valueSet,
			@OperationParam(name="valueSetVersion") String valueSetVersion,
			@OperationParam(name="code") String code,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="systemVersion") String systemVersion,
			@OperationParam(name="display") String display,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="codeableConcept") CodeableConcept codeableConcept,
			@OperationParam(name="date") String date,
			@OperationParam(name="abstract") BooleanType abstractBool,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="system-version") String incorrectParamSystemVersion) {

		// TODO: Support this?
		notSupported("valueSetVersion", valueSetVersion);

		notSupported("context", context);
		notSupported("date", date);
		parameterNamingHint("system-version", incorrectParamSystemVersion, "systemVersion");

		requireExactlyOneOf("code", code, "coding", coding, "codeableConcept", codeableConcept);
		mutuallyRequired("code", code, "system", system);
		mutuallyRequired("display", display, "code", code, "coding", coding);

		// Get set of codings - one of which needs to be valid
		Set<Coding> codingsToValidate = new HashSet<>();
		if (code != null) {
			codingsToValidate.add(new Coding(FHIRHelper.toString(system), code, display).setVersion(systemVersion));
		} else if (coding != null) {
			coding.setDisplay(display);
			codingsToValidate.add(coding);
		} else {
			codingsToValidate.addAll(codeableConcept.getCoding());
		}
		if (codingsToValidate.isEmpty()) {
			throw exception("No codings provided to validate.", OperationOutcome.IssueType.INVALID, 400);
		}

		// If any display terms are in the validation input: grab all the relevant display terms from the store
		BooleanType includeDesignations = new BooleanType(codingsToValidate.stream().anyMatch(coding1 -> coding1.getDisplay() != null));

		// Resolve and expand the ValueSet with the requested codes
		Pair<ValueSet, List<FHIRConcept>> expandedValueSetAndConceptPage = doExpand(request, rawBody, id, url, null, new IntegerType(0),
				includeDesignations, displayLanguage, codingsToValidate.size(), codingsToValidate);
		List<FHIRConcept> expandedConcepts = expandedValueSetAndConceptPage.getSecond();

		Parameters response = new Parameters();

		// Add response details about the coding, if there is only one
		if (codingsToValidate.size() == 1) {
			Coding codingA = codingsToValidate.iterator().next();
			response.addParameter("code", codingA.getCode());
			response.addParameter("system", codingA.getSystem());
		}

		// Grab 'the' CodeSystem (only one in Snowstorm Lite)
		FHIRCodeSystem loadedCodeSystem = codeSystemRepository.getCodeSystem();

		// Matching CodeSystem version
		Set<FHIRCodeSystem> codeSystemVersionsMatchingCodings = codingsToValidate.stream()
				.filter(coding1 -> coding1.getSystem().equals(SNOMED_URI))
				.anyMatch(coding1 -> versionsMatch(coding1, loadedCodeSystem)) ? Collections.singleton(loadedCodeSystem) : Collections.emptySet();

		if (codeSystemVersionsMatchingCodings.isEmpty()) {
			// No CodeSystem versions match
			// Produce informative message
			response.addParameter("result", false);
			if (codingsToValidate.stream().anyMatch(coding1 -> coding1.getSystem().equals(SNOMED_URI))) {
				if (codingsToValidate.size() == 1) {
					Coding codingA = codingsToValidate.iterator().next();
					response.addParameter("message", format("The system '%s' is included in this ValueSet but the version '%s' is not.",
							codingA.getSystem(), codingA.getVersion()));
				} else {
					response.addParameter("message", "One or more codes in the CodableConcept are within a system included by this ValueSet " +
							"but none of the versions match.");
				}
			} else {
				if (codingsToValidate.size() == 1) {
					Coding codingA = codingsToValidate.iterator().next();
					response.addParameter("message", format("The system '%s' is not included in this ValueSet.", codingA.getSystem()));
				} else {
					response.addParameter("message", "None of the codes in the CodableConcept are within a system included by this ValueSet.");
				}
			}
			return response;
		}

		// Add version actually used to the response
		if (codingsToValidate.size() == 1) {
			response.addParameter("version", codeSystemVersionsMatchingCodings.iterator().next().getVersionUri());
		}

		List<LanguageDialect> languageDialects = languageDialectParser.parseAcceptLanguageHeader(displayLanguage);
		for (Coding codingA : codingsToValidate) {
			FHIRConcept concept = expandedConcepts.stream()
					.filter(conceptA -> codingA.getSystem().equals(SNOMED_URI) && conceptA.getConceptId().equals(codingA.getCode())
							&& versionsMatch(codingA, loadedCodeSystem))
					.findFirst().orElse(null);

			if (concept != null) {
				if (codingsToValidate.size() == 1 && FHIRHelper.isSnomedUri(codingA.getSystem())) {
					response.addParameter("inactive", !concept.isActive());
				}
				String codingADisplay = codingA.getDisplay();
				if (codingADisplay == null) {
					response.addParameter("result", true);
					return response;
				} else {
					FHIRDescription termMatch = null;
					for (FHIRDescription designation : concept.getDescriptions()) {
						if (codingADisplay.equalsIgnoreCase(designation.getTerm())) {
							termMatch = designation;
							if (designation.getLang() == null || languageDialects.isEmpty() || languageDialects.stream()
									.anyMatch(languageDialect -> designation.getLang().equals(languageDialect.getLanguageCode()))) {
								response.addParameter("result", true);
								response.addParameter("message", format("The code '%s' was found in the ValueSet and the display matched one of the designations.",
										codingA.getCode()));
								return response;
							}
						}
					}
					if (termMatch != null) {
						response.addParameter("result", false);
						response.addParameter("message", format("The code '%s' was found in the ValueSet and the display matched the designation with term '%s', " +
										"however the language of the designation '%s' did not match any of the languages in the requested display language '%s'.",
								codingA.getCode(), termMatch.getTerm(), termMatch.getLang(), displayLanguage));
						return response;
					} else {
						response.addParameter("result", false);
						response.addParameter("message", format("The code '%s' was found in the ValueSet, however the display '%s' did not match any designations.",
								codingA.getCode(), codingA.getDisplay()));
						return response;
					}
				}
			}
		}

		response.addParameter("result", false);
		if (codingsToValidate.size() == 1) {
			Coding codingA = codingsToValidate.iterator().next();
			String codingAVersion = codingA.getVersion();
			response.addParameter("message", format("The code '%s' from CodeSystem '%s'%s was not found in this ValueSet.", codingA.getCode(), codingA.getSystem(),
					codingAVersion != null ? format(" version '%s'", codingAVersion) : ""));
		} else {
			response.addParameter("message", "None of the codes in the CodableConcept were found in this ValueSet.");
		}
		return response;
	}

	private static boolean versionsMatch(Coding coding, FHIRCodeSystem codeSystem) {
		return coding.getVersion() == null || coding.getVersion().equals(codeSystem.getEditionUri())
				|| coding.getVersion().equals(codeSystem.getVersionUri());
	}

	private Pair<ValueSet, List<FHIRConcept>> doExpand(HttpServletRequest request, String rawBody, IdType id, UriType url,
			String filter, IntegerType offset, BooleanType includeDesignationsType, String displayLanguage, int count, Set<Coding> codingsToValidate) {

		ValueSet postedValueSet = null;
		List<String> requestedProperties = Collections.emptyList();
		if (request.getMethod().equals(RequestMethod.POST.name())) {
			List<Parameters.ParametersParameterComponent> parameters = fhirContext.newJsonParser().parseResource(Parameters.class, rawBody).getParameter();
			Parameters.ParametersParameterComponent valueSetParam = findParameterOrNull(parameters, "valueSet");
			if (valueSetParam != null) {
				postedValueSet = (ValueSet) valueSetParam.getResource();
			}
			requestedProperties = getParameterValueStringsOrEmpty(parameters, "property");
		}
		String idString = id != null ? id.getIdPart() : null;
		String urlString = url != null ? URLDecoder.decode(url.getValueAsString(), StandardCharsets.UTF_8) : null;
		List<LanguageDialect> languageDialects = languageDialectParser.parseDisplayLanguageWithDefaultFallback(displayLanguage, request.getHeader(ACCEPT_LANGUAGE_HEADER));
		try {
			ValueSet valueSet = valueSetService.findOrInferValueSet(idString, urlString, postedValueSet);
			if (valueSet == null) {
				throw FHIRHelper.exception("ValueSet not found.", OperationOutcome.IssueType.NOTFOUND, 404);
			}
			return valueSetService.expand(new FHIRValueSet(valueSet), filter, languageDialects, toBool(includeDesignationsType),
					requestedProperties, offset != null ? offset.getValue() : 0, count, codingsToValidate);
		} catch (IOException e) {
			throw FHIRHelper.exceptionWithErrorLogging("Failed to expand ValueSet " + (url != null ? url : id), OperationOutcome.IssueType.EXCEPTION, 500, e);
		}
	}

	private boolean toBool(BooleanType bool) {
		return bool != null && bool.booleanValue();
	}

	@Override
	public Class<ValueSet> getResourceType() {
		return ValueSet.class;
	}
}
