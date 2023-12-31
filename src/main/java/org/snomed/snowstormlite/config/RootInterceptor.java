package org.snomed.snowstormlite.config;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;

import static java.lang.String.format;

public class RootInterceptor extends InterceptorAdapter {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	* Override the incomingRequestPreProcessed method, which is called
	* for each incoming request before any processing is done
	*/
	@Override
	public boolean incomingRequestPreProcessed(HttpServletRequest request, HttpServletResponse response) {
		try {
			//The base URL will return a static HTML page
			String pathInfo = request.getPathInfo();
			if (StringUtils.isEmpty(pathInfo) || pathInfo.equals("/")) {
				response.setContentType("text/html");
				InputStream inputStream = this.getClass().getResourceAsStream("/static/index.html");
				if (inputStream == null) {
					throw new ConfigurationException("Did not find internal resource file fhir/index.html");
				}
				StreamUtils.copy(inputStream, response.getOutputStream());
				return false;
			} else if (pathInfo.startsWith("/.well-known/")) {
				String filename = pathInfo.replaceFirst("/.well-known/", "");
				String path = format("/well-known-resources/%s", filename);
				request.getServletContext().getRequestDispatcher(path).forward(request, response);
			}
		} catch (Exception e) {
			logger.error("Failed to intercept request", e);
		}
		return true;
	}
	
}
