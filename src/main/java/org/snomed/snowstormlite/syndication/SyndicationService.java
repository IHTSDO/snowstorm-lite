package org.snomed.snowstormlite.syndication;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.snomedimport.ImportService;
import org.snomed.snowstormlite.service.ServiceException;
import org.snomed.snowstormlite.syndication.client.SyndicationClient;
import org.snomed.snowstormlite.syndication.client.SyndicationFeed;
import org.snomed.snowstormlite.syndication.client.SyndicationFeedEntry;
import org.snomed.snowstormlite.syndication.client.SyndicationLink;
import org.springframework.data.util.Pair;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SyndicationService {

	private static final long STANDARD_RF2_IMPORT_DURATION_MS = 5L * 60 * 1000;
	private static final long STANDARD_RF2_PACKAGE_BYTES = SyndicationClient.DEFAULT_RF2_PACKAGE_LENGTH_BYTES;
	private static final long MIN_RF2_IMPORT_DURATION_MS = 30_000L;

	private final SyndicationClient syndicationClient;
	private final Queue<InstallationTask> installationQueue;
	private final AtomicBoolean isProcessing;
	private final Map<String, InstallationTask> activeTasks;
	private final ImportService importService;
	private final ExecutorService executorService;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SyndicationService(SyndicationClient syndicationClient, ImportService importService) {
		this.syndicationClient = syndicationClient;
		this.importService = importService;
		this.executorService = Executors.newFixedThreadPool(1);
		this.installationQueue = new ConcurrentLinkedQueue<>();
		this.isProcessing = new AtomicBoolean(false);
		this.activeTasks = new ConcurrentHashMap<>();
	}

	public List<SyndicationSnomedEdition> getSnomedEditions() throws IOException {
		SyndicationFeed feed = syndicationClient.getFeed();
		Map<String, List<SyndicationFeedEntry>> entryGroups = new HashMap<>();
		feed.getEntries().stream()
				.filter(entry -> entry.getContentItemIdentifier() != null)
				.filter(entry -> entry.getContentItemIdentifier().startsWith("http://snomed.info/sct/"))
				.forEach(entry -> entryGroups.computeIfAbsent(entry.getContentItemIdentifier(), i -> new ArrayList<>()).add(entry));
		List<SyndicationSnomedEdition> snomedEditions = new ArrayList<>();
		SyndicationSnomedEdition internationalEdition = null;
		for (Map.Entry<String, List<SyndicationFeedEntry>> mapEntry : entryGroups.entrySet()) {
			SyndicationSnomedEdition edition = new SyndicationSnomedEdition(mapEntry.getKey());
			List<SyndicationFeedEntry> feedEntries = mapEntry.getValue();
			String titleCleaned = feedEntries.get(0).getTitleCleaned();
			edition.setTitle(titleCleaned);
			String versionUriPrefix = mapEntry.getKey() + "/version/";
			edition.setVersionsAvailable(feedEntries.stream().map(entry -> entry.getContentItemVersion().replace(versionUriPrefix, "")).toList());
			if ("SNOMED CT International Edition".equals(titleCleaned)) {
				internationalEdition = edition;
			} else {
				snomedEditions.add(edition);
			}
		}
		snomedEditions.sort(Comparator.comparing(SyndicationSnomedEdition::getTitle));
		if (internationalEdition != null) {
			snomedEditions.add(0, internationalEdition);
		}
		return snomedEditions;
	}

	public List<SyndicationDerivativeOption> listRefsetDerivatives(String editionId, String editionSelectedVersion) throws IOException {
		if (editionId == null || editionId.isBlank() || editionSelectedVersion == null || !editionSelectedVersion.matches("\\d{8}")) {
			throw new IllegalArgumentException("editionId and an 8-digit yyyyMMdd version are required");
		}
		int editionDate = Integer.parseInt(editionSelectedVersion);
		SyndicationFeed feed = syndicationClient.getFeed();
		List<SyndicationDerivativeOption> options = new ArrayList<>();
		for (SyndicationFeedEntry entry : feed.getEntries()) {
			if (entry.getTitle() == null || !entry.getTitle().toLowerCase(Locale.ROOT).contains("refset")) {
				continue;
			}
			if (entry.getCategory() == null || !SyndicationClient.acceptablePackageTypes.contains(entry.getCategory().getTerm())) {
				continue;
			}
			if (entry.getZipLink() == null) {
				continue;
			}
			Optional<Integer> derivativeDate = versionDateFromContentItemVersion(entry.getContentItemVersion());
			if (derivativeDate.isEmpty() || derivativeDate.get() > editionDate) {
				continue;
			}
			options.add(new SyndicationDerivativeOption(
					identifierForRefsetEntry(entry),
					entry.getContentItemVersion(),
					displayTitle(entry),
					derivativeDate.get()));
		}
		options.sort(Comparator.comparing(SyndicationDerivativeOption::getContentItemIdentifier, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(SyndicationDerivativeOption::getVersionDate, Comparator.reverseOrder()));
		return options;
	}

	private static String identifierForRefsetEntry(SyndicationFeedEntry entry) {
		String id = entry.getContentItemIdentifier();
		if (id != null && !id.isBlank()) {
			return id;
		}
		String versionUri = entry.getContentItemVersion();
		if (versionUri == null) {
			return "";
		}
		String marker = "/version/";
		int idx = versionUri.lastIndexOf(marker);
		if (idx <= 0) {
			return versionUri;
		}
		return versionUri.substring(0, idx);
	}

	private static String displayTitle(SyndicationFeedEntry entry) {
		String title = entry.getTitle();
		int dash = title.indexOf('-');
		if (dash > 0) {
			return title.substring(0, dash).trim();
		}
		return title.trim();
	}

	static Optional<Integer> versionDateFromContentItemVersion(String contentItemVersion) {
		if (contentItemVersion == null) {
			return Optional.empty();
		}
		String marker = "/version/";
		int idx = contentItemVersion.lastIndexOf(marker);
		if (idx < 0) {
			return Optional.empty();
		}
		String suffix = contentItemVersion.substring(idx + marker.length());
		if (suffix.length() != 8 || !suffix.chars().allMatch(Character::isDigit)) {
			return Optional.empty();
		}
		return Optional.of(Integer.parseInt(suffix));
	}

	public String getFeedUrl() {
		return syndicationClient.getBaseUrl();
	}

	public String getDefaultFeedUrl() {
		return syndicationClient.getDefaultUrl();
	}

	public void setFeedUrl(String url) {
		syndicationClient.setBaseUrl(url);
	}

	public String getFeedUsername() {
		return syndicationClient.getUsername();
	}

	public boolean isFeedPasswordSet() {
		return syndicationClient.isPasswordSet();
	}

	public void setFeedCredentials(String username, String password) {
		syndicationClient.setCredentials(username, password);
	}

	public String installEdition(String editionId, String version, List<String> derivativeContentItemVersions) {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		InstallationTask task = new InstallationTask(editionId, version, derivativeContentItemVersions, securityContext);
		installationQueue.offer(task);
		activeTasks.put(task.getTaskId(), task);
		logger.info("Created installation task {} for edition {} version {}", task.getTaskId(), editionId, version);

		if (isProcessing.compareAndSet(false, true)) {
			executorService.submit(() -> {
				try {
					processInstallationTasks();
				} finally {
					isProcessing.set(false);
				}
			});
		}

		return task.getTaskId();
	}

	public InstallationTask getInstallationTask(String taskId) {
		return activeTasks.get(taskId);
	}

	public List<InstallationTask> getActiveInstallationTasks() {
		return activeTasks.values().stream()
				.filter(t -> t.getStatus() == InstallationTask.InstallationStatus.PENDING
						|| t.getStatus() == InstallationTask.InstallationStatus.IN_PROGRESS)
				.sorted(Comparator.comparing(InstallationTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
	}

	private void processInstallationTasks() {
		while (!installationQueue.isEmpty()) {
			InstallationTask task = installationQueue.poll();
			if (task != null) {
				processTask(task);
			}
		}
	}

	private void processTask(InstallationTask task) {
		logger.info("Processing installation task {} for edition {} version {}", task.getTaskId(), task.getEditionId(), task.getVersion());
		task.setStatus(InstallationTask.InstallationStatus.IN_PROGRESS);

		SecurityContext originalContext = SecurityContextHolder.getContext();
		try {
			SecurityContextHolder.setContext(task.getSecurityContext());

			try {
				SyndicationFeed feed = syndicationClient.getFeed();

				String versionUri = task.getEditionId() + "/version/" + task.getVersion();

				SyndicationFeedEntry entry = syndicationClient.findEntry(versionUri, feed);
				if (entry == null) {
					throw new ServiceException("No matching syndication entry found for URI: " + versionUri);
				}

				logger.info("Installation task {}: syndication entry matched {} ({}) — resolving credentials, then building package list.",
						task.getTaskId(), entry.getContentItemVersion(), entry.getTitleCleaned());

				Pair<String, String> creds = syndicationClient.getSyndicationCredentials();
				logger.info("Installation task {}: credentials step finished (HTTP Basic for package downloads: {}).",
						task.getTaskId(), creds != null);

				int editionEffectiveDate = Integer.parseInt(task.getVersion());
				validateDerivativeSelections(feed, task.getDerivativeContentItemVersions(), editionEffectiveDate);
				logger.info("Installation task {}: derivative selections OK ({} optional refset package(s)).",
						task.getTaskId(), task.getDerivativeContentItemVersions().size());

				Set<String> consumedVersionUris = new HashSet<>();
				List<InstallationPackageProgress> packageSlots = new ArrayList<>();
				List<Pair<SyndicationFeedEntry, SyndicationLink>> allOrdered = new ArrayList<>();

				List<Pair<SyndicationFeedEntry, SyndicationLink>> mainOrdered = syndicationClient.collectOrderedPackages(entry, feed, consumedVersionUris);
				for (Pair<SyndicationFeedEntry, SyndicationLink> pair : mainOrdered) {
					packageSlots.add(progressSlot(pair.getFirst(), pair.getSecond()));
					allOrdered.add(pair);
				}
				for (String derivativeUri : task.getDerivativeContentItemVersions()) {
					SyndicationFeedEntry derivativeEntry = syndicationClient.findEntry(derivativeUri, feed);
					if (derivativeEntry == null) {
						throw new ServiceException("No matching syndication entry found for derivative URI: " + derivativeUri);
					}
					List<Pair<SyndicationFeedEntry, SyndicationLink>> derivOrdered = syndicationClient.collectOrderedPackages(derivativeEntry, feed, consumedVersionUris, false);
					for (Pair<SyndicationFeedEntry, SyndicationLink> pair : derivOrdered) {
						packageSlots.add(progressSlot(pair.getFirst(), pair.getSecond()));
						allOrdered.add(pair);
					}
				}
				logger.info("Installation task {}: {} package(s) queued for download/import (edition + dependencies + selected refsets).",
						task.getTaskId(), allOrdered.size());
				task.replacePackageProgress(packageSlots);
				List<String> orderedFiles = new ArrayList<>(syndicationClient.downloadOrderedPackageList(allOrdered, creds, packageSlots));
				task.getDownloadedFiles().addAll(orderedFiles);

				runCombinedImport(task, packageSlots, orderedFiles, versionUri, entry.getTitleCleaned());

				task.startVersioningPhase();
				task.setStatus(InstallationTask.InstallationStatus.COMPLETED);
				task.setCompletedAt(new Date());
				logger.info("Completed installation task {} for edition {} version {}", task.getTaskId(), task.getEditionId(), task.getVersion());

			} catch (Throwable e) {
				// Catch Throwable, not just Exception: the install runs via ExecutorService.submit(), which swallows any
				// uncaught Throwable into an unread Future — an Error (e.g. a NoClassDefFoundError or an IDE-compiler
				// "Unresolved compilation problem") would otherwise leave the task stuck IN_PROGRESS with no log.
				logger.error("Failed installation task {} for edition {} version {}", task.getTaskId(), task.getEditionId(), task.getVersion(), e);
				task.setStatus(InstallationTask.InstallationStatus.FAILED);
				task.setErrorMessage(SyndicationInstallUserMessage.describe(e));
				task.setCompletedAt(new Date());
			}
		} finally {
			SecurityContextHolder.setContext(originalContext);
		}
	}

	private void runCombinedImport(InstallationTask task, List<InstallationPackageProgress> packageSlots, List<String> orderedFiles, String versionUri,
			String syndicationEditionTitle)
			throws IOException, ReleaseImportException {
		long totalEstimateMs = 0;
		for (InstallationPackageProgress pkg : packageSlots) {
			totalEstimateMs += estimatedImportMillis(pkg.getDeclaredSizeBytes());
		}
		totalEstimateMs = Math.max(MIN_RF2_IMPORT_DURATION_MS, totalEstimateMs);
		long importStartedAtMillis = System.currentTimeMillis();
		for (InstallationPackageProgress pkg : packageSlots) {
			pkg.beginImportEstimate(totalEstimateMs, importStartedAtMillis);
		}
		try {
			importService.importRelease(new LinkedHashSet<>(orderedFiles), versionUri, syndicationEditionTitle);
		} finally {
			for (InstallationPackageProgress pkg : packageSlots) {
				pkg.markImportComplete();
			}
			for (String filePath : orderedFiles) {
				File file = new File(filePath);
				if (file.exists() && !file.delete()) {
					logger.info("Failed to delete temp file {}", filePath);
				}
			}
		}
	}

	private static long estimatedImportMillis(long declaredSizeBytes) {
		long size = declaredSizeBytes > 0 ? declaredSizeBytes : STANDARD_RF2_PACKAGE_BYTES;
		long scaled = (long) (STANDARD_RF2_IMPORT_DURATION_MS * (size / (double) STANDARD_RF2_PACKAGE_BYTES));
		return Math.max(MIN_RF2_IMPORT_DURATION_MS, scaled);
	}

	private InstallationPackageProgress progressSlot(SyndicationFeedEntry entry, SyndicationLink link) {
		long bytes = SyndicationClient.parseDeclaredPackageBytes(link.getLength());
		String title = progressSlotTitle(entry);
		String ver = entry.getContentItemVersion() != null ? entry.getContentItemVersion() : "";
		return new InstallationPackageProgress(ver, title, bytes);
	}

	private static String progressSlotTitle(SyndicationFeedEntry entry) {
		try {
			return entry.getTitleCleaned();
		} catch (Exception e) {
			return entry.getTitle() != null ? entry.getTitle() : "";
		}
	}

	private void validateDerivativeSelections(SyndicationFeed feed, List<String> derivativeUris, int editionEffectiveDate)
			throws ServiceException {
		if (derivativeUris == null || derivativeUris.isEmpty()) {
			return;
		}
		for (String uri : derivativeUris) {
			SyndicationFeedEntry matching = getSyndicationFeedEntry(feed, uri);
			if (matching.getCategory() == null || !SyndicationClient.acceptablePackageTypes.contains(matching.getCategory().getTerm())) {
				throw new ServiceException("Unacceptable package type for derivative: " + uri);
			}
			Optional<Integer> derivativeDate = versionDateFromContentItemVersion(uri);
			if (derivativeDate.isEmpty() || derivativeDate.get() > editionEffectiveDate) {
				throw new ServiceException("Derivative version date is after the selected edition: " + uri);
			}
		}
	}

	private static SyndicationFeedEntry getSyndicationFeedEntry(SyndicationFeed feed, String uri) throws ServiceException {
		SyndicationFeedEntry matching = null;
		for (SyndicationFeedEntry candidate : feed.getEntries()) {
			if (uri.equals(candidate.getContentItemVersion())) {
				matching = candidate;
				break;
			}
		}
		if (matching == null) {
			throw new ServiceException("Derivative not found in syndication feed: " + uri);
		}
		if (matching.getTitle() == null || !matching.getTitle().toLowerCase(Locale.ROOT).contains("refset")) {
			throw new ServiceException("Not a refset derivative package: " + uri);
		}
		return matching;
	}
}
