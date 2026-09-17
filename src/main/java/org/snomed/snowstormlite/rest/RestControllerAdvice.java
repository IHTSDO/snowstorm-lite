package org.snomed.snowstormlite.rest;

import org.snomed.snowstormlite.fhir.FHIRServerResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

@org.springframework.web.bind.annotation.ControllerAdvice
public class RestControllerAdvice {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
	}

	@ExceptionHandler(FHIRServerResponseException.class)
	public ResponseEntity<String> handleFhirServerResponseException(FHIRServerResponseException exception) {
		return ResponseEntity.status(exception.getStatusCode()).body(exception.getMessage());
	}

}
