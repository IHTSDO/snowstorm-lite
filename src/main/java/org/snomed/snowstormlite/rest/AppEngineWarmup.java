package org.snomed.snowstormlite.rest;

import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
public class AppEngineWarmup {

	@RequestMapping("/_ah/warmup")
	public void getWarmupRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		LoggerFactory.getLogger(getClass()).info("Received Google App Engine warmup request.");
		request.getServletContext().getRequestDispatcher("/fhir/CodeSystem").forward(request, response);
	}

}
