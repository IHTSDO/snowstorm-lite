package org.snomed.snowstormmicro.loading;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.service.CodeSystemService;

import java.io.IOException;
import java.util.List;

public class IndexCreator implements AutoCloseable {

	private final IndexWriter indexWriter;
	private final CodeSystemService codeSystemService;

	public IndexCreator(Directory directory, CodeSystemService codeSystemService) throws IOException {
		IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
		indexWriter = new IndexWriter(directory, config);
		this.codeSystemService = codeSystemService;
	}

	public void createIndex(ComponentFactoryImpl componentFactory, String versionUri) throws IOException {
		int count = 0;
		for (Concept concept : componentFactory.getConceptMap().values()) {
			List<Document> conceptDocs = codeSystemService.getDocs(concept);
			indexWriter.addDocuments(conceptDocs);
			count++;
			if (count % 10_000 == 0) {
				System.out.print(".");
			}
		}
		Document codeSystemDoc = codeSystemService.getCodeSystemDoc(componentFactory, versionUri);
		indexWriter.addDocument(codeSystemDoc);
		System.out.println();
	}

	@Override
	public void close() throws IOException {
		indexWriter.close();
	}
}
