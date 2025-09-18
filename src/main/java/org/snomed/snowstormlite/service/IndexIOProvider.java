package org.snomed.snowstormlite.service;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.FSDirectory;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.fhir.FHIRHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * Lucene Index I/O Provider - manages read and write operations for the SNOMED CT terminology index.
 * 
 * This service provides a centralized interface for Lucene index operations with the following key features:
 * - Thread-safe write operations using synchronized blocks
 * - Automatic IndexSearcher refresh after write operations
 * - Lazy initialization of IndexSearcher for read operations
 * - Standard analyzer configuration for text processing
 * 
 * Architecture patterns:
 * - Singleton IndexSearcher per application instance (read operations)
 * - Short-lived IndexWriter instances (write operations) 
 * - FSDirectory for file-based persistent storage
 * - Synchronized access to prevent read/write conflicts
 * 
 * Python implementation considerations:
 * - Use Whoosh or PyLucene as Lucene equivalent
 * - Implement thread synchronization with threading.Lock()
 * - Use context managers for IndexWriter lifecycle management
 * - Consider asyncio for non-blocking I/O operations
 */
@Service
public class IndexIOProvider {

	private final FSDirectory indexDirectory;
	private IndexSearcher indexSearcher;
	private final Object writeLock;  // Synchronization lock for write operations

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * Constructor initializes the Lucene index directory and prepares for I/O operations.
	 * 
	 * Key initialization steps:
	 * 1. Create index directory if it doesn't exist
	 * 2. Initialize FSDirectory pointing to the index location
	 * 3. Set up write synchronization lock
	 * 
	 * Note: IndexSearcher is NOT initialized here - it's created lazily when first read operation occurs
	 * This allows the application to start even without existing index data
	 * 
	 * Python implementation:
	 * - Use pathlib.Path for directory operations
	 * - Initialize index writer/reader configuration
	 * - Set up threading locks for synchronization
	 */
	public IndexIOProvider(@Value("${index.path}") String indexPath) throws IOException {
		writeLock = new Object();
		
		// Ensure index directory exists - create if necessary
		File indexDirFile = new File(indexPath);
		if (!indexDirFile.exists()) {
			if (!indexDirFile.mkdirs()) {
				logger.error("Failed to create index directory '{}'", indexDirFile.getAbsoluteFile());
			}
		}
		
		// Initialize Lucene FSDirectory for persistent file-based storage
		indexDirectory = FSDirectory.open(indexDirFile.toPath());
	}

	/**
	 * Convenience method for writing a single document to the index.
	 * Delegates to writeDocuments() with a singleton collection for consistency.
	 * 
	 * Python implementation: Use list comprehension or single-item list
	 */
	public void writeDocument(Document document) throws IOException {
		writeDocuments(Collections.singleton(document));
	}

	/**
	 * Writes multiple documents to the Lucene index in a single atomic operation.
	 * 
	 * Key operational aspects:
	 * 1. Synchronized access prevents concurrent write operations
	 * 2. Uses try-with-resources for automatic IndexWriter cleanup
	 * 3. CREATE_OR_APPEND mode allows index updates without full rebuild
	 * 4. StandardAnalyzer provides text tokenization and processing
	 * 5. Automatically refreshes IndexSearcher after write to ensure read consistency
	 * 
	 * Performance considerations:
	 * - Batch multiple documents in single transaction for efficiency
	 * - IndexWriter lifecycle is short-lived to minimize resource usage
	 * - Synchronization ensures thread safety but may create bottlenecks under high write load
	 * 
	 * Python implementation:
	 * - Use context managers (with statements) for index writer lifecycle
	 * - Implement similar synchronization with threading.Lock()
	 * - Consider using asyncio locks for async implementations
	 */
	public void writeDocuments(Collection<Document> documents) throws IOException {
		synchronized (writeLock) {
			// Create short-lived IndexWriter with StandardAnalyzer for text processing
			try (IndexWriter indexWriter = new IndexWriter(indexDirectory, 
					new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))) {
				indexWriter.addDocuments(documents);
			}
			
			// Refresh IndexSearcher if it exists to reflect new documents
			// This ensures read operations see the newly written data immediately
			if (indexSearcher != null) {
				indexSearcher = new IndexSearcher(DirectoryReader.open(indexDirectory));
			}
		}
	}

	/**
	 * Deletes documents from the index based on a Lucene query.
	 * 
	 * Usage patterns:
	 * - Remove outdated concepts during index updates
	 * - Delete ValueSet documents when they are removed
	 * - Clean up test data during unit testing
	 * 
	 * Implementation notes:
	 * - Uses same synchronization pattern as write operations
	 * - Automatically refreshes IndexSearcher after deletion
	 * - Query-based deletion allows selective document removal
	 * 
	 * Python implementation:
	 * - Use similar query-based deletion in Whoosh/PyLucene
	 * - Implement same refresh pattern for search consistency
	 */
	public void deleteDocuments(Query build) throws IOException {
		synchronized (writeLock) {
			try (IndexWriter indexWriter = new IndexWriter(indexDirectory, 
					new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))) {
				indexWriter.deleteDocuments(build);
			}
			
			// Refresh IndexSearcher to reflect deletions
			if (indexSearcher != null) {
				indexSearcher = new IndexSearcher(DirectoryReader.open(indexDirectory));
			}
		}
	}

	/**
	 * Returns the IndexSearcher for read operations, with lazy initialization.
	 * 
	 * Behavior:
	 * - Returns existing IndexSearcher if available
	 * - Throws FHIR exception if no index has been loaded (enables proper API error responses)
	 * - IndexSearcher is thread-safe for read operations (multiple concurrent readers allowed)
	 * 
	 * Error handling:
	 * - Returns HTTP 409 (Conflict) when SNOMED CT data not yet loaded
	 * - Provides clear error message for API consumers
	 * 
	 * Python implementation:
	 * - Use similar lazy initialization pattern
	 * - Implement appropriate exception types for HTTP error responses
	 * - Consider using property decorators for getter behavior
	 */
	public IndexSearcher getIndexSearcher() throws IOException {
		if (indexSearcher == null) {
			throw FHIRHelper.exception("SNOMED CT has not yet been loaded.", OperationOutcome.IssueType.CONFLICT, 409);
		}
		return indexSearcher;
	}

	/**
	 * Initializes the IndexSearcher for read operations after index creation/loading.
	 * 
	 * Called after:
	 * - Successful SNOMED CT import completion
	 * - Application startup when existing index is detected
	 * - Test setup to enable query operations
	 * 
	 * Creates new DirectoryReader/IndexSearcher pointing to current index state.
	 * 
	 * Python implementation:
	 * - Initialize search index reader/searcher
	 * - Set up query execution capability
	 */
	public void enableRead() throws IOException {
		indexSearcher = new IndexSearcher(DirectoryReader.open(indexDirectory));
	}

	/**
	 * Disables read operations by clearing the IndexSearcher reference.
	 * 
	 * Used for:
	 * - Application shutdown cleanup
	 * - Test teardown
	 * - Forcing re-initialization during index rebuilds
	 * 
	 * Python implementation:
	 * - Clear search index references
	 * - Release any held resources
	 */
	public void disableRead() {
		indexSearcher = null;
	}
}
