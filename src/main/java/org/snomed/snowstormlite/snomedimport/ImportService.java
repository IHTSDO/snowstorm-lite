package org.snomed.snowstormlite.snomedimport;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactoryProvider;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.fhir.FHIRHelper;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.IndexIOProvider;
import org.snomed.snowstormlite.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.google.common.collect.Iterables.partition;
import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRHelper.SNOMED_URI_MODULE_AND_VERSION_PATTERN;

/**
 * SNOMED CT Import Service - handles RF2 file processing and Lucene index creation.
 * 
 * This service coordinates the complex process of importing SNOMED CT RF2 release files
 * into a searchable Lucene index. It uses a multi-pass strategy to handle large datasets
 * efficiently within memory constraints:
 * 
 * Import Strategy:
 * 1. First pass: Load concepts and relationships into memory using snomed-boot library
 * 2. Second pass: Process descriptions in batches to manage memory usage
 * 3. Batch processing: Split concept processing into configurable batch sizes
 * 4. Index creation: Write processed data to Lucene index using IndexCreator
 * 
 * Memory Management:
 * - Configurable batch size (default 40k concepts) to stay within memory limits
 * - Progressive garbage collection and memory cleanup
 * - Description processing separated from concept loading to reduce peak memory
 * 
 * Concurrency Control:
 * - Synchronized import operations (only one import at a time)
 * - Import status tracking to prevent concurrent operations
 * - Clear error messaging for concurrent import attempts
 * 
 * Python implementation considerations:
 * - Use pandas or similar for efficient data processing
 * - Implement batch processing with generators to manage memory
 * - Use multiprocessing for parallel RF2 file parsing
 * - Consider using SQLite as intermediate storage for large datasets
 * - Implement progress tracking and cancellation support
 */
@Service
public class ImportService {

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private IndexIOProvider indexIOProvider;

	// Configurable batch size in thousands (e.g., 40 = 40,000 concepts per batch)
	// Smaller batches use less memory but create more index operations
	@Value("${import.batch-size}")
	private int importBatchSizeInThousands;

	// Import synchronization flag - prevents concurrent imports
	private boolean importRunning;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	// Loading profile configuration for snomed-boot library
	// Defines which RF2 file types to process and include
	private final LoadingProfile loadingProfile = LoadingProfile.light
			.withAllRefsets()        // Include all reference sets for full functionality
			.withInactiveConcepts(); // Include inactive concepts for completeness

	/**
	 * Imports SNOMED CT release from file paths.
	 * 
	 * Convenience method that converts file paths to input streams and delegates
	 * to the main import method. Validates file existence before processing.
	 * 
	 * Supports multiple RF2 archive files:
	 * - International Edition base package
	 * - Extension packages (country/organization specific)
	 * - Multiple packages can be combined in single import operation
	 * 
	 * Python implementation:
	 * ```python
	 * def import_release(file_paths: Set[str], version_uri: str):
	 *     input_streams = []
	 *     try:
	 *         for file_path in file_paths:
	 *             if not Path(file_path).is_file():
	 *                 raise FileNotFoundError(f"File not found: {file_path}")
	 *             input_streams.append(open(file_path, 'rb'))
	 *         
	 *         import_release_streams(input_streams, version_uri)
	 *     finally:
	 *         for stream in input_streams:
	 *             stream.close()
	 * ```
	 */
	public void importRelease(Set<String> releaseArchivePaths, String versionUri) throws IOException, ReleaseImportException {
		// Convert file paths to input streams with validation
		Set<InputStream> archiveInputStreams = new HashSet<>();
		for (String filePath : releaseArchivePaths) {
			File file = new File(filePath);
			if (!file.isFile()) {
				throw new IOException(format("File not found %s", file.getAbsolutePath()));
			}
			archiveInputStreams.add(new FileInputStream(file));
		}
		importReleaseStreams(archiveInputStreams, versionUri);
	}

