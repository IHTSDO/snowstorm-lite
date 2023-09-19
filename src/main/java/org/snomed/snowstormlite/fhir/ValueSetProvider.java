package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;
import org.snomed.snowstormlite.service.ValueSetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.snomed.snowstormlite.fhir.FHIRHelper.exception;

@Component
public class ValueSetProvider implements IResourceProvider {

	@Autowired
	private ValueSetService valueSetService;

	@Search
	public List<ValueSet> search() {
		// TODO
		return Collections.emptyList();
	}

	@Operation(name = "$expand", idempotent = true)
	public ValueSet expand(
			@OperationParam(name="url") UriType url,
			@OperationParam(name="filter") String filter,
			@OperationParam(name="offset") IntegerType offset,
			@OperationParam(name="count") IntegerType count
			) throws IOException {

		if (url == null || url.isEmpty()) {
			throw exception("Use the 'url' parameter.", OperationOutcome.IssueType.INVARIANT, 400);
		}

		return valueSetService.expand(url.getValue(), filter, offset != null ? offset.getValue() : 0, count != null ? count.getValue() : 10);
	}

	@Override
	public Class<ValueSet> getResourceType() {
		return ValueSet.class;
	}
}
