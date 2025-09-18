package org.snomed.snowstormlite.syndication;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.service.ServiceException;
import org.snomed.snowstormlite.util.StreamUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

import static java.lang.String.format;

/**
 * SNOMED CT Syndication Client - handles automated downloading of SNOMED CT packages from MLDS.
 * 
 * This service provides a complete implementation for interacting with SNOMED International's
 * Managed Language Distribution Service (MLDS) syndication API. It supports:
 * 
 * Key Capabilities:
 * - Fetching and parsing Atom XML syndication feeds
 * - Searching for specific SNOMED CT package versions
 * - Handling package dependencies (International + Extensions)
 * - Authenticated downloads with progress tracking
 * - Credential management (configuration-based or interactive)
 * 
 * Syndication Feed Structure:
 * - Atom XML feed containing metadata for all available SNOMED CT packages
 * - Each entry represents a specific package version with download links
 * - Categories identify package types (SNAPSHOT, FULL, ALL)
 * - Dependencies link Extension packages to their required International base
 * 
 * Authentication Methods:
 * 1. Configuration-based: Use syndication.username/password properties
 * 2. Interactive: Prompt user for credentials at runtime
 * 3. Console-aware: Use System.console() for secure password input when available
 * 
 * Python implementation considerations:
 * ```python
 * import requests
 * import xml.etree.ElementTree as ET
 * from typing import Set, Tuple, Optional
 * import getpass
 * 
 * class SyndicationClient:
 *     def __init__(self, syndication_url: str, username: str = None, password: str = None):
 *         self.base_url = syndication_url
 *         self.session = requests.Session()
 *         self.username = username
 *         self.password = password
 *         
 *     def get_feed(self) -> dict:
 *         response = self.session.get(f"{self.base_url}/feed", 
 *                                   headers={'Accept': 'application/atom+xml'})
 *         response.raise_for_status()
 *         
 *         # Parse Atom XML feed
 *         root = ET.fromstring(response.text)
 *         return self._parse_syndication_feed(root)
 *         
 *     def download_packages(self, entry, credentials):
 *         # Implement recursive dependency resolution
 *         # Download with progress tracking using tqdm
 *         # Return list of downloaded file paths
 * ```
 */
@Service
public class SyndicationClient {

	// Acceptable SNOMED CT package types for import
	// SNAPSHOT: Latest version only, FULL: Complete historical data, ALL: Both snapshot and delta
	public static final Set<String> acceptablePackageTypes = Set.of("SCT_RF2_SNAPSHOT", "SCT_RF2_FULL", "SCT_RF2_ALL");

	private final RestTemplate restTemplate;     // HTTP client for API communication
	private final JAXBContext jaxbContext;       // XML unmarshalling context for Atom feeds
	private final String username;               // Configured syndication username
	private final String password;               // Configured syndication password
	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * Constructor initializes the syndication client with configuration and XML processing.
	 * 
	 * Setup tasks:
	 * 1. Configure RestTemplate with base URL and string message converter
	 * 2. Initialize JAXB context for XML feed parsing
	 * 3. Store authentication credentials for later use
	 * 
	 * Configuration properties:
	 * - syndication.url: Base URL for MLDS syndication service (e.g., https://mlds.ihtsdotools.org/api)
	 * - syndication.username: Optional username for authentication
	 * - syndication.password: Optional password for authentication
	 * 
	 * Python implementation:
	 * ```python
	 * def __init__(self, syndication_url: str, username: str = None, password: str = None):
	 *     self.base_url = syndication_url.rstrip('/')
	 *     self.session = requests.Session()
	 *     self.session.headers.update({'User-Agent': 'Snowstorm-Lite/2.3.0'})
	 *     self.username = username
	 *     self.password = password
	 * ```
	 */
	public SyndicationClient(@Value("${syndication.url}") String url,
			@Value("${syndication.username}") String username,
			@Value("${syndication.password}") String password) throws JAXBException {

		// Configure HTTP client with syndication service base URL
		restTemplate = new RestTemplateBuilder()
				.rootUri(url)
				.messageConverters(new StringHttpMessageConverter())  // Handle XML/text responses
				.build();
		
		// Initialize XML processing context for Atom feed parsing
		jaxbContext = JAXBContext.newInstance(SyndicationFeed.class);
		
		// Store credentials for authentication (may be null for interactive input)
		this.username = username;
		this.password = password;
	}

	/**
	 * Fetches and parses the SNOMED CT syndication feed from MLDS.
	 * 
	 * The syndication feed is an Atom XML document that contains metadata about all
	 * available SNOMED CT packages. Each entry in the feed represents a specific
	 * package version with download links, dependencies, and categorization.
	 * 
	 * Processing steps:
	 * 1. Make HTTP GET request to /feed endpoint with Atom XML accept header
	 * 2. Strip Atom namespace from XML to simplify JAXB unmarshalling
	 * 3. Parse XML into SyndicationFeed object using JAXB
	 * 4. Sort entries by version in descending order (newest first)
	 * 
	 * XML namespace handling:
	 * The Atom namespace is stripped because it complicates JAXB binding without
	 * providing significant value for this use case. This is a pragmatic approach
	 * that simplifies the XML parsing while maintaining functionality.
	 * 
	 * Python implementation:
	 * ```python
	 * def get_feed(self) -> SyndicationFeed:
	 *     logger.info("Loading syndication feed")
	 *     
	 *     response = self.session.get(f"{self.base_url}/feed", 
	 *                               headers={'Accept': 'application/atom+xml'})
	 *     response.raise_for_status()
	 *     
	 *     # Parse XML response
	 *     xml_content = response.text
	 *     # Remove namespace for simpler parsing
	 *     xml_content = xml_content.replace('xmlns="http://www.w3.org/2005/Atom"', '')
	 *     
	 *     root = ET.fromstring(xml_content)
	 *     feed = self._parse_syndication_feed(root)
	 *     
	 *     # Sort entries by version (newest first)
	 *     feed.entries.sort(key=lambda entry: entry.content_item_version, reverse=True)
	 *     
	 *     return feed
	 * ```
	 * 
	 * @return Parsed syndication feed with sorted entries
	 * @throws IOException if network request fails or XML parsing fails
	 */
	public SyndicationFeed getFeed() throws IOException {
		logger.info("Loading syndication feed");
		
		// Set up HTTP headers for Atom XML feed request
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_ATOM_XML));
		
