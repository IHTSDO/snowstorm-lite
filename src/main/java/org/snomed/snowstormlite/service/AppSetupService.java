package org.snomed.snowstormlite.service;

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

/**
 * Application Setup Service responsible for initializing Snowstorm Lite with SNOMED CT data.
 * 
 * This service handles three primary initialization modes:
 * 1. Syndication-based loading: Downloads SNOMED CT packages from MLDS syndication service
 * 2. File-based loading: Imports local SNOMED CT RF2 archive files
 * 3. Index validation: Checks existing Lucene index and enables read operations
 * 
 * Key responsibilities:
 * - Coordinate data loading strategies based on configuration parameters
 * - Manage temporary file cleanup after syndication downloads
 * - Handle Lucene version compatibility issues
 * - Provide graceful error handling with appropriate exit codes
 * 
 * Python implementation notes:
 * - Replace Spring dependency injection with explicit dependency management
 * - Use environment variables or configuration files for parameter injection
 * - Implement similar try-catch logic for error handling and cleanup
 * - Consider using asyncio for syndication downloads and file processing
 */
@Service
public class AppSetupService {

	@Autowired
	private ImportService importService;

	@Autowired
	private IndexIOProvider indexIOProvider;

	@Autowired
	private SyndicationClient syndicationClient;

	// Configuration parameters injected from application.properties
	@Value("${index.path}")
	private String indexPath;

	@Value("${syndicate}")
	private String useSyndication;

	@Value("${load}")
	private String loadReleaseArchives;

	@Value("${version-uri}")
	private String loadVersionUri;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * Main application initialization method that determines and executes the appropriate data loading strategy.
	 * 
	 * Logic flow:
	 * 1. Check if syndication is enabled (useSyndication is empty/null means syndication is requested)
	 * 2. If syndication: authenticate, fetch feed, find entry, download packages, import
	 * 3. If file loading: parse comma-separated file paths and import directly
	 * 4. If neither: validate existing index and enable read operations
	 * 
	 * Error handling:
	 * - Lucene version incompatibility: Provide clear guidance to delete index
	 * - Parameter validation: Ensure required version-uri is present
	 * - File cleanup: Remove temporary downloaded files after import
	 * 
	 * Python implementation considerations:
	 * - Use pathlib for file operations instead of Java File
	 * - Implement proper exception hierarchy for different error types
	 * - Use context managers for file cleanup (with statements)
	 * - Consider using requests library for HTTP operations in syndication
	 */
	public void run() throws IOException, ReleaseImportException {
		try {
			// Branch 1: Syndication-based loading
			// Note: useSyndication is empty/null when syndication is requested (Spring property behavior)
			if (Strings.isEmpty(useSyndication)) {
				// Validate required parameter for syndication
				if (Strings.isEmpty(loadVersionUri)) {
					throw new IllegalArgumentException("Parameter 'version-uri' must be set when loading SNOMED via syndication.");
				}
				
				// Step 1: Authenticate with syndication service (typically MLDS)
				// Returns username/password pair for subsequent API calls
				Pair<String, String> syndicationCredentials = syndicationClient.getSyndicationCredentials();
				
				// Step 2: Fetch the syndication feed (XML/JSON feed containing available packages)
				// This contains metadata about all available SNOMED CT releases
				SyndicationFeed feed = syndicationClient.getFeed();
				
				// Step 3: Find the specific entry matching the requested version URI
				// Searches through feed entries to locate the exact SNOMED CT version requested
				SyndicationFeedEntry entry = syndicationClient.findEntry(loadVersionUri, feed);
				
				// Step 4: Download the packages associated with this entry
				// Returns set of file paths to downloaded temporary files
				Set<String> filePaths = syndicationClient.downloadPackages(entry, feed, syndicationCredentials);
				
				if (filePaths != null) {
					// Extract version identifier from the feed entry for import labeling
					String contentItemVersion = entry.getContentItemVersion();

					// Step 5: Import the downloaded RF2 files into Lucene index
					importService.importRelease(filePaths, contentItemVersion);
					
					// Step 6: Cleanup temporary downloaded files to free disk space
					// Important: Always cleanup temp files to prevent disk space issues
					for (String filePath : filePaths) {
						if (!new File(filePath).delete()) {
							logger.info("Failed to delete temp file {}", filePath);
						}
					}
				}
			} 
			// Branch 2: File-based loading from local RF2 archives
			else if (!Strings.isEmpty(loadReleaseArchives)) {
				// Validate required parameter for file loading
				if (Strings.isEmpty(loadVersionUri)) {
					throw new IllegalArgumentException("Parameter 'version-uri' must be set when loading a SNOMED package.");
				}
				
				// Parse comma-separated list of file paths into a set
				// Supports multiple RF2 files (e.g., International + Extension)
				Set<String> filePaths = Arrays.stream(loadReleaseArchives.split(",")).collect(Collectors.toSet());
				
				// Import directly from local files without download step
				importService.importRelease(filePaths, loadVersionUri);
			} 
			// Branch 3: Use existing index (no data loading required)
			else {
				// Check if existing Lucene index is available and contains data
				File indexDirectory = new File(indexPath);
				File[] files = indexDirectory.listFiles();
				
				if (indexDirectory.isDirectory() && files != null && files.length > 0) {
					// Initialize index reader for query operations
					indexIOProvider.enableRead();
					logger.info("Snowstorm Lite started. Ready.");
				} else {
					// No index available - application ready but requires data loading via API
					logger.info("Snowstorm Lite started. Please load a SNOMED CT package.");
				}
			}
		} catch (IllegalArgumentException e) {
			// Special handling for Lucene version compatibility issues
			// This occurs when index was created with different Lucene version
			if (e.getMessage().contains("Could not load codec")) {
				String message = "New Lucene engine detected. Please delete directory '%s' and import again.".formatted(indexPath);
				logger.error(message);
				System.out.println();
				System.err.println(message);
				System.out.println();
			} else {
				// General parameter validation errors
				logger.error("Illegal Argument: {}", e.getMessage());
				System.err.printf("%nIllegal Argument: %s%n%n", e.getMessage());
			}

			// Exit application on initialization failure
			System.exit(1);
		} catch (ServiceException e) {
			// Handle service-level errors (network, file I/O, etc.)
			logger.debug(e.getMessage(), e);
			logger.error(e.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Setter for test purposes - allows overriding the file paths for RF2 archives.
	 * Used primarily in unit tests to provide controlled test data paths.
	 * 
	 * Python implementation: Use property setters or configuration override mechanisms
	 */
	public void setLoadReleaseArchives(String loadReleaseArchives) {
		this.loadReleaseArchives = loadReleaseArchives;
	}

	/**
	 * Setter for test purposes - allows overriding the version URI.
	 * Used primarily in unit tests to provide controlled version identifiers.
	 * 
	 * Python implementation: Use property setters or configuration override mechanisms
	 */
	public void setLoadVersionUri(String loadVersionUri) {
		this.loadVersionUri = loadVersionUri;
	}

}
