package org.snomed.snowstormmicro.service;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormmicro.domain.CodeSystem;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.domain.Description;
import org.snomed.snowstormmicro.loading.ComponentFactoryImpl;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class CodeSystemService {

	public static final String TYPE = "_type";

	private IndexSearcher indexSearcher;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public CodeSystem getCodeSystem() throws IOException {
		TopDocs docs = indexSearcher.search(new TermQuery(new Term(TYPE, CodeSystem.DOC_TYPE)), 1);
		if (docs.totalHits == 0) {
			return null;
		}
		Document codeSystemDoc = indexSearcher.doc(docs.scoreDocs[0].doc);
		return getCodeSystemFromDoc(codeSystemDoc);
	}

	private CodeSystem getCodeSystemFromDoc(Document codeSystemDoc) {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setVersionDate(codeSystemDoc.get(CodeSystem.FieldNames.VERSION_DATE));
		return codeSystem;
	}

	public Concept getConceptFromDoc(Document conceptDoc) {
		Concept concept = new Concept();
		concept.setConceptId(conceptDoc.get(Concept.FieldNames.ID));
		concept.setActive(conceptDoc.get(Concept.FieldNames.ACTIVE).equals("1"));
		for (IndexableField termField : conceptDoc.getFields(Concept.FieldNames.TERM)) {
			concept.addDescription(new Description(termField.stringValue()));
		}
		return concept;
	}

	public Document getConceptDoc(Concept concept) {
		Document conceptDoc = new Document();
		conceptDoc.add(new StringField(TYPE, Concept.DOC_TYPE, Field.Store.YES));
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
			conceptDoc.add(new TextField(Concept.FieldNames.TERM, description.getTerm(), Field.Store.YES));
		}
		return conceptDoc;
	}

	public Document getCodeSystemDoc(ComponentFactoryImpl componentFactory) {
		Document codeSystemDoc = new Document();
		codeSystemDoc.add(new StringField(TYPE, CodeSystem.DOC_TYPE, Field.Store.YES));
		Integer maxDate = componentFactory.getMaxDate();
		logger.info("Detected release date is {}", maxDate);
		if (maxDate != null) {
			codeSystemDoc.add(new StringField(CodeSystem.FieldNames.VERSION_DATE, maxDate.toString(), Field.Store.YES));
		}
		return codeSystemDoc;
	}

	public void setIndexSearcher(IndexSearcher indexSearcher) {
		this.indexSearcher = indexSearcher;
	}
}