		// Fetch syndication feed from MLDS API
		ResponseEntity<String> response = restTemplate.exchange("/feed", HttpMethod.GET, new HttpEntity<>(headers), String.class);
		
		try {
			String xmlBody = response.getBody();
			
			// Strip Atom namespace to simplify JAXB unmarshalling
			// This is a pragmatic approach that avoids complex namespace handling
			xmlBody = xmlBody.replace("xmlns=\"http://www.w3.org/2005/Atom\"", "");
			
			// Parse XML into SyndicationFeed object using JAXB
			SyndicationFeed feed = (SyndicationFeed) jaxbContext.createUnmarshaller().unmarshal(new StringReader(xmlBody));
			
			// Sort entries by version in descending order (newest packages first)
			// This ensures that when searching for packages, the most recent versions are considered first
			List<SyndicationFeedEntry> sortedEntries = new ArrayList<>(feed.getEntries());
			sortedEntries.sort(Comparator.comparing(SyndicationFeedEntry::getContentItemVersion, Comparator.reverseOrder()));
			feed.setEntries(sortedEntries);
			
			return feed;
		} catch (JAXBException e) {
			throw new IOException("Failed to read XML feed.", e);
		}
	}
	/**
	 * Searches the syndication feed for a specific SNOMED CT package entry.
	 * 
	 * This method implements the logic for finding the correct package to download
	 * based on the requested version URI. It handles two types of URI matching:
	 * 1. Content Item Version: Full version URI (http://snomed.info/sct/module/version/date)
	 * 2. Content Item Identifier: Package identifier string
	 * 
	 * Package validation criteria:
	 * - Must have a valid download link (zipLink)
	 * - Must be one of the acceptable package types (SNAPSHOT, FULL, ALL)
	 * - Must match the requested version URI exactly
	 * 
	 * Search strategy:
	 * - Linear search through sorted entries (newest first)
	 * - First match wins (prioritizes newer packages if multiple matches)
	 * - Returns null if no matching package found
	 * 
	 * Python implementation:
	 * ```python
	 * def find_entry(self, load_version_uri: str, feed: SyndicationFeed) -> Optional[SyndicationFeedEntry]:
	 *     for entry in feed.entries:
	 *         zip_link = entry.get_zip_link()
	 *         category = entry.get_category()
	 *         
	 *         if (category and zip_link and 
	 *             category.term in self.ACCEPTABLE_PACKAGE_TYPES and
	 *             (entry.content_item_version == load_version_uri or 
	 *              entry.content_item_identifier == load_version_uri)):
	 *             
	 *             logger.info(f"Found entry to load {entry.content_item_version}")
	 *             return entry
	 *     
	 *     logger.warning(f"No matching syndication entry found for URI {load_version_uri}")
	 *     return None
	 * ```
	 * 
	 * @param loadVersionUri The SNOMED CT version URI or identifier to search for
	 * @param feed The parsed syndication feed to search within
	 * @return The matching feed entry, or null if not found
	 */
	public SyndicationFeedEntry findEntry(String loadVersionUri, SyndicationFeed feed) {
		// Linear search through sorted feed entries (newest packages first)
		for (SyndicationFeedEntry entry : feed.getEntries()) {
			SyndicationLink zipLink = entry.getZipLink();
			SyndicationCategory category = entry.getCategory();
			
			// Validate entry has required components
			if (category != null) {
				String categoryString = category.getTerm();
				
				// Check if entry matches all criteria:
				// 1. Has download link available
				// 2. Is acceptable package type (SNAPSHOT, FULL, ALL)
				// 3. Matches requested version URI (exact match on version or identifier)
				if (zipLink != null &&
						acceptablePackageTypes.contains(categoryString) &&
						(entry.getContentItemVersion().equals(loadVersionUri) || entry.getContentItemIdentifier().equals(loadVersionUri))) {

					logger.info("Found entry to load {}", entry.getContentItemVersion());
					return entry;
				}
			}
		}
		
		// No matching entry found - log warning and return null
		logger.warn("No matching syndication entry was found for URI {}", loadVersionUri);
		return null;
	}

	/**
	 * Downloads SNOMED CT packages and their dependencies from the syndication service.
	 * 
	 * This method implements a complete package download workflow including:
	 * - Recursive dependency resolution (International + Extensions)
	 * - Authenticated HTTP downloads with progress tracking
	 * - Temporary file management with automatic cleanup
	 * - Error handling and credential validation
	 * 
	 * Download process:
	 * 1. Gather all required packages (base package + dependencies) using recursive search
	 * 2. Display package list to user for confirmation
	 * 3. Validate credentials using OPTIONS request (preflight check)
	 * 4. Download each package to temporary file with progress display
	 * 5. Return set of temporary file paths for import processing
	 * 
	 * Dependency handling:
	 * - Extensions depend on specific International Edition versions
	 * - Dependencies are resolved recursively through the feed metadata
	 * - All required packages are downloaded in a single operation
	 * 
	 * File management:
	 * - Creates temporary files with random UUID names
	 * - Files are marked for deletion on JVM exit (cleanup)
	 * - Caller is responsible for deleting files after import
	 * 
	 * Python implementation:
	 * ```python
	 * def download_packages(self, entry: SyndicationFeedEntry, feed: SyndicationFeed, 
	 *                      credentials: Tuple[str, str]) -> Set[str]:
	 *     # Gather all packages including dependencies
	 *     package_urls = set()
	 *     self._gather_package_urls(entry.content_item_version, feed.entries, package_urls)
	 *     
	 *     if not package_urls:
	 *         logger.error("No download links found")
	 *         return None
	 *     
	 *     # Display packages to user
	 *     print("Matched the following packages:")
	 *     for entry, link in package_urls:
	 *         print(f" {entry.title}, {entry.content_item_version}")
	 *     
	 *     # Download each package
	 *     file_paths = set()
	 *     for entry, link in package_urls:
	 *         # Test credentials first
	 *         response = self.session.options(link.href, auth=credentials)
	 *         response.raise_for_status()
	 *         
	 *         # Download with progress
	 *         temp_file = self._download_with_progress(link, credentials)
	 *         file_paths.add(temp_file)
	 *     
	 *     return file_paths
	 * ```
	 * 
	 * @param entry The main package entry to download
	 * @param feed The complete syndication feed (for dependency resolution)
	 * @param creds Authentication credentials (username, password pair)
	 * @return Set of temporary file paths containing downloaded packages
	 * @throws IOException if download fails or file operations fail
	 * @throws ServiceException if HTTP errors occur during download
	 */
	public Set<String> downloadPackages(SyndicationFeedEntry entry, SyndicationFeed feed, Pair<String, String> creds) throws IOException, ServiceException {
		// Step 1: Gather all required packages including dependencies
		Set<Pair<SyndicationFeedEntry, SyndicationLink>> packageUrls = new LinkedHashSet<>();
		gatherPackageUrls(entry.getContentItemVersion(), feed.getEntries(), packageUrls);
		
		if (!packageUrls.isEmpty()) {
			Set<String> packageFilePaths = new HashSet<>();
			
			// Step 2: Display package list to user for transparency
			System.out.println("Matched the following packages:");
			for (Pair<SyndicationFeedEntry, SyndicationLink> packageEntry : packageUrls) {
				System.out.printf(" %s, %s%n", packageEntry.getFirst().getTitle(), packageEntry.getFirst().getContentItemVersion());
			}
			System.out.println();
			
			try {
				// Step 3: Download each package with authentication and progress tracking
				for (Pair<SyndicationFeedEntry, SyndicationLink> packageEntry : packageUrls) {
					SyndicationLink packageLink = packageEntry.getSecond();
					logger.info("Downloading package {} file {}", packageEntry.getFirst().getContentItemVersion(), packageLink.getHref());

					// Preflight check: Test credentials and download link using OPTIONS request
					// This validates authentication before attempting the full download
					HttpHeaders headers = new HttpHeaders();
					if (creds != null) {
						headers.setBasicAuth(creds.getFirst(), creds.getSecond());
					}
					restTemplate.exchange(packageLink.getHref(), HttpMethod.OPTIONS, new HttpEntity<Void>(headers), Void.class);

					// Create temporary file for download (UUID ensures uniqueness)
					File outputFile = Files.createTempFile(UUID.randomUUID().toString(), ".zip").toFile();
					
					// Execute authenticated download with progress tracking
					restTemplate.execute(packageLink.getHref(), HttpMethod.GET,
							request -> {
								// Set authentication headers for actual download
								if (creds != null) {
									request.getHeaders().setBasicAuth(creds.getFirst(), creds.getSecond());
								}
							},
							clientHttpResponse -> {
								try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
									// Parse expected file size for progress calculation
									String lengthString = packageLink.getLength();
									int length;
									if (lengthString == null || lengthString.isEmpty()) {
										length = 1024 * 500;  // Default 500KB if size unknown
									} else {
										length = Integer.parseInt(lengthString.replace(",", ""));  // Remove comma separators
									}
									
									try {
										// Copy stream with progress display (custom utility)
										StreamUtils.copyWithProgress(clientHttpResponse.getBody(), outputStream, length, "Download progress: %s%%");
									} catch (Exception e) {
										logger.error("Failed to download file from syndication service.", e);
									}
								}
								return outputFile;
							});
					
					// Mark file for automatic cleanup on JVM exit
					outputFile.deleteOnExit();
					packageFilePaths.add(outputFile.getAbsolutePath());
				}
			} catch (HttpClientErrorException e) {
				// Convert HTTP client errors to service exceptions with meaningful messages
				throw new ServiceException(format("Failed to download package due to HTTP error: %s", e.getStatusCode()), e);
			}
			return packageFilePaths;
		} else {
			logger.error("Can not load content, no links found within the syndication feed for the requested package.");
		}
		return null;
	}

	/**
	 * Obtains syndication credentials using configuration or interactive input.
	 * 
	 * This method implements a flexible credential management strategy that supports:
	 * 1. Configuration-based credentials (preferred for automated deployments)
	 * 2. Interactive console input (for manual/development use)
	 * 3. Fallback handling for non-console environments
	 * 
	 * Credential resolution priority:
	 * 1. Use configured username/password if both are provided
	 * 2. If System.console() available: Use secure console input (password masking)
	 * 3. If no console: Fall back to standard input (no password masking)
	 * 4. If credentials still blank: Return null with warning
	 * 
	 * Security considerations:
	 * - Uses System.console().readPassword() for secure password input when available
	 * - Console passwords are masked during input
	 * - Falls back gracefully in environments without console access
	 * 
	 * Python implementation:
	 * ```python
	 * def get_syndication_credentials(self) -> Optional[Tuple[str, str]]:
	 *     # Check configured credentials first
	 *     if self.username and self.password:
	 *         return (self.username, self.password)
	 *     
	 *     # Interactive credential input
	 *     try:
	 *         username = input("Syndication username: ")
	 *         password = getpass.getpass("Syndication password: ")
	 *     except (KeyboardInterrupt, EOFError):
	 *         logger.warning("Credential input cancelled")
	 *         return None
	 *     
	 *     if not username and not password:
	 *         logger.warning("Syndication credentials are blank")
	 *         return None
	 *     
	 *     return (username, password)
	 * ```
	 * 
	 * @return Pair of (username, password) or null if credentials unavailable
	 * @throws IOException if input stream operations fail
	 */
	public Pair<String, String> getSyndicationCredentials() throws IOException {
		// Option 1: Use configured credentials if both are provided
		if (!Strings.isBlank(username) && !Strings.isBlank(password)) {
			return Pair.of(username, password);
		}

		// Option 2: Interactive credential input
		Console console = System.console();
		String username;
		String password;
		
		if (console != null) {
			// Secure console input with password masking
			username = console.readLine("Syndication username:");
			password = new String(console.readPassword("Syndication password:"));
		} else {
			// Fallback for non-console environments (no password masking)
			BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
			System.out.println("Syndication username:");
			username = consoleReader.readLine();
			System.out.println("Syndication password:");
			password = consoleReader.readLine();
			System.out.println();
		}
		
		// Validate that credentials were provided
		if (Strings.isBlank(username) && Strings.isBlank(password)) {
			logger.warn("Syndication credentials are blank. If required use the properties: syndication.username and syndication.password.");
			return null;
		}
		
		return Pair.of(username, password);
	}

	/**
	 * Recursively gathers all packages required for download including dependencies.
	 * 
	 * This private method implements the dependency resolution algorithm for SNOMED CT packages.
	 * It handles two types of dependencies:
	 * 1. Edition dependencies: Base International Edition required by Extensions
	 * 2. Derivative dependencies: Additional packages required by complex Extensions
	 * 
	 * Recursive strategy:
	 * - Find packages matching the requested version URI
	 * - For each matching package, add it to download list
	 * - Recursively resolve and add any dependencies
	 * - Use LinkedHashSet to maintain order and prevent duplicates
	 * 
	 * Dependency types:
	 * - Edition Dependency: Single International Edition version (most common)
	 * - Derivative Dependencies: List of additional required packages (less common)
	 * 
	 * Algorithm prevents infinite recursion through the natural constraint that
	 * dependencies eventually resolve to packages without further dependencies.
	 * 
	 * Python implementation:
	 * ```python
	 * def _gather_package_urls(self, load_version_uri: str, entries: List[SyndicationFeedEntry], 
	 *                         download_list: Set[Tuple[SyndicationFeedEntry, SyndicationLink]]):
	 *     for entry in entries:
	 *         zip_link = entry.get_zip_link()
	 *         
	 *         if (zip_link and entry.category and
	 *             entry.category.term in self.ACCEPTABLE_PACKAGE_TYPES and
	 *             (entry.content_item_version == load_version_uri or 
	 *              entry.content_item_identifier == load_version_uri)):
	 *             
	 *             # Add this package to download list
	 *             download_list.add((entry, zip_link))
	 *             
	 *             # Recursively resolve dependencies
	 *             dependency = entry.get_package_dependency()
	 *             if dependency:
	 *                 if dependency.edition_dependency:
	 *                     self._gather_package_urls(dependency.edition_dependency, entries, download_list)
	 *                 
	 *                 if dependency.derivative_dependencies:
	 *                     for dep_uri in dependency.derivative_dependencies:
	 *                         self._gather_package_urls(dep_uri, entries, download_list)
	 * ```
	 * 
	 * @param loadVersionUri The version URI to search for
	 * @param sortedEntries All available feed entries (sorted by version)
	 * @param downloadList Accumulator set for packages to download (maintains order, prevents duplicates)
	 */
	private void gatherPackageUrls(String loadVersionUri, List<SyndicationFeedEntry> sortedEntries, Set<Pair<SyndicationFeedEntry, SyndicationLink>> downloadList) {
		for (SyndicationFeedEntry entry : sortedEntries) {
			SyndicationLink zipLink = entry.getZipLink();
			
			// Check if this entry matches the requested version and has a download link
			if (zipLink != null && entry.getCategory() != null &&
					acceptablePackageTypes.contains(entry.getCategory().getTerm()) &&
					(entry.getContentItemVersion().equals(loadVersionUri) || entry.getContentItemIdentifier().equals(loadVersionUri))) {

				// Add this package to the download list
				downloadList.add(Pair.of(entry, zipLink));

				// Recursively resolve and add dependencies
				SyndicationDependency packageDependency = entry.getPackageDependency();
				if (packageDependency != null) {
					// Handle edition dependency (typically International Edition for Extensions)
					if (packageDependency.getEditionDependency() != null) {
						gatherPackageUrls(packageDependency.getEditionDependency(), sortedEntries, downloadList);
					}
					
					// Handle derivative dependencies (additional required packages)
					if (packageDependency.getDerivativeDependency() != null) {
						for (String dependencyUri : packageDependency.getDerivativeDependency()) {
							gatherPackageUrls(dependencyUri, sortedEntries, downloadList);
						}
					}
				}
			}
		}
	}
}
