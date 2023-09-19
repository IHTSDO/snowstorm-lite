package org.snomed.snowstormlite.service;

import org.apache.logging.log4j.util.Strings;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.snomedimport.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppSetupService {

	@Autowired
	private ImportService importService;

	@Autowired
	private IndexSearcherProvider indexSearcherProvider;

	@Value("${index.path}")
	private String indexPath;

	@Value("${load}")
	private String loadReleaseArchives;

	@Value("${version-uri}")
	private String loadVersionUri;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void run() throws IOException, ReleaseImportException {
		if (!Strings.isEmpty(loadReleaseArchives)) {
			if (Strings.isEmpty(loadVersionUri)) {
				throw new IllegalArgumentException("Parameter 'version-uri' must be set when loading a SNOMED package.");
			}
			Set<String> filePaths = Arrays.stream(loadReleaseArchives.split(",")).collect(Collectors.toSet());
			importService.importRelease(filePaths, loadVersionUri);
		} else {
			File indexDirectory = new File(indexPath);
			File[] files = indexDirectory.listFiles();
			if (indexDirectory.isDirectory() && files != null && files.length > 0) {
				indexSearcherProvider.createIndexSearcher();
				logger.info("Snowstorm Lite started. Ready.");
			} else {
				logger.info("Snowstorm Lite started. Please load a SNOMED CT package.");
			}
		}
	}

	public void setLoadReleaseArchives(String loadReleaseArchives) {
		this.loadReleaseArchives = loadReleaseArchives;
	}

	public void setLoadVersionUri(String loadVersionUri) {
		this.loadVersionUri = loadVersionUri;
	}

}
