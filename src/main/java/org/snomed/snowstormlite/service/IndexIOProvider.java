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

@Service
public class IndexIOProvider {

	private final FSDirectory indexDirectory;
	private IndexSearcher indexSearcher;
	private final Object writeLock;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public IndexIOProvider(@Value("${index.path}") String indexPath) throws IOException {
		writeLock = new Object();
		File indexDirFile = new File(indexPath);
		if (!indexDirFile.exists()) {
			if (!indexDirFile.mkdirs()) {
				logger.error("Failed to create index directory '{}'", indexDirFile.getAbsoluteFile());
			}
		}
		indexDirectory = FSDirectory.open(indexDirFile.toPath());
	}

	public void writeDocument(Document document) throws IOException {
		writeDocuments(Collections.singleton(document));
	}

	public void writeDocuments(Collection<Document> documents) throws IOException {
		synchronized (writeLock) {
			try (IndexWriter indexWriter = new IndexWriter(indexDirectory, new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))) {
				indexWriter.addDocuments(documents);
			}
			if (indexSearcher != null) {
				indexSearcher = new IndexSearcher(DirectoryReader.open(indexDirectory));
			}
		}
	}

	public void deleteDocuments(Query build) throws IOException {
		synchronized (writeLock) {
			try (IndexWriter indexWriter = new IndexWriter(indexDirectory, new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))) {
				indexWriter.deleteDocuments(build);
			}
			if (indexSearcher != null) {
				indexSearcher = new IndexSearcher(DirectoryReader.open(indexDirectory));
			}
		}
	}

	public IndexSearcher getIndexSearcher() throws IOException {
		if (indexSearcher == null) {
			throw FHIRHelper.exception("SNOMED CT has not yet been loaded.", OperationOutcome.IssueType.CONFLICT, 409);
		}
		return indexSearcher;
	}

	public void enableRead() throws IOException {
		indexSearcher = new IndexSearcher(DirectoryReader.open(indexDirectory));
	}

	public void disableRead() {
		indexSearcher = null;
	}
}
