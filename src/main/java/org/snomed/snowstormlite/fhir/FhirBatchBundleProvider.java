package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.CodeSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.snomed.snowstormlite.fhir.FHIRConstants.ACCEPT_LANGUAGE_HEADER;
import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI;

/**
 * Handles FHIR {@link Bundle} POST at the server base URL for {@link Bundle.BundleType#BATCH}.
 * Individual GET operations are executed independently (not transactional).
 */
@Component
public class FhirBatchBundleProvider {

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private LanguageDialectParser languageDialectParser;

	@Transaction
	public Bundle batchBundle(@TransactionParam Bundle bundle, HttpServletRequest servletRequest) {
		if (bundle.getType() != Bundle.BundleType.BATCH) {
			throw new InvalidRequestException("Only Bundle.type=batch is supported when posting a Bundle to the base FHIR URL.");
		}
		Bundle response = new Bundle();
		response.setType(Bundle.BundleType.BATCHRESPONSE);
		for (Bundle.BundleEntryComponent reqEntry : bundle.getEntry()) {
			Bundle.BundleEntryComponent respEntry = response.addEntry();
			Bundle.BundleEntryResponseComponent resp = new Bundle.BundleEntryResponseComponent();
			respEntry.setResponse(resp);
			try {
				processEntry(reqEntry, respEntry, servletRequest, resp);
			} catch (ResourceNotFoundException e) {
				resp.setStatus("404 Not Found");
				respEntry.setResource(operationOutcomeError(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.NOTFOUND, e.getMessage()));
			} catch (IllegalArgumentException e) {
				resp.setStatus("400 Bad Request");
				respEntry.setResource(operationOutcomeError(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.INVALID, e.getMessage()));
			} catch (IllegalStateException e) {
				resp.setStatus("503 Service Unavailable");
				respEntry.setResource(operationOutcomeError(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.EXCEPTION, e.getMessage()));
			} catch (IOException e) {
				resp.setStatus("500 Internal Server Error");
				respEntry.setResource(operationOutcomeError(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.EXCEPTION, e.getMessage()));
			}
		}
		return response;
	}

	private void processEntry(
			Bundle.BundleEntryComponent reqEntry,
			Bundle.BundleEntryComponent respEntry,
			HttpServletRequest servletRequest,
			Bundle.BundleEntryResponseComponent resp) throws IOException {

		Bundle.BundleEntryRequestComponent req = reqEntry.getRequest();
		if (req == null) {
			resp.setStatus("400 Bad Request");
			respEntry.setResource(operationOutcomeError(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.INVALID, "Missing bundle entry request."));
			return;
		}
		Enumeration<Bundle.HTTPVerb> methodEnum = req.getMethodElement();
		Bundle.HTTPVerb method = methodEnum != null ? methodEnum.getValue() : null;
		if (method == null) {
			resp.setStatus("400 Bad Request");
			respEntry.setResource(operationOutcomeError(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.INVALID, "Missing HTTP method on bundle entry request."));
			return;
		}
		if (method != Bundle.HTTPVerb.GET) {
			resp.setStatus("405 Method Not Allowed");
			respEntry.setResource(operationOutcomeError(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.NOTSUPPORTED,
					"Only GET requests are supported in batch entries."));
			return;
		}
		String url = req.getUrl();
		if (url == null || url.isBlank()) {
			resp.setStatus("400 Bad Request");
			respEntry.setResource(operationOutcomeError(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.INVALID, "Missing request url."));
			return;
		}

		if (url.startsWith("CodeSystem/$lookup")) {
			Parameters result = performCodeSystemLookup(servletRequest, url);
			resp.setStatus("200 OK");
			respEntry.setResource(result);
			return;
		}

		resp.setStatus("404 Not Found");
		respEntry.setResource(operationOutcomeError(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.NOTFOUND,
				"Unsupported batch entry URL: " + url));
	}

	private Parameters performCodeSystemLookup(HttpServletRequest servletRequest, String requestUrl) throws IOException {
		int q = requestUrl.indexOf('?');
		if (q < 0) {
			throw new IllegalArgumentException("CodeSystem $lookup requires query parameters.");
		}
		Map<String, String> params = splitQueryParams(requestUrl.substring(q + 1));
		String system = params.get("system");
		String code = params.get("code");
		if (system == null || code == null) {
			throw new IllegalArgumentException("Both system and code query parameters are required for CodeSystem $lookup.");
		}
		if (!SNOMED_URI.equals(system)) {
			throw new IllegalArgumentException("Unsupported system for batch lookup: " + system);
		}
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		if (codeSystem == null) {
			throw new IllegalStateException("No CodeSystem is loaded on this server.");
		}
		FHIRConcept concept = codeSystemRepository.getConcept(code);
		if (concept == null) {
			throw new ResourceNotFoundException("Concept not found: " + code);
		}
		List<LanguageDialect> dialects = languageDialectParser.parseDisplayLanguageWithDefaultFallback(null,
				servletRequest.getHeader(ACCEPT_LANGUAGE_HEADER));
		return codeSystemService.lookup(codeSystem, code, dialects);
	}

	private static Map<String, String> splitQueryParams(String query) {
		Map<String, String> map = new LinkedHashMap<>();
		for (String pair : query.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int eq = pair.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
			String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			map.put(key, value);
		}
		return map;
	}

	private static OperationOutcome operationOutcomeError(OperationOutcome.IssueSeverity severity, OperationOutcome.IssueType type, String diagnostics) {
		OperationOutcome oo = new OperationOutcome();
		oo.addIssue()
				.setSeverity(severity)
				.setCode(type)
				.setDiagnostics(diagnostics);
		return oo;
	}
}
