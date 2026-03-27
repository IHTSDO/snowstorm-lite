package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.param.StringAndListParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.*;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRConstants.*;
import static org.snomed.snowstormlite.fhir.FHIRHelper.*;

@Component
public class CodeSystemProvider implements IResourceProvider {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private LanguageDialectParser languageDialectParser;

	@Autowired
	private ValueSetService valueSetService;

	@Autowired(required = false)
	private EmbeddingIndexService embeddingIndexService;

	@Autowired
	private FhirContext fhirContext;

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
			CodeSystem hapi = codeSystem.toHapi();
			if (hapi.getId().equals(idPart)) {
				return hapi;
			}
		}
		return null;
	}

	@Operation(name = "$lookup", idempotent = true)
	public Parameters lookupImplicit(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name = "code") CodeType code,
			@OperationParam(name = "system") UriType system,
			@OperationParam(name = "version") StringType version,
			@OperationParam(name = "coding") Coding coding,
			@OperationParam(name = "date") StringType date,
			@OperationParam(name = "displayLanguage") String displayLanguage,
			@OperationParam(name = "property") List<CodeType> propertiesType) {

		mutuallyExclusive("code", code, "coding", coding);
		notSupported("date", date);
		FHIRCodeSystem codeSystem = getCodeSystemVersionOrThrow(system, version, coding);
		List<LanguageDialect> languageDialects = languageDialectParser.parseDisplayLanguageWithDefaultFallback(displayLanguage, request.getHeader(ACCEPT_LANGUAGE_HEADER));
		return codeSystemService.lookup(codeSystem, recoverCode(code, coding), languageDialects);
	}

	@Operation(name = "$subsumes", idempotent = true)
	public Parameters subsumes(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name = "codeA") CodeType codeA,
			@OperationParam(name = "codeB") CodeType codeB,
			@OperationParam(name = "system") UriType system,
			@OperationParam(name = "version") StringType version,
			@OperationParam(name = "codingA") Coding codingA,
			@OperationParam(name = "codingB") Coding codingB,
			@OperationParam(name = "date") StringType date) {

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

	@Operation(name = "$index-embeddings", idempotent = false)
	public Parameters indexEmbeddings(
			@ResourceParam String rawBody,
			@OperationParam(name = "model") String modelParam,
			@OperationParam(name = "replace") BooleanType replaceType) {

		requireEmbeddingsEnabled();
		Parameters requestParameters = parseParametersBody(rawBody);
		String modelId = embeddingIndexService.resolveModelId(firstStringValue(requestParameters, "model").orElse(modelParam));
		boolean replace = replaceType != null ? replaceType.booleanValue() : firstBooleanValue(requestParameters, "replace").orElse(false);

		List<EmbeddingIndexService.EmbeddingInput> embeddingInputs = new ArrayList<>();
		for (Parameters.ParametersParameterComponent parameter : requestParameters.getParameter()) {
			if ("embedding".equals(parameter.getName())) {
				String code = requiredPartValue(parameter, "code");
				String vectorText = requiredPartValue(parameter, "vector");
				embeddingInputs.add(new EmbeddingIndexService.EmbeddingInput(code, embeddingIndexService.parseVector(vectorText, "embedding.vector")));
			}
		}
		if (embeddingInputs.isEmpty()) {
			throw exception("At least one 'embedding' parameter is required.", OperationOutcome.IssueType.REQUIRED, 400);
		}

		try {
			int indexed = embeddingIndexService.indexEmbeddings(modelId, embeddingInputs, replace);
			Parameters response = new Parameters();
			response.addParameter("result", true);
			response.addParameter("model", modelId);
			response.addParameter("replace", replace);
			response.addParameter("indexed", indexed);
			return response;
		} catch (IOException e) {
			throw exception("Failed to index embeddings.", OperationOutcome.IssueType.EXCEPTION, 500, e);
		}
	}

	@Operation(name = "$index-embeddings-binary", idempotent = false, manualRequest = true)
	public Parameters indexEmbeddingsBinary(
			HttpServletRequest request,
			@OperationParam(name = "model") String modelParam,
			@OperationParam(name = "replace") BooleanType replaceType) {

		requireEmbeddingsEnabled();
		String modelId = embeddingIndexService.resolveModelId(modelParam != null ? modelParam : request.getParameter("model"));
		Boolean replaceFromQuery = parseBooleanQueryParam(request.getParameter("replace"), "replace");
		boolean replace = replaceType != null ? replaceType.booleanValue() : replaceFromQuery != null && replaceFromQuery;

		String contentType = request.getContentType();
		if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("application/octet-stream")) {
			throw exception(
					"Binary embedding indexing requires content type 'application/octet-stream'.",
					OperationOutcome.IssueType.INVALID,
					400);
		}

		List<EmbeddingIndexService.EmbeddingInput> embeddingInputs = embeddingIndexService.parseBinaryEmbeddings(getRequestInputStream(request));
		try {
			int indexed = embeddingIndexService.indexEmbeddings(modelId, embeddingInputs, replace);
			Parameters response = new Parameters();
			response.addParameter("result", true);
			response.addParameter("model", modelId);
			response.addParameter("replace", replace);
			response.addParameter("indexed", indexed);
			return response;
		} catch (IOException e) {
			throw exception("Failed to index binary embeddings.", OperationOutcome.IssueType.EXCEPTION, 500, e);
		}
	}

	@Operation(name = "$semantic-match", idempotent = true)
	public Parameters semanticMatch(
			HttpServletRequest request,
			@OperationParam(name = "text") String text,
			@OperationParam(name = "vector") String vector,
			@OperationParam(name = "ecl") String ecl,
			@OperationParam(name = "count") IntegerType countType,
			@OperationParam(name = "offset") IntegerType offsetType,
			@OperationParam(name = "model") String model,
			@OperationParam(name = "displayLanguage") String displayLanguage) {

		requireEmbeddingsEnabled();
		int count = countType != null ? countType.getValue() : 20;
		int offset = offsetType != null ? offsetType.getValue() : 0;
		String modelId = embeddingIndexService.resolveModelId(model);
		float[] queryVector = embeddingIndexService.parseVector(vector, "vector");
		SemanticQueryRequest semanticQuery = SemanticQueryRequest.enabled(modelId, queryVector);
		String valueSetUrl = (ecl == null || ecl.isBlank()) ? IMPLICIT_EVERYTHING : SNOMED_URI + IMPLICIT_ECL + ecl;

		List<LanguageDialect> languageDialects = languageDialectParser.parseDisplayLanguageWithDefaultFallback(
				displayLanguage, request.getHeader(ACCEPT_LANGUAGE_HEADER));
		try {
			ValueSet semanticExpansion = valueSetService.expand(valueSetUrl, text, languageDialects, false, offset, count, semanticQuery);
			Parameters response = new Parameters();
			response.addParameter("result", true);
			response.addParameter("model", modelId);
			response.addParameter("offset", offset);
			response.addParameter("count", count);
			response.addParameter("total", semanticExpansion.getExpansion().getTotal());
			for (ValueSet.ValueSetExpansionContainsComponent containsComponent : semanticExpansion.getExpansion().getContains()) {
				Parameters.ParametersParameterComponent match = response.addParameter().setName("match");
				match.addPart().setName("code").setValue(new CodeType(containsComponent.getCode()));
				match.addPart().setName("display").setValue(new StringType(containsComponent.getDisplay()));
				containsComponent.getExtensionsByUrl(ValueSetService.SEMANTIC_SCORE_EXTENSION_URL).stream()
						.findFirst()
						.ifPresent(extension -> match.addPart().setName("score").setValue(extension.getValue()));
			}
			return response;
		} catch (IOException e) {
			throw exception("Failed to execute semantic search.", OperationOutcome.IssueType.EXCEPTION, 500, e);
		}
	}

	private void requireEmbeddingsEnabled() {
		if (embeddingIndexService == null) {
			throw exception("Embedding support is not enabled. Set 'snowstorm.embeddings.enabled=true' in application configuration.",
					OperationOutcome.IssueType.NOTSUPPORTED, 501);
		}
	}

	private FHIRCodeSystem getCodeSystemVersionOrThrow(UriType system, StringType version, Coding coding) {
		CodeSystemVersionParams codeSystemVersionParams = getCodeSystemVersionParams(system, version, coding);
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		if (codeSystemVersionParams.matchesCodeSystem(codeSystem)) {
			return codeSystem;
		}
		throw exception(format("Code system not found for parameters %s.", codeSystemVersionParams), OperationOutcome.IssueType.NOTFOUND, 404);
	}

	private Parameters parseParametersBody(String rawBody) {
		if (rawBody == null || rawBody.isBlank()) {
			throw exception("Parameters payload is required.", OperationOutcome.IssueType.REQUIRED, 400);
		}
		try {
			return fhirContext.newJsonParser().parseResource(Parameters.class, rawBody);
		} catch (Exception e) {
			throw exception("Unable to parse Parameters payload.", OperationOutcome.IssueType.INVALID, 400, e);
		}
	}

	private Optional<String> firstStringValue(Parameters parameters, String name) {
		return parameters.getParameter().stream()
				.filter(parameter -> name.equals(parameter.getName()) && parameter.getValue() instanceof PrimitiveType<?> )
				.map(parameter -> ((PrimitiveType<?>) parameter.getValue()).getValueAsString())
				.filter(value -> value != null && !value.isBlank())
				.findFirst();
	}

	private Optional<Boolean> firstBooleanValue(Parameters parameters, String name) {
		return parameters.getParameter().stream()
				.filter(parameter -> name.equals(parameter.getName()) && parameter.getValue() instanceof BooleanType)
				.map(parameter -> ((BooleanType) parameter.getValue()).booleanValue())
				.findFirst();
	}

	private String requiredPartValue(Parameters.ParametersParameterComponent parameter, String partName) {
		return parameter.getPart().stream()
				.filter(part -> partName.equals(part.getName()) && part.getValue() instanceof PrimitiveType<?> )
				.map(part -> ((PrimitiveType<?>) part.getValue()).getValueAsString())
				.filter(value -> value != null && !value.isBlank())
				.findFirst()
				.orElseThrow(() -> exception(
						format("Each embedding parameter must include non-empty part '%s'.", partName),
						OperationOutcome.IssueType.INVALID,
						400));
	}

	private Boolean parseBooleanQueryParam(String value, String parameterName) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		throw exception(
				format("Parameter '%s' must be 'true' or 'false'.", parameterName),
				OperationOutcome.IssueType.INVALID,
				400);
	}

	private java.io.InputStream getRequestInputStream(HttpServletRequest request) {
		try {
			return request.getInputStream();
		} catch (IOException e) {
			throw exception("Unable to read request body.", OperationOutcome.IssueType.INVALID, 400, e);
		}
	}

	@Override
	public Class<CodeSystem> getResourceType() {
		return CodeSystem.class;
	}
}
