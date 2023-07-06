package org.snomed.snowstormmicro.service;

import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormmicro.domain.CodeSystem;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.domain.Description;
import org.snomed.snowstormmicro.loading.ComponentFactoryImpl;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static java.lang.String.format;

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
		for (IndexableField termField : conceptDoc.getFields(Concept.FieldNames.TERM_STORED)) {
			concept.addDescription(deserialiseDescription(termField.stringValue()));
		}
		return concept;
	}

	public Document getConceptDoc(Concept concept) {
		Document conceptDoc = new Document();
		conceptDoc.add(new StringField(TYPE, Concept.DOC_TYPE, Field.Store.YES));
		conceptDoc.add(new StringField(Concept.FieldNames.ID, concept.getConceptId(), Field.Store.YES));
		conceptDoc.add(new StringField(Concept.FieldNames.ACTIVE, concept.isActive() ? "1" : "0", Field.Store.YES));
		conceptDoc.add(new NumericDocValuesField(Concept.FieldNames.ACTIVE_SORT, concept.isActive() ? 1 : 0));
		for (String ancestor : concept.getAncestors()) {
			conceptDoc.add(new StringField(Concept.FieldNames.ANCESTORS, ancestor, Field.Store.YES));
		}
		for (String refsetId : concept.getMembership()) {
			conceptDoc.add(new StringField(Concept.FieldNames.MEMBERSHIP, refsetId, Field.Store.YES));
		}

		String ptTerm = null;
		for (Description description : concept.getDescriptions()) {
			// For search store just language and term
			String fieldName = format("%s_%s", Concept.FieldNames.TERM, description.getLang());
			conceptDoc.add(new TextField(fieldName, description.getTerm(), Field.Store.YES));

			// For display store each description with PT flags
			String serialisedDescription = serialiseDescription(description);
			conceptDoc.add(new StoredField(Concept.FieldNames.TERM_STORED, serialisedDescription));
			if (!description.getPreferredLangRefsets().isEmpty()) {
				ptTerm = description.getTerm();
				conceptDoc.add(new StringField(Concept.FieldNames.PT, ptTerm, Field.Store.YES));
			}
		}
		if (ptTerm != null) {
			conceptDoc.add(new NumericDocValuesField(Concept.FieldNames.PT_TERM_LENGTH, ptTerm.length()));
			conceptDoc.add(new NumericDocValuesField(Concept.FieldNames.PT_WORD_COUNT, getWordCount(ptTerm)));
			conceptDoc.add(new SortedDocValuesField(Concept.FieldNames.PT_STORED, new BytesRef(ptTerm)));
		} else {
			conceptDoc.add(new NumericDocValuesField(Concept.FieldNames.PT_WORD_COUNT, 200));
		}

		return conceptDoc;
	}

	private int getWordCount(String term) {
		return term.split(" ").length;
	}

	private static String serialiseDescription(Description description) {
		String serialisedDescription;
		if (description.isFsn()) {
			serialisedDescription = format("fsn|%s|%s", description.getLang(), description.getTerm());
		} else {
			serialisedDescription = format("syn|%s|%s|%s", description.getLang(), description.getTerm(), String.join(",", description.getPreferredLangRefsets()));
		}
		return serialisedDescription;
	}

	private static Description deserialiseDescription(String serialisedDescription) {
		Description description = new Description();
		String[] split = serialisedDescription.split("\\|");
		description.setLang(split[1]);
		description.setTerm(split[2]);
		if (serialisedDescription.startsWith("fsn")) {
			description.setFsn(true);
		} else {
			if (split.length == 4) {
				for (String langRefset : split[3].split(",")) {
					description.getPreferredLangRefsets().add(langRefset);
				}
			}
		}
		return description;
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
