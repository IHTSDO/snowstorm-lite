package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.EmbeddingIndexService;
import org.snomed.snowstormlite.snomedimport.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(value = "fhir-admin", produces = "application/json")
public class AdminController {

	@Autowired
	private ImportService importService;

	@Autowired
	private FhirContext fhirContext;

	@Autowired(required = false)
	private BuildProperties buildProperties;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired(required = false)
	private EmbeddingIndexService embeddingIndexService;

	@GetMapping("features")
	public Map<String, Object> features() {
		boolean embeddingsEnabled = embeddingIndexService != null;

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("schemaVersion", 1);
		response.put("product", "snowstorm-lite");
		response.put("version", buildProperties != null ? buildProperties.getVersion() : "development");
		response.put("embeddingsSupported", embeddingsEnabled);
		response.put("semanticSearchSupported", embeddingsEnabled);
		response.put("codeSystemLoaded", codeSystemRepository.getCodeSystem() != null);

		Map<String, Object> featureFlags = new LinkedHashMap<>();
		featureFlags.put("semanticExpand", embeddingsEnabled);
		featureFlags.put("semanticMatchOperation", embeddingsEnabled);
		featureFlags.put("embeddingIndexOperation", embeddingsEnabled);
		featureFlags.put("embeddingBinaryIndexOperation", embeddingsEnabled);
		featureFlags.put("semanticSearchWithinEcl", embeddingsEnabled);
		response.put("features", featureFlags);

		if (embeddingsEnabled) {
			Map<String, Object> operations = new LinkedHashMap<>();
			operations.put("semanticExpand", "GET|POST /fhir/ValueSet/$expand?_semantic=true");
			operations.put("semanticMatch", "GET|POST /fhir/CodeSystem/$semantic-match");
			operations.put("indexEmbeddings", "POST /fhir/CodeSystem/$index-embeddings");
			operations.put("indexEmbeddingsBinary", "POST /fhir/CodeSystem/$index-embeddings-binary");
			response.put("operations", operations);
		}

		return response;
	}

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
