package org.snomed.snowstormmicro.loading;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormmicro.service.CodeSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static java.lang.String.format;

@Service
public class ImportService {

	@Autowired
	private CodeSystemService codeSystemService;

	@Value("${index.path}")
	private String indexPath;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final LoadingProfile loadingProfile = LoadingProfile.light
			.withAllRefsets()
			.withInactiveConcepts()
			.withIncludedReferenceSetFilenamePattern(".*der2_Refset.*|.*der2_cRefset.*");

	public void importRelease(Set<String> releaseArchivePaths) throws IOException, ReleaseImportException {
		Set<InputStream> archiveInputStream = new HashSet<>();
		for (String filePath : releaseArchivePaths) {
			File file = new File(filePath);
			if (!file.isFile()) {
				throw new IOException(format("File not found %s", file.getAbsolutePath()));
			}
			archiveInputStream.add(new FileInputStream(file));
		}

		ReleaseImporter releaseImporter = new ReleaseImporter();
		File luceneIndex = new File(indexPath);
		if (luceneIndex.exists()) {
			logger.info("Deleting existing index directory.");
			FileSystemUtils.deleteRecursively(luceneIndex);
		}
		try (Directory directory = new NIOFSDirectory(luceneIndex.toPath())) {
			ComponentFactoryImpl componentFactory = new ComponentFactoryImpl();
			logger.info("Reading release files");
			releaseImporter.loadEffectiveSnapshotReleaseFileStreams(archiveInputStream, loadingProfile, componentFactory, false);
			logger.info("Writing lucene index");
			try (IndexCreator indexCreator = new IndexCreator(directory, codeSystemService)) {
				indexCreator.createIndex(componentFactory);
			}
		}
	}
}
