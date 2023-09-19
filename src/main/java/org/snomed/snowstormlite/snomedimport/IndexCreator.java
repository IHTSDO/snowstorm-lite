package org.snomed.snowstormlite.snomedimport;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.domain.Concept;
import org.snomed.snowstormlite.service.CodeSystemRepository;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class IndexCreator implements AutoCloseable {

	private final CodeSystemRepository codeSystemRepository;
	private IndexWriter indexWriter;
	private Directory directory;
	private final String indexPath;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public IndexCreator(String indexPath, CodeSystemRepository codeSystemRepository) {
		this.codeSystemRepository = codeSystemRepository;
		this.indexPath = indexPath;
	}

	public void recreateIndex() throws IOException {
		IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
		File indexDirectory = new File(indexPath);
		if (indexDirectory.exists() && indexDirectory.listFiles() != null) {
			logger.info("Deleting existing index.");
			FileUtils.cleanDirectory(indexDirectory);
		}
		directory = new NIOFSDirectory(indexDirectory.toPath());
		indexWriter = new IndexWriter(directory, config);
	}

	public void createCodeSystem(String versionUri) throws IOException {
		Document codeSystemDoc = codeSystemRepository.getCodeSystemDoc(versionUri);
		indexWriter.addDocument(codeSystemDoc);
	}

	public void createConceptBatch(Collection<Concept> conceptBatch) throws IOException {
		logger.info("Writing batch of {} concepts.", conceptBatch.size());
		int count = 0;
		for (Concept concept : conceptBatch) {
			List<Document> conceptDocs = codeSystemRepository.getDocs(concept);
			indexWriter.addDocuments(conceptDocs);
			count++;
			if (count % 10_000 == 0) {
				System.out.print(".");
			}
		}
		System.out.println();
	}

	@Override
	public void close() throws IOException {
		indexWriter.close();
		directory.close();
	}
}
