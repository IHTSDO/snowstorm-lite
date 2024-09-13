package org.snomed.snowstormlite.config;

import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

public class FHIRContextInterceptor extends InterceptorAdapter {
	
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
			if (pathInfo == null || pathInfo.equals("/")) {
				request.getServletContext().getRequestDispatcher("/index.html").forward(request, response);
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
