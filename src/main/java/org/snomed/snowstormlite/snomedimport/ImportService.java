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

@Service
public class ImportService {

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Value("${index.path}")
	private String indexPath;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final LoadingProfile loadingProfile = LoadingProfile.light
			.withAllRefsets()
			.withInactiveConcepts()
			.withIncludedReferenceSetFilenamePattern(".*der2_Refset.*|.*der2_cRefset.*");

	public void importRelease(Set<String> releaseArchivePaths, String versionUri) throws IOException, ReleaseImportException {
		Set<InputStream> archiveInputStream = new HashSet<>();
		for (String filePath : releaseArchivePaths) {
			File file = new File(filePath);
			if (!file.isFile()) {
				throw new IOException(format("File not found %s", file.getAbsolutePath()));
			}
			archiveInputStream.add(new FileInputStream(file));
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
			releaseImporter.loadEffectiveSnapshotReleaseFileStreams(archiveInputStream, loadingProfile, componentFactoryProvider, false);

//			logger.info("Writing lucene index");
//			try (IndexCreator indexCreator = new IndexCreator(directory, codeSystemRepository)) {
//				indexCreator.createIndex(componentFactory, versionUri);
//			}
		}
	}
}
