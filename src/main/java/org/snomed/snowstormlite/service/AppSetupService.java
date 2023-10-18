package org.snomed.snowstormlite.service;

import org.apache.jena.ext.xerces.impl.xs.SchemaSymbols;
import org.apache.logging.log4j.util.Strings;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.snomedimport.ImportService;
import org.snomed.snowstormlite.syndication.SyndicationClient;
import org.snomed.snowstormlite.syndication.SyndicationFeed;
import org.snomed.snowstormlite.syndication.SyndicationFeedEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
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

	@Autowired
	private SyndicationClient syndicationClient;

	@Value("${index.path}")
	private String indexPath;

	@Value("${syndicate}")
	private String useSyndication;

	@Value("${load}")
	private String loadReleaseArchives;

	@Value("${version-uri}")
	private String loadVersionUri;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void run() throws IOException, ReleaseImportException {
		try {
			if (Strings.isEmpty(useSyndication)) {
				if (Strings.isEmpty(loadVersionUri)) {
					throw new IllegalArgumentException("Parameter 'version-uri' must be set when loading SNOMED via syndication.");
				}
				Pair<String, String> syndicationCredentials = syndicationClient.getSyndicationCredentials();
				SyndicationFeed feed = syndicationClient.getFeed();
				SyndicationFeedEntry entry = syndicationClient.findEntry(loadVersionUri, feed);
				Set<String> filePaths = syndicationClient.downloadPackages(entry, feed, syndicationCredentials);
				if (filePaths != null) {
					String contentItemVersion = entry.getContentItemVersion();

					importService.importRelease(filePaths, contentItemVersion);
					for (String filePath : filePaths) {
						if (!new File(filePath).delete()) {
							logger.info("Failed to delete temp file {}", filePath);
						}
					}
				}
			} else if (!Strings.isEmpty(loadReleaseArchives)) {
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
		} catch (IllegalArgumentException e) {
			logger.error("Illegal Argument: {}", e.getMessage());
			System.err.printf("%nIllegal Argument: %s%n%n", e.getMessage());

			System.exit(1);
		} catch (ServiceException e) {
			logger.debug(e.getMessage(), e);
			logger.error(e.getMessage());
			System.exit(1);
		}
	}

	public void setLoadReleaseArchives(String loadReleaseArchives) {
		this.loadReleaseArchives = loadReleaseArchives;
	}

	public void setLoadVersionUri(String loadVersionUri) {
		this.loadVersionUri = loadVersionUri;
	}

}