	/**
	 * Main import method that processes RF2 input streams into Lucene index.
	 * 
	 * Synchronization and concurrency control:
	 * - Method is synchronized to prevent concurrent imports
	 * - Uses importRunning flag for additional concurrency protection
	 * - Returns HTTP 409 (Conflict) if import already in progress
	 * 
	 * Resource management:
	 * - Clears repository cache before import to free memory
	 * - Suggests garbage collection after import completion
	 * - Ensures importRunning flag is cleared in finally block
	 * 
	 * Error handling:
	 * - Preserves all exceptions and re-throws them
	 * - Logs completion status
	 * - Guaranteed cleanup in finally block
	 * 
	 * Python implementation:
	 * ```python
	 * import threading
	 * 
	 * class ImportService:
	 *     def __init__(self):
	 *         self._import_lock = threading.Lock()
	 *         self._import_running = False
	 *     
	 *     def import_release_streams(self, streams, version_uri):
	 *         with self._import_lock:
	 *             if self._import_running:
	 *                 raise ImportConflictError("Import already in progress")
	 *             
	 *             try:
	 *                 self._import_running = True
	 *                 self.code_system_repository.clear_cache()
	 *                 self._do_import_release_streams(streams, version_uri)
	 *                 gc.collect()  # Suggest garbage collection
	 *                 logger.info("Import complete")
	 *             finally:
	 *                 self._import_running = False
	 * ```
	 */
	public synchronized void importReleaseStreams(Set<InputStream> archiveInputStreams, String versionUri) throws IOException, ReleaseImportException {
		// Check for concurrent import operations
		if (importRunning) {
			throw FHIRHelper.exception("An import is already running. Concurrent import is not supported.", OperationOutcome.IssueType.CONFLICT, 409);
		}
		
		try {
			// Set import status and prepare for operation
			importRunning = true;
			codeSystemRepository.clearCache();
			
			// Execute the main import logic
			doImportReleaseStreams(archiveInputStreams, versionUri);
			
			// Suggest garbage collection after memory-intensive import operation
			System.gc();
			logger.info("Import complete");
		} finally {
			// Ensure import flag is cleared regardless of success/failure
			importRunning = false;
		}
	}

