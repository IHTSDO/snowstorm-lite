package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;

public class FHIRServerResponseException extends BaseServerResponseException {
	public FHIRServerResponseException(int theStatusCode, String theMessage, IBaseOperationOutcome theBaseOperationOutcome) {
		super(theStatusCode, theMessage, theBaseOperationOutcome);
	}

	public FHIRServerResponseException(int theStatusCode, String theMessage, IBaseOperationOutcome theBaseOperationOutcome, Throwable e) {
		super(theStatusCode, theMessage, e, theBaseOperationOutcome);
	}
}
