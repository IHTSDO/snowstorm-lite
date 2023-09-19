package org.snomed.snowstormlite.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import org.snomed.snowstormlite.fhir.LoadPackageServlet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.MultipartConfigElement;
import java.io.IOException;
import java.nio.file.Files;

@Configuration
public class FHIRConfig {

	private static final int MB_IN_BYTES = 1024 * 1024;

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
	public ServletRegistrationBean<LoadPackageServlet> addLoadPackageServlet(@Value("${fhir.servlet.max-file-size.mb}") Integer maxUploadSizeInMB) throws IOException {
		ServletRegistrationBean<LoadPackageServlet> registrationBean = new ServletRegistrationBean<>(new LoadPackageServlet(), "/fhir-admin/load-package");
		long maxFileSizeInBytes = (long) MB_IN_BYTES * maxUploadSizeInMB;
		registrationBean.setMultipartConfig(
				new MultipartConfigElement(Files.createTempDirectory("fhir-bundle-upload").toFile().getAbsolutePath(), maxFileSizeInBytes, maxFileSizeInBytes, 0));
		return registrationBean;
	}

}
