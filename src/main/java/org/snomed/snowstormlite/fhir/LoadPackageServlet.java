package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.snomed.snowstormlite.snomedimport.ImportService;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.UUID;

public class LoadPackageServlet extends HttpServlet {

	private static final String MULTIPART_FORM_DATA = "multipart/form-data";
	private ImportService importService;
	private FhirContext fhirContext;

	@Override
	public void init(ServletConfig config) {
		final WebApplicationContext applicationContext = WebApplicationContextUtils.getWebApplicationContext(config.getServletContext());
		importService = applicationContext.getBean(ImportService.class);
		fhirContext = applicationContext.getBean(FhirContext.class);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String contentType = req.getContentType();
		if (!contentType.startsWith(MULTIPART_FORM_DATA)) {
			badRequest("Request must use content type " + MULTIPART_FORM_DATA, resp);
			return;
		}
		Part filePart = req.getPart("file");
		if (filePart == null) {
			badRequest("Request must include \"file\" parameter.", resp);
			return;
		}
		File tempFile = File.createTempFile("snomed-archive-upload-" + UUID.randomUUID(), ".tgz");
		try (InputStream inputStream = filePart.getInputStream()) {
			Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		String versionUri = null;
		for (Part part : req.getParts()) {
			if ("version-uri".equals(part.getName())) {
				versionUri = Streams.asString(part.getInputStream());
			}
		}
		if (versionUri == null) {
			badRequest("Request must include \"version-uri\" parameter with the URI for the SNOMED CT Edition and version being uploaded.", resp);
			return;
		}

		try {
			importService.importReleaseStreams(Collections.singleton(new FileInputStream(tempFile)), versionUri);
		} catch (ReleaseImportException e) {
			error(new FHIRServerResponseException(500, "Failed to import SNOMED CT.", new OperationOutcome()), resp);
			return;
		} catch (FHIRServerResponseException e) {
			error(e, resp);
			return;
		}
		resp.setStatus(200);
	}

	private void badRequest(String message, HttpServletResponse resp) throws IOException {
		FHIRServerResponseException exception = FHIRHelper.exception(message, OperationOutcome.IssueType.NOTSUPPORTED, 400);
		error(exception, resp);
	}

	private void error(FHIRServerResponseException exception, HttpServletResponse resp) throws IOException {
		resp.setStatus(exception.getStatusCode());
		IParser jsonParser = fhirContext.newJsonParser();
		jsonParser.setPrettyPrint(true);
		jsonParser.encodeResourceToWriter(exception.getOperationOutcome(), new OutputStreamWriter(resp.getOutputStream()));
	}

}