	/**
	 * Core import logic that processes RF2 streams using a sophisticated multi-pass strategy.
	 * 
	 * This method implements a complex import algorithm designed to handle large SNOMED CT
	 * datasets within memory constraints. The strategy involves:
	 * 
	 * Import Algorithm:
	 * 1. Validation: Verify version URI format against SNOMED CT URI standard
	 * 2. Index setup: Initialize Lucene index and create CodeSystem document
	 * 3. First pass: Load concepts and relationships using minimal descriptions
	 * 4. Batch processing: Process descriptions in memory-managed batches
	 * 5. Memory recovery: Aggressive cleanup between batches
	 * 
	 * Multi-pass Processing Strategy:
	 * - Pass 1 (ComponentFactoryWithMinimalDescriptions): Load core concept data
	 * - Pass 2+ (ComponentFactoryWithDescriptionBatch): Process descriptions in batches
	 * 
	 * Memory Management Techniques:
	 * - Batch size configuration controls memory usage vs. performance trade-off
	 * - Aggressive object nullification to trigger garbage collection
	 * - Progress reporting to monitor memory-intensive operations
	 * 
	 * Python implementation considerations:
	 * ```python
	 * def do_import_release_streams(self, streams, version_uri):
	 *     # Validate version URI format
	 *     if not self._validate_snomed_uri(version_uri):
	 *         raise ValueError("Invalid SNOMED CT URI format")
	 *     
	 *     with IndexCreator(self.index_provider, self.repository) as index_creator:
	 *         # Create code system document
	 *         index_creator.create_code_system(version_uri)
	 *         
	 *         # First pass: Load concepts and relationships
	 *         concepts = self._load_concepts_from_rf2(streams)
	 *         
	 *         # Batch processing for descriptions
	 *         batch_size = self.import_batch_size * 1000
	 *         for batch in self._batch_concepts(concepts, batch_size):
	 *             # Load descriptions for this batch
	 *             self._load_descriptions_for_batch(batch, streams)
	 *             
	 *             # Create index documents
	 *             index_creator.create_concept_batch(batch)
	 *             
	 *             # Memory cleanup
	 *             self._cleanup_batch_memory(batch)
	 *             
	 *             # Progress reporting
	 *             self._report_progress(batch_num, total_batches)
	 * ```
	 */
	public void doImportReleaseStreams(Set<InputStream> archiveInputStreams, String versionUri) throws IOException, ReleaseImportException {
		TimerUtil timer = new TimerUtil("Import");

		// Validate version URI format against SNOMED CT URI standard
		// Expected format: http://snomed.info/sct/[module-id]/version/[YYYYMMDD]
		if (!SNOMED_URI_MODULE_AND_VERSION_PATTERN.matcher(versionUri).matches()) {
			throw new IllegalArgumentException("Parameter 'version-uri' is not a valid SNOMED CT Edition Version URI. " +
					"Please use the format: 'http://snomed.info/sct/[module-id]/version/[YYYYMMDD]'. " +
					"See http://snomed.org/uri for examples of Edition version URIs");
		}

		// Initialize snomed-boot ReleaseImporter for RF2 file processing
		ReleaseImporter releaseImporter = new ReleaseImporter();
		
		// Use IndexCreator with try-with-resources for automatic cleanup
		try (IndexCreator indexCreator = new IndexCreator(indexIOProvider, codeSystemRepository)) {
			// Create CodeSystem document in Lucene index
			indexCreator.createCodeSystem(versionUri);

			// First pass component factory - loads concepts and relationships with minimal descriptions
			// This reduces memory usage by avoiding full description loading initially
			ComponentFactoryWithMinimalDescriptions componentFactoryBase = new ComponentFactoryWithMinimalDescriptions();
			
			// Component factory provider implements the multi-pass strategy
			// Returns different factories for different processing phases
			ComponentFactoryProvider componentFactoryProvider = new ComponentFactoryProvider() {

				private boolean firstFactoryProvided;          // Tracks first pass completion
				private Iterator<List<Long>> conceptIdBatchIterator;  // Batch iterator for memory management
				private Integer batchNumber = 0;              // Progress tracking

				@Override
				public ComponentFactory getNextComponentFactory() {
					// First pass: Return base factory for concepts and relationships
					if (!firstFactoryProvided) {
						firstFactoryProvided = true;
						return componentFactoryBase;
					} else if (batchNumber == 0) {
						System.out.println("Writing concepts to store");
					}

					// Initialize batch iterator for description processing
					if (conceptIdBatchIterator == null) {
						Set<Long> conceptIds = componentFactoryBase.getConceptMap().keySet();
						// Partition concept IDs into batches based on configured batch size
						conceptIdBatchIterator = partition(conceptIds, importBatchSizeInThousands * 1_000).iterator();
					}
					
					// Return batch-specific factory for each remaining batch
					if (conceptIdBatchIterator.hasNext()) {
						batchNumber++;
						return new ComponentFactoryWithDescriptionBatch(componentFactoryBase.getConceptMap(), new LongOpenHashSet(conceptIdBatchIterator.next())) {
							
							@Override
							public LoadingProfile getLoadingProfile() {
								// Configure loading profile for description-only processing
								// This pass only loads language reference set files
								return LoadingProfile.light
										.withoutConcepts()           // Skip concepts (already loaded)
										.withoutTextDefinitions()    // Skip text definitions
										.withoutRelationships()      // Skip relationships (already loaded)
										.withoutIdentifiers()        // Skip identifiers
										.withIncludedReferenceSetFilenamePattern(".?der2_cRefset_Language.*");  // Only language refsets
							}

							@Override
							public void loadingComponentsCompleted() throws ReleaseImportException {
								try {
									// Extract concepts for this batch
									Collection<Long> conceptIdBatch = getConceptIdBatch();
									List<FHIRConcept> conceptBatch = getConceptMap().values().stream()
											.filter(concept -> conceptIdBatch.contains(Long.parseLong(concept.getConceptId())))
											.toList();
									
									// Write batch to Lucene index
									indexCreator.createConceptBatch(conceptBatch);

									// Aggressive memory recovery - nullify references to trigger GC
									// This is critical for staying within memory limits
									conceptBatch.forEach(concept -> {
										concept.getDescriptions().forEach(desc -> desc.setConcept(null));
										concept.setDescriptions(null);
										concept.getMappings().clear();
									});

									// Calculate and display progress percentage
									float batchSize = importBatchSizeInThousands * 1_000;
									float completeCount = batchSize * batchNumber;
									int completePercent = (int)((completeCount / getConceptMap().size()) * 100);
									completePercent = Math.min(completePercent, 100);
									System.out.printf("%s%% complete%n", completePercent);
								} catch (IOException e) {
									throw new ReleaseImportException("Failed to write concept batch to index.", e);
								}
							}
						};
					} else {
						// No more batches - return null to signal completion
						return null;
					}
				}
			};

			// Execute the multi-pass import process
			logger.info("Reading release files");
			System.out.println("Import will take a few minutes, please be patient.");
			
			// Load RF2 snapshot files using the component factory provider
			// The 'false' parameter indicates snapshot processing (not delta)
			releaseImporter.loadEffectiveSnapshotReleaseFileStreams(archiveInputStreams, loadingProfile, componentFactoryProvider, false);
		}
		timer.finish();
	}

}
