package org.snomed.snowstormmicro.fhir;

import org.hl7.fhir.r4.model.OperationOutcome;

public class FHIRHelper {

	public static FHIRServerResponseException exception(String message, OperationOutcome.IssueType issueType, int theStatusCode) {
		return exception(message, issueType, theStatusCode, null);
	}

	public static FHIRServerResponseException exception(String message, OperationOutcome.IssueType issueType, int theStatusCode, Throwable e) {
		OperationOutcome outcome = new OperationOutcome();
		OperationOutcome.OperationOutcomeIssueComponent component = new OperationOutcome.OperationOutcomeIssueComponent();
		component.setSeverity(OperationOutcome.IssueSeverity.ERROR);
		component.setCode(issueType);
		component.setDiagnostics(message);
		outcome.addIssue(component);
		return new FHIRServerResponseException(theStatusCode, message, outcome, e);
	}

}
