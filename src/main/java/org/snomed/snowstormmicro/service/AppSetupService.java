package org.snomed.snowstormmicro.service;

import org.apache.logging.log4j.util.Strings;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.NIOFSDirectory;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormmicro.loading.ImportService;
import org.snomed.snowstormmicro.util.TimerUtil;
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
	private CodeSystemService codeSystemService;

	@Autowired
	private ValueSetService valueSetService;

	@Value("${index.path}")
	private String indexPath;

	@Value("${load}")
	private String loadReleaseArchives;

	@Value("${version-uri}")
	private String loadVersionUri;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public boolean run() throws IOException, ReleaseImportException {
		if (!Strings.isEmpty(loadReleaseArchives)) {
			if (Strings.isEmpty(loadVersionUri)) {
				throw new IllegalArgumentException("Parameter 'version-uri' must be set when loading a SNOMED package.");
			}
			Set<String> filePaths = Arrays.stream(loadReleaseArchives.split(",")).collect(Collectors.toSet());
			TimerUtil timer = new TimerUtil("Import");
			importService.importRelease(filePaths, loadVersionUri);
			timer.finish();
			logger.info("Import complete");
			return true;
		} else {
			IndexSearcher indexSearcher = new IndexSearcher(DirectoryReader.open(new NIOFSDirectory(new File(indexPath).toPath())));
			codeSystemService.setIndexSearcher(indexSearcher);
			valueSetService.setIndexSearcher(indexSearcher);
		}
		return false;
	}

	public void setLoadReleaseArchives(String loadReleaseArchives) {
		this.loadReleaseArchives = loadReleaseArchives;
	}
}
