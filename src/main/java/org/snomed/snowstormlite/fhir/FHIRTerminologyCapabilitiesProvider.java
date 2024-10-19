package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.provider.ServerCapabilityStatementProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * See https://www.hl7.org/fhir/terminologycapabilities.html
 * See https://smilecdr.com/hapi-fhir/docs/server_plain/introduction.html#capability-statement-server-metadata
 * Call using GET [base]/metadata?mode=terminology
 * See https://github.com/jamesagnew/hapi-fhir/issues/1681
 */
public class FHIRTerminologyCapabilitiesProvider extends ServerCapabilityStatementProvider {

	private final String serverVersion;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public FHIRTerminologyCapabilitiesProvider(RestfulServer theServer, String serverVersion) {
		super(theServer);
		this.serverVersion = serverVersion;
	}

	@Metadata(cacheMillis = 0)
	public IBaseConformance getMetadataResource(HttpServletRequest request, RequestDetails requestDetails) {
		logger.info(requestDetails.getCompleteUrl());
		if (request.getParameter("mode") != null && request.getParameter("mode").equals("terminology")) {
			return new FHIRTerminologyCapabilities().withDefaults(serverVersion);
		} else {
			return super.getServerConformance(request, requestDetails);
		}
	}
}
