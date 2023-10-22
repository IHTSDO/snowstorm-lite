package org.snomed.snowstormlite.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.fhir.CodeSystemProvider;
import org.snomed.snowstormlite.fhir.ConceptMapProvider;
import org.snomed.snowstormlite.fhir.FHIRTerminologyCapabilitiesProvider;
import org.snomed.snowstormlite.fhir.ValueSetProvider;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.cors.CorsConfiguration;

import javax.servlet.ServletException;
import java.util.Arrays;

public class HapiRestfulServlet extends RestfulServer {

	private static final long serialVersionUID = 1L;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The initialize method is automatically called when the servlet is starting up, so it can be used to configure the
	 * servlet to define resource providers, or set up configuration, interceptors, etc.
	 */
	@Override
	protected void initialize() throws ServletException {
		final WebApplicationContext applicationContext =
				WebApplicationContextUtils.getWebApplicationContext(this.getServletContext());

		setDefaultResponseEncoding(EncodingEnum.JSON);

		final FhirContext fhirContext = applicationContext.getBean(FhirContext.class);
		final LenientErrorHandler delegateHandler = new LenientErrorHandler();
		fhirContext.setParserErrorHandler(new StrictErrorHandler() {
			@Override
			public void unknownAttribute(IParseLocation theLocation, String theAttributeName) {
				delegateHandler.unknownAttribute(theLocation, theAttributeName);
			}

			@Override
			public void unknownElement(IParseLocation theLocation, String theElementName) {
				delegateHandler.unknownElement(theLocation, theElementName);
			}

			@Override
			public void unknownReference(IParseLocation theLocation, String theReference) {
				delegateHandler.unknownReference(theLocation, theReference);
			}
		});
		setFhirContext(fhirContext);

		/*
		 * The servlet defines any number of resource providers, and configures itself to use them by calling
		 * setResourceProviders()
		 */
		setResourceProviders(
				applicationContext.getBean(CodeSystemProvider.class),
				applicationContext.getBean(ValueSetProvider.class),
				applicationContext.getBean(ConceptMapProvider.class)
		);

		setServerConformanceProvider(new FHIRTerminologyCapabilitiesProvider(this));

		// CORS configuration
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedHeader("x-fhir-starter");
		config.addAllowedHeader("Origin");
		config.addAllowedHeader("Accept");
		config.addAllowedHeader("X-Requested-With");
		config.addAllowedHeader("Content-Type");
		config.addAllowedOrigin("*");
		config.addExposedHeader("Location");
		config.addExposedHeader("Content-Location");
		config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
		registerInterceptor(new CorsInterceptor(config));

		// Register interceptors
		registerInterceptor(new RootInterceptor());

		logger.info("FHIR Resource providers and interceptors registered");
	}
}
