package org.snomed.snowstormlite.syndication;

import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.service.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

import static java.lang.String.format;

@Service
public class SyndicationClient {

	private final RestTemplate restTemplate;
	private final JAXBContext jaxbContext;
	private final String username;
	private final String password;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SyndicationClient(@Value("${syndication.url}") String url,
			@Value("${syndication.username}") String username,
			@Value("${syndication.password}") String password) throws JAXBException {

		restTemplate = new RestTemplateBuilder()
				.rootUri(url)
				.messageConverters(new StringHttpMessageConverter())
				.build();
		jaxbContext = JAXBContext.newInstance(SyndicationFeed.class);
		this.username = username;
		this.password = password;
	}

	public SyndicationFeed getFeed() throws IOException {
		logger.info("Loading syndication feed");
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_ATOM_XML));
		ResponseEntity<String> response = restTemplate.exchange("/", HttpMethod.GET, new HttpEntity<>(headers), String.class);
		try {
			String xmlBody = response.getBody();
			// Strip Atom namespace to simplify unmarshalling
			xmlBody = xmlBody.replace("xmlns=\"http://www.w3.org/2005/Atom\"", "");
			SyndicationFeed feed = (SyndicationFeed) jaxbContext.createUnmarshaller().unmarshal(new StringReader(xmlBody));
			List<SyndicationFeedEntry> sortedEntries = new ArrayList<>(feed.getEntries());
			sortedEntries.sort(Comparator.comparing(SyndicationFeedEntry::getContentItemVersion, Comparator.reverseOrder()));
			feed.setEntries(sortedEntries);
			return feed;
		} catch (JAXBException e) {
			throw new IOException("Failed to read XML feed.", e);
		}
	}

	public SyndicationFeedEntry findEntry(String loadVersionUri, SyndicationFeed feed) {
		for (SyndicationFeedEntry entry : feed.getEntries()) {
			String zipUrl = entry.getZipUrl();
			if (zipUrl != null && entry.getCategory() != null && "SCT_RF2_SNAPSHOT".equals(entry.getCategory().getTerm()) &&
					entry.getContentItemVersion().equals(loadVersionUri) || entry.getContentItemIdentifier().equals(loadVersionUri)) {

				logger.info("Found entry to load {}", entry.getContentItemVersion());
				return entry;
			}
		}
		logger.warn("No matching package was found for URI {}", loadVersionUri);
		return null;
	}

	public Set<String> downloadPackages(SyndicationFeedEntry entry, SyndicationFeed feed, Pair<String, String> creds) throws IOException, ServiceException {
		Set<Pair<SyndicationFeedEntry, String>> packageUrls = new LinkedHashSet<>();
		gatherPackageUrls(entry.getContentItemVersion(), feed.getEntries(), packageUrls);
		if (!packageUrls.isEmpty()) {
			Set<String> packageFilePaths = new HashSet<>();
			System.out.println("Matched the following packages:");
			for (Pair<SyndicationFeedEntry, String> packageEntry : packageUrls) {
				System.out.printf(" %s, %s%n", packageEntry.getFirst().getTitle(), packageEntry.getFirst().getContentItemVersion());
			}
			System.out.println();
			try {
				for (Pair<SyndicationFeedEntry, String> packageEntry : packageUrls) {
					String packageUrl = packageEntry.getSecond();
					System.out.println("Downloading " + packageUrl);
					File outputFile = Files.createTempFile(UUID.randomUUID().toString(), ".zip").toFile();
					restTemplate.execute(packageUrl, HttpMethod.GET,
							request -> {
								if (creds != null) {
									request.getHeaders().setBasicAuth(creds.getFirst(), creds.getSecond());
								}
							},
							clientHttpResponse -> {
								try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
									StreamUtils.copy(clientHttpResponse.getBody(), outputStream);
								}
								return outputFile;
							});
					outputFile.deleteOnExit();
					packageFilePaths.add(outputFile.getAbsolutePath());
				}
			} catch (HttpClientErrorException e) {
				throw new ServiceException(format("Failed to download package due to HTTP error: %s", e.getStatusCode()), e);
			}
			return packageFilePaths;
		}
		return null;
	}

	public Pair<String, String> getSyndicationCredentials() throws IOException {
		if (!Strings.isBlank(username) && !Strings.isBlank(password)) {
			return Pair.of(username, password);
		}

		Console console = System.console();
		String username;
		String password;
		if (console != null) {
			username = console.readLine("Syndication username:");
			password = new String(console.readPassword("Syndication password:"));
		} else {
			BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
			System.out.println("Syndication username:");
			username = consoleReader.readLine();
			System.out.println("Syndication password:");
			password = consoleReader.readLine();
			System.out.println();
		}
		if (Strings.isBlank(username) && Strings.isBlank(password)) {
			logger.warn("Syndication credentials are blank. If required use the properties: syndication.username and syndication.password.");
			return null;
		}
		return Pair.of(username, password);
	}

	private void gatherPackageUrls(String loadVersionUri, List<SyndicationFeedEntry> sortedEntries, Set<Pair<SyndicationFeedEntry, String>> downloadList) {
		for (SyndicationFeedEntry entry : sortedEntries) {
			String zipUrl = entry.getZipUrl();
			if (zipUrl != null && entry.getCategory() != null && "SCT_RF2_SNAPSHOT".equals(entry.getCategory().getTerm()) &&
					entry.getContentItemVersion().equals(loadVersionUri) || entry.getContentItemIdentifier().equals(loadVersionUri)) {

				logger.info("Found entry to load {}", entry.getContentItemVersion());
				downloadList.add(Pair.of(entry, zipUrl));

				SyndicationDependency packageDependency = entry.getPackageDependency();
				if (packageDependency != null) {
					if (packageDependency.getEditionDependency() != null) {
						gatherPackageUrls(packageDependency.getEditionDependency(), sortedEntries, downloadList);
					}
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
