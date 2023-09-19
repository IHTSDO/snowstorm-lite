package org.snomed.snowstormlite.snomedimport;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactoryProvider;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.domain.Concept;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.IndexSearcherProvider;
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

@Service
public class ImportService {

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private IndexSearcherProvider indexSearcherProvider;

	@Value("${index.path}")
	private String indexPath;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final LoadingProfile loadingProfile = LoadingProfile.light
			.withAllRefsets()
			.withInactiveConcepts()
			.withIncludedReferenceSetFilenamePattern(".*der2_Refset.*|.*der2_cRefset.*");

	public void importRelease(Set<String> releaseArchivePaths, String versionUri) throws IOException, ReleaseImportException {
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

	public void importReleaseStreams(Set<InputStream> archiveInputStreams, String versionUri) throws IOException, ReleaseImportException {
		doImportReleaseStreams(archiveInputStreams, versionUri);
		// Suggest GC after RF2 import
		System.gc();
		indexSearcherProvider.createIndexSearcher();
		logger.info("Import complete");
	}

	public void doImportReleaseStreams(Set<InputStream> archiveInputStreams, String versionUri) throws IOException, ReleaseImportException {
		TimerUtil timer = new TimerUtil("Import");

		if (!SNOMED_URI_MODULE_AND_VERSION_PATTERN.matcher(versionUri).matches()) {
			throw new IllegalArgumentException("Parameter 'version-uri' is not a valid SNOMED CT Edition Version URI. " +
					"Please use the format: 'http://snomed.info/sct/[module-id]/version/[YYYYMMDD]'. " +
					"See http://snomed.org/uri for examples of Edition version URIs");
		}

		ReleaseImporter releaseImporter = new ReleaseImporter();
		try (IndexCreator indexCreator = new IndexCreator(indexPath, codeSystemRepository)) {

			indexCreator.recreateIndex();
			indexCreator.createCodeSystem(versionUri);

			ComponentFactoryWithoutDescriptions componentFactoryBase = new ComponentFactoryWithoutDescriptions();
			ComponentFactoryProvider componentFactoryProvider = new ComponentFactoryProvider() {

				private boolean firstFactoryProvided;
				private Iterator<List<Long>> conceptIdBatchIterator;
				private final TimerUtil timer = new TimerUtil("Loading");
				private Integer batchNumber = 0;

				@Override
				public ComponentFactory getNextComponentFactory() {
					if (!firstFactoryProvided) {
						firstFactoryProvided = true;
						return componentFactoryBase;
					}

					if (conceptIdBatchIterator == null) {
						timer.checkpoint("Loaded concepts");
						Set<Long> conceptIds = componentFactoryBase.getConceptMap().keySet();
						conceptIdBatchIterator = partition(conceptIds, 50_000).iterator();
					}
					if (conceptIdBatchIterator.hasNext()) {
						batchNumber++;
						componentFactoryBase.clearDescriptions();
						return new ComponentFactoryWithDescriptionBatch(componentFactoryBase, new LongOpenHashSet(conceptIdBatchIterator.next())) {
							@Override
							public LoadingProfile getLoadingProfile() {
								return LoadingProfile.light
										.withoutConcepts()
										.withoutTextDefinitions()
										.withoutRelationships()
										.withoutIdentifiers()
										.withIncludedReferenceSetFilenamePattern(".?der2_cRefset_Language.*");
							}

							@Override
							public void loadingComponentsCompleted() throws ReleaseImportException {
								timer.checkpoint("Loaded description batch " + batchNumber);
								try {
									Collection<Long> conceptIdBatch = getConceptIdBatch();
									List<Concept> conceptBatch = getConceptMap().values().stream()
											.filter(concept -> conceptIdBatch.contains(Long.parseLong(concept.getConceptId())))
											.toList();
									indexCreator.createConceptBatch(conceptBatch);
									timer.checkpoint("Written to index, batch " + batchNumber);
								} catch (IOException e) {
									throw new ReleaseImportException("Failed to write concept batch to index.", e);
								}
							}
						};
					} else {
						timer.finish();
						return null;
					}
				}
			};

			logger.info("Reading release files");
			releaseImporter.loadEffectiveSnapshotReleaseFileStreams(archiveInputStreams, loadingProfile, componentFactoryProvider, false);
		}
		timer.finish();
	}

}
