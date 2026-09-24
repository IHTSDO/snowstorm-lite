package org.snomed.snowstormlite.service;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.grouping.GroupingSearch;
import org.apache.lucene.search.grouping.TopGroups;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRDescription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Accessory Lucene index with one document per active description, used to rank filtered ValueSet $expand results by
 * the shortest matching description term, exactly and without a relevance sort window.
 * <p>
 * The index is derived from the main index (concept documents already store their descriptions), so it can be rebuilt
 * at any time without importing SNOMED CT again. It records the CodeSystem version it was built from; when that does
 * not match the loaded CodeSystem, or the index is missing, callers fall back to the window-based ranking.
 * It lives in a subdirectory of the main index directory, which Lucene ignores, so it shares the same storage volume.
 */
@Service
public class DescriptionSortIndex {

	public static final String DIRECTORY_NAME = "description-sort";

	private static final String CONCEPT_ID = "cid";
	private static final String ACTIVE = "active";
	private static final String ACTIVE_SORT = "active_sort";
	private static final String TERM_LENGTH = "term_len";
	private static final String TERM_SORT = "term_sort";
	private static final String VERSION_KEY = "codeSystemVersion";

	private static final Sort RANKING = new Sort(
			new SortField(ACTIVE_SORT, SortField.Type.INT, true),
			new SortField(TERM_LENGTH, SortField.Type.INT),
			new SortField(TERM_SORT, SortField.Type.STRING),
			new SortField(CONCEPT_ID, SortField.Type.STRING));

	private final CodeSystemRepository codeSystemRepository;
	private final boolean enabled;
	private final Path indexPath;
	private final Path mainIndexPath;
	// Static: tests can create several Spring contexts in one JVM, all pointing at the same directory
	private static final Object buildLock = new Object();
	private static final long REOPEN_CHECK_INTERVAL_MILLIS = 5_000;
	private volatile OpenIndex openIndex;
	private volatile long lastReopenCheck;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public record Page(List<String> conceptIds, int total) {
	}

	private record OpenIndex(FSDirectory directory, SearcherManager searcherManager, String version) {
	}

	public DescriptionSortIndex(CodeSystemRepository codeSystemRepository,
			@Value("${index.path}") String mainIndexPath,
			@Value("${search.description-sort-index.enabled:true}") boolean enabled) {
		this.codeSystemRepository = codeSystemRepository;
		this.enabled = enabled;
		this.mainIndexPath = Paths.get(mainIndexPath);
		this.indexPath = Paths.get(mainIndexPath, DIRECTORY_NAME);
	}

	/** True when the index is enabled and was built from the currently loaded CodeSystem. */
	public boolean isUsable() {
		if (!enabled) {
			return false;
		}
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		if (codeSystem == null) {
			return false;
		}
		String version = versionKey(codeSystem);
		OpenIndex index = getOpenIndex();
		if (index != null && version.equals(index.version())) {
			return true;
		}
		// Another instance may have rebuilt the directory since this one was opened
		long now = System.currentTimeMillis();
		if (now - lastReopenCheck > REOPEN_CHECK_INTERVAL_MILLIS) {
			lastReopenCheck = now;
			synchronized (buildLock) {
				try {
					closeOpenIndex();
				} catch (IOException e) {
					logger.debug("Failed to close description sort index before reopening.", e);
				}
			}
			index = getOpenIndex();
			return index != null && version.equals(index.version());
		}
		return false;
	}

	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Ranks the concepts that have a description matching the term query: active concepts first, then by the shortest
	 * matching description term, then alphabetically. One result per concept.
	 *
	 * @param termQuery          query over the {@code term.<lang>} fields, as built for the main index
	 * @param conceptIds         restrict to these concepts, or null for no restriction
	 * @param activeConceptsOnly restrict to active concepts
	 */
	public Page search(Query termQuery, Set<String> conceptIds, boolean activeConceptsOnly, int offset, int count) throws IOException {
		return search(termQuery, conceptIds, activeConceptsOnly, offset, count, true);
	}

