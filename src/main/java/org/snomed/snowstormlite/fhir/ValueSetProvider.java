package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.r4.model.*;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSet;
import org.snomed.snowstormlite.service.ValueSetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.snomed.snowstormlite.fhir.FHIRConstants.ACCEPT_LANGUAGE_HEADER;
import static org.snomed.snowstormlite.fhir.FHIRHelper.*;

@Component
public class ValueSetProvider implements IResourceProvider {

	@Autowired
	private ValueSetService valueSetService;

	@Autowired
	private FhirContext fhirContext;

	@Autowired
	private LanguageDialectParser languageDialectParser;

	@Search
	public List<ValueSet> search() throws IOException {
		return valueSetService.findAll().stream().peek(vs -> vs.setCompose(null)).map(FHIRValueSet::toHapi).collect(Collectors.toList());
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
		notSupported("version", version);// Not part of the FHIR API spec but requested under MAINT-1363

		int count = countType != null ? countType.getValue() : 100;

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
			return valueSetService.expand(new FHIRValueSet(valueSet), filter, languageDialects, toBool(includeDesignationsType), requestedProperties, offset != null ? offset.getValue() : 0, count);
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
