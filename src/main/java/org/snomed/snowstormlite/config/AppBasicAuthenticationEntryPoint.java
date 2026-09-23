package org.snomed.snowstormlite.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;

@Component
public class AppBasicAuthenticationEntryPoint extends BasicAuthenticationEntryPoint {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authEx) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
			// Dashboard request: omit the Basic challenge so the browser does not show its native login dialog,
			// the dashboard shows its own sign-in modal instead.
			response.setContentType("application/json");
			response.getWriter().println("{\"message\":\"Admin credentials required.\"}");
			return;
		}
		response.addHeader("WWW-Authenticate", "Basic realm=" + getRealmName() + "");
		PrintWriter writer = response.getWriter();
		writer.println("HTTP Status 401 - " + authEx.getMessage());
	}

	@Override
	public void afterPropertiesSet() {
		setRealmName("snomedtools");
		super.afterPropertiesSet();
	}

}