	/** As {@link #search(Query, Set, boolean, int, int)}; without the total (0) when {@code includeTotal} is false, which is cheaper. */
	public Page search(Query termQuery, Set<String> conceptIds, boolean activeConceptsOnly, int offset, int count,
			boolean includeTotal) throws IOException {
		if (conceptIds != null && conceptIds.isEmpty()) {
			return new Page(List.of(), 0);
		}
		OpenIndex index = getOpenIndex();
		if (index == null) {
			throw new IllegalStateException("Description sort index is not available.");
		}
		BooleanQuery.Builder query = new BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.MUST);
		if (activeConceptsOnly) {
			query.add(new TermQuery(new Term(ACTIVE, "1")), BooleanClause.Occur.FILTER);
		}
		if (conceptIds != null) {
			query.add(new TermInSetQuery(CONCEPT_ID, conceptIds.stream().map(BytesRef::new).toList()), BooleanClause.Occur.FILTER);
		}

		GroupingSearch groupingSearch = new GroupingSearch(CONCEPT_ID);
		groupingSearch.setGroupSort(RANKING);
		groupingSearch.setSortWithinGroup(RANKING);
		groupingSearch.setGroupDocsLimit(1);
		groupingSearch.setAllGroups(includeTotal);

		IndexSearcher searcher;
		try {
			searcher = index.searcherManager().acquire();
		} catch (AlreadyClosedException e) {
			// Replaced by a rebuild after this search started: use the new index
			index = getOpenIndex();
			if (index == null) {
				throw new IllegalStateException("Description sort index is not available.", e);
			}
			searcher = index.searcherManager().acquire();
		}
		try {
			TopGroups<BytesRef> topGroups = groupingSearch.search(searcher, query.build(), offset, Math.max(count, 1));
			List<String> ids = new ArrayList<>();
			if (topGroups != null && count > 0) {
				for (var group : topGroups.groups) {
					ids.add(group.groupValue.utf8ToString());
				}
			}
			int total = topGroups != null && topGroups.totalGroupCount != null ? topGroups.totalGroupCount : 0;
			return new Page(ids, total);
		} finally {
			index.searcherManager().release(searcher);
		}
	}

	/** Rebuilds the index from the main index. Searches keep using the previous index until the new one is complete. */
	public void rebuild() throws IOException {
		if (!enabled) {
			return;
		}
		synchronized (buildLock) {
			FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
			if (codeSystem == null) {
				delete();
				return;
			}
			long start = System.currentTimeMillis();
			deleteLeftoverBuildDirectories();
			Path buildPath = mainIndexPath.resolve(DIRECTORY_NAME + "-building-" + start);
			long[] descriptionCount = {0};
			try (FSDirectory buildDirectory = FSDirectory.open(buildPath);
				 IndexWriter writer = new IndexWriter(buildDirectory, new IndexWriterConfig(new StandardAnalyzer())
						 .setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {
				List<Document> batch = new ArrayList<>();
				codeSystemRepository.forEachConceptDescriptions((conceptId, active, descriptions) -> {
					for (FHIRDescription description : descriptions) {
						batch.add(descriptionDoc(conceptId, active, description));
						descriptionCount[0]++;
					}
					if (batch.size() >= 10_000) {
						writer.addDocuments(batch);
						batch.clear();
					}
				});
				writer.addDocuments(batch);
				writer.setLiveCommitData(Map.of(VERSION_KEY, versionKey(codeSystem)).entrySet());
				writer.forceMerge(1);
				writer.commit();
			}
			swapIn(buildPath);
			logger.info("Description sort index built with {} descriptions in {} seconds.", descriptionCount[0],
					(System.currentTimeMillis() - start) / 1000);
		}
	}

	/** Builds the index in the background if it is enabled, SNOMED CT is loaded and the index is missing or out of date. */
	public void rebuildInBackgroundIfNeeded() {
		if (!enabled || codeSystemRepository.getCodeSystem() == null || isUsable()) {
			return;
		}
		Thread thread = new Thread(() -> {
			try {
				logger.info("Building description sort index in the background. Filtered searches use the previous ranking until it is ready.");
				rebuild();
			} catch (IOException | RuntimeException e) {
				logger.error("Failed to build description sort index.", e);
			}
		}, "description-sort-index-build");
		thread.setDaemon(true);
		thread.start();
	}

	public void delete() throws IOException {
		synchronized (buildLock) {
			closeOpenIndex();
			deleteDirectory(indexPath);
		}
	}

	private Document descriptionDoc(String conceptId, boolean active, FHIRDescription description) {
		String lang = description.getLang();
		String term = description.getTerm();
		Document doc = new Document();
		doc.add(new StringField(CONCEPT_ID, conceptId, Field.Store.NO));
		doc.add(new SortedDocValuesField(CONCEPT_ID, new BytesRef(conceptId)));
		doc.add(new StringField(ACTIVE, active ? "1" : "0", Field.Store.NO));
		doc.add(new NumericDocValuesField(ACTIVE_SORT, active ? 1 : 0));
		doc.add(new NumericDocValuesField(TERM_LENGTH, term.length()));
		doc.add(new SortedDocValuesField(TERM_SORT, new BytesRef(term)));
		doc.add(new TextField(CodeSystemRepository.getTermField(lang), codeSystemRepository.foldTermForIndex(term, lang), Field.Store.NO));
		return doc;
	}

	private void swapIn(Path buildPath) throws IOException {
		closeOpenIndex();
		deleteDirectory(indexPath);
		Files.move(buildPath, indexPath, StandardCopyOption.ATOMIC_MOVE);
		openIndex = open();
	}

	private OpenIndex getOpenIndex() {
		OpenIndex index = openIndex;
		if (index == null && Files.isDirectory(indexPath)) {
			synchronized (buildLock) {
				if (openIndex == null) {
					try {
						openIndex = open();
					} catch (IOException e) {
						logger.warn("Failed to open description sort index, it will be rebuilt.", e);
					}
				}
				index = openIndex;
			}
		}
		return index;
	}

	private OpenIndex open() throws IOException {
		FSDirectory directory = FSDirectory.open(indexPath);
		if (!DirectoryReader.indexExists(directory)) {
			directory.close();
			return null;
		}
		SearcherManager searcherManager = new SearcherManager(directory, null);
		String version;
		IndexSearcher searcher = searcherManager.acquire();
		try {
			version = ((DirectoryReader) searcher.getIndexReader()).getIndexCommit().getUserData().get(VERSION_KEY);
		} finally {
			searcherManager.release(searcher);
		}
		return new OpenIndex(directory, searcherManager, version);
	}

	private void closeOpenIndex() throws IOException {
		OpenIndex index = openIndex;
		openIndex = null;
		if (index != null) {
			// Searches in flight keep their acquired searcher until they release it
			index.searcherManager().close();
			index.directory().close();
		}
	}

	/** Removes temporary build directories left by an interrupted build. */
	private void deleteLeftoverBuildDirectories() throws IOException {
		if (!Files.isDirectory(mainIndexPath)) {
			return;
		}
		try (DirectoryStream<Path> leftovers = Files.newDirectoryStream(mainIndexPath, DIRECTORY_NAME + "-building-*")) {
			for (Path leftover : leftovers) {
				deleteDirectory(leftover);
			}
		}
	}

	private static String versionKey(FHIRCodeSystem codeSystem) {
		Date lastUpdated = codeSystem.getLastUpdated();
		return codeSystem.getVersionUri() + "|" + (lastUpdated != null ? lastUpdated.getTime() : "");
	}

	private static void deleteDirectory(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(path)) {
			for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(p);
			}
		}
	}
}
