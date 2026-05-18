package org.snomed.snowstormlite.config;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static java.lang.String.format;

public class FHIRContextInterceptor extends InterceptorAdapter {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String FHIR_RESOURCE_ROOT = "/fhir";

	/**
	 * Override the incomingRequestPreProcessed method, which is called
	 * for each incoming request before any processing is done
	 */
	@Override
	public boolean incomingRequestPreProcessed(HttpServletRequest request, HttpServletResponse response) {
		try {
			String pathInfo = request.getPathInfo();

			// /fhir (no trailing slash) -> 302 to /fhir/ for GET/HEAD so the dashboard URL matches the FHIR base path.
			// POST without slash must reach HAPI (e.g. Batch Bundle); do not redirect POST or DELETE.
			if (StringUtils.isEmpty(pathInfo)) {
				String method = request.getMethod();
				if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
					StringBuilder location = new StringBuilder(request.getContextPath()).append("/fhir/");
					String queryString = request.getQueryString();
					if (queryString != null) {
						location.append('?').append(queryString);
					}
					response.sendRedirect(location.toString());
					return false;
				}
				return true;
			}

			// Dashboard shell only for GET/HEAD at /fhir/. POST /fhir/ is reserved for FHIR Batch Bundles etc.
			if (pathInfo.equals("/")) {
				String method = request.getMethod();
				if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
					response.setContentType("text/html; charset=UTF-8");
					try (InputStream ios = FHIRContextInterceptor.class.getResourceAsStream(FHIR_RESOURCE_ROOT + "/index.html")) {
						if (ios == null) {
							throw new ConfigurationException("Did not find internal resource file fhir/index.html");
						}
						if ("HEAD".equalsIgnoreCase(method)) {
							return false;
						}
						ios.transferTo(response.getOutputStream());
					}
					return false;
				}
				return true;
			}

			if (serveDashboardStaticIfApplicable(request, response, pathInfo)) {
				return false;
			}

			if (pathInfo.startsWith("/.well-known/")) {
				String filename = pathInfo.replaceFirst("/.well-known/", "");
				String path = format("/well-known-resources/%s", filename);
				request.getServletContext().getRequestDispatcher(path).forward(request, response);
				return false;
			} else if (pathInfo.equals("/partial-hierarchy")) {
				// Forward /fhir/partial-hierarchy to /partial-hierarchy to avoid HAPI FHIR interception
				request.getServletContext().getRequestDispatcher("/partial-hierarchy").forward(request, response);
				return false;
			}
		} catch (Exception e) {
			logger.error("Failed to intercept request", e);
		}
		return true;
	}

	/**
	 * Dashboard assets live under classpath:/fhir/{css,js,images}/ and are requested as /fhir/css/..., etc.
	 * (The FHIR servlet is mapped to /fhir/* so Spring's static handler never sees these paths.)
	 */
	private boolean serveDashboardStaticIfApplicable(HttpServletRequest request, HttpServletResponse response, String pathInfo)
			throws IOException {
		if (!pathInfo.startsWith("/css/") && !pathInfo.startsWith("/js/") && !pathInfo.startsWith("/images/")) {
			return false;
		}
		if (pathInfo.contains("..")) {
			return false;
		}
		String method = request.getMethod();
		if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
			return false;
		}

		String classpathLocation = FHIR_RESOURCE_ROOT + pathInfo;
		try (InputStream ios = FHIRContextInterceptor.class.getResourceAsStream(classpathLocation)) {
			if (ios == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return true;
			}

			String contentType = contentTypeForPath(pathInfo);
			if (contentType != null) {
				response.setContentType(contentType);
			}
			if ("HEAD".equalsIgnoreCase(method)) {
				return true;
			}
			ios.transferTo(response.getOutputStream());
		}
		return true;
	}

	private static String contentTypeForPath(String pathInfo) {
		String lower = pathInfo.toLowerCase();
		if (lower.endsWith(".css")) {
			return "text/css; charset=UTF-8";
		}
		if (lower.endsWith(".js")) {
			return "text/javascript; charset=UTF-8";
		}
		if (lower.endsWith(".svg")) {
			return "image/svg+xml";
		}
		if (lower.endsWith(".ico")) {
			return "image/x-icon";
		}
		return null;
	}

}
