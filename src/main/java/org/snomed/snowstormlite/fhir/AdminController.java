package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.snomed.snowstormlite.snomedimport.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(value = "fhir-admin", produces = "application/json")
public class AdminController {

	@Autowired
	private ImportService importService;

	@Autowired
	private FhirContext fhirContext;

	@PostMapping(value = "load-package", consumes = "multipart/form-data")
	public void loadPackage(@RequestParam(name = "version-uri") String versionUri, @RequestParam MultipartFile[] file, HttpServletResponse response) throws IOException {
		if (file == null || file.length == 0) {
			error(new FHIRServerResponseException(400, "Missing file parameter.", new OperationOutcome()), response);
			return;
		}

		try {
			Set<InputStream> inputStreams = new HashSet<>();
			for (MultipartFile multipartFile : file) {
				File tempFile = File.createTempFile("snomed-archive-upload-" + UUID.randomUUID(), ".tgz");
				try (InputStream inputStream = multipartFile.getInputStream()) {
					Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
				inputStreams.add(new FileInputStream(tempFile));
			}
			importService.importReleaseStreams(inputStreams, versionUri);
		} catch (IOException | ReleaseImportException e) {
			error(new FHIRServerResponseException(500, "Failed to import SNOMED CT.", new OperationOutcome()), response);
		} catch (FHIRServerResponseException e) {
			error(e, response);
		}
	}

	private void error(FHIRServerResponseException exception, HttpServletResponse resp) throws IOException {
		resp.setStatus(exception.getStatusCode());
		IParser jsonParser = fhirContext.newJsonParser();
		jsonParser.setPrettyPrint(true);
		jsonParser.encodeResourceToWriter(exception.getOperationOutcome(), new OutputStreamWriter(resp.getOutputStream()));
	}

}
