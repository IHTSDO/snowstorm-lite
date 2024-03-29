package org.snomed.snowstormlite.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FHIRConfig {

	@Bean
	public FhirContext fhirContext() {
		return FhirContext.forR4();
	}

	@Bean
	public ServletRegistrationBean<HapiRestfulServlet> hapi() {
		HapiRestfulServlet hapiServlet = new HapiRestfulServlet();

		ServletRegistrationBean<HapiRestfulServlet> servletRegistrationBean = new ServletRegistrationBean<>(hapiServlet, "/fhir/*");
		hapiServlet.setServerName("Snowstorm Lite FHIR Terminology Server");
		hapiServlet.setServerVersion(getClass().getPackage().getImplementationVersion());
		hapiServlet.setDefaultResponseEncoding(EncodingEnum.JSON);

		ResponseHighlighterInterceptor interceptor = new ResponseHighlighterInterceptor();
		hapiServlet.registerInterceptor(interceptor);

		return servletRegistrationBean;
	}

	@Bean
	@ConfigurationProperties(prefix = "fhir.conceptmap")
	public FHIRConceptMapImplicitConfig getFhirConceptMapImplicitConfig() {
		return new FHIRConceptMapImplicitConfig();
	}

}
