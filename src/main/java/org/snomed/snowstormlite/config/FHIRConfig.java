package org.snomed.snowstormlite.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.TerminologyCapabilities;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FHIRConfig {

	private final BuildProperties buildProperties;
	private final CodeSystemRepository codeSystemRepository;

	public FHIRConfig(@Autowired(required = false) BuildProperties buildProperties, @Autowired CodeSystemRepository codeSystemRepository) {
		this.buildProperties = buildProperties;
		this.codeSystemRepository = codeSystemRepository;
	}

	@Bean
	public FhirContext fhirContext() {
		return FhirContext.forR4();
	}

	@Bean
	public ServletRegistrationBean<HapiRestfulServlet> hapi() {
		HapiRestfulServlet hapiServlet = new HapiRestfulServlet();

		ServletRegistrationBean<HapiRestfulServlet> servletRegistrationBean = new ServletRegistrationBean<>(hapiServlet, "/fhir/*");
		hapiServlet.setServerName("Snowstorm Lite FHIR Terminology Server");
		hapiServlet.setServerVersion(buildProperties != null ? buildProperties.getVersion() : "development");
		hapiServlet.setDefaultResponseEncoding(EncodingEnum.JSON);
		hapiServlet.setImplementationDescription("Snowstorm Lite");

		hapiServlet.registerInterceptor(new ResponseHighlighterInterceptor() {
			@Override
			public void capabilityStatementGenerated(RequestDetails theRequestDetails, IBaseConformance theCapabilityStatement) {
				if (!(theCapabilityStatement instanceof TerminologyCapabilities)) {
					super.capabilityStatementGenerated(theRequestDetails, theCapabilityStatement);
				}
			}
		});
		hapiServlet.registerInterceptor(new CapabilityStatementCustomizer(codeSystemRepository));

		return servletRegistrationBean;
	}

	@Bean
	@ConfigurationProperties(prefix = "fhir.conceptmap")
	public FHIRConceptMapImplicitConfig getFhirConceptMapImplicitConfig() {
		return new FHIRConceptMapImplicitConfig();
	}

}
