package org.snomed.snowstormmicro.loading;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.domain.Description;

import java.io.IOException;

public class IndexCreator implements AutoCloseable {

	private final org.apache.lucene.index.IndexWriter indexWriter;

	public IndexCreator(Directory directory) throws IOException {
		IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
		indexWriter = new IndexWriter(directory, config);
	}

	public void createIndex(ComponentFactoryImpl componentFactory) throws IOException {
		int count = 0;
		System.out.println("Dot every 10K");
		for (Concept concept : componentFactory.getConceptMap().values()) {
			Document conceptDoc = new Document();
			conceptDoc.add(new StringField(Concept.FieldNames.ID, concept.getConceptId(), Field.Store.YES));
			conceptDoc.add(new StringField(Concept.FieldNames.ACTIVE, concept.isActive() ? "1" : "0", Field.Store.YES));
			for (String ancestor : concept.getAncestors()) {
				conceptDoc.add(new StringField(Concept.FieldNames.ANCESTORS, ancestor, Field.Store.YES));
			}
			for (String refsetId : concept.getMembership()) {
				conceptDoc.add(new StringField(Concept.FieldNames.MEMBERSHIP, refsetId, Field.Store.YES));
			}

			for (Description description : concept.getDescriptions()) {
				// TODO: Add language and acceptability. Perhaps field name of: term_en_200000333
				// TODO: Need to store description type and acceptability to select "display" and return designations
				conceptDoc.add(new StringField(Concept.FieldNames.TERM, description.getTerm(), Field.Store.YES));
			}
			indexWriter.addDocument(conceptDoc);
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
	}
}
