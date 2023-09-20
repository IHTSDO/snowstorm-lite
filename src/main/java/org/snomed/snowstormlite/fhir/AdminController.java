package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.snomed.snowstormlite.snomedimport.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = "fhir-admin", produces = "application/json")
public class AdminController {

	@Autowired
	private ImportService importService;

	@Autowired
	private FhirContext fhirContext;

	@PostMapping(value = "load-package", consumes = "multipart/form-data")
	public void loadPackage(@RequestParam(name = "version-uri") String versionUri, @RequestParam MultipartFile file, HttpServletResponse response) throws IOException {

		File tempFile = File.createTempFile("snomed-archive-upload-" + UUID.randomUUID(), ".tgz");
		try (InputStream inputStream = file.getInputStream()) {
			Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		try {
			importService.importReleaseStreams(Collections.singleton(new FileInputStream(tempFile)), versionUri);
		} catch (ReleaseImportException e) {
			error(new FHIRServerResponseException(500, "Failed to import SNOMED CT.", new OperationOutcome()), response);
			return;
		} catch (FHIRServerResponseException e) {
			error(e, response);
			return;
		}
	}

	private void error(FHIRServerResponseException exception, HttpServletResponse resp) throws IOException {
		resp.setStatus(exception.getStatusCode());
		IParser jsonParser = fhirContext.newJsonParser();
		jsonParser.setPrettyPrint(true);
		jsonParser.encodeResourceToWriter(exception.getOperationOutcome(), new OutputStreamWriter(resp.getOutputStream()));
	}

}
