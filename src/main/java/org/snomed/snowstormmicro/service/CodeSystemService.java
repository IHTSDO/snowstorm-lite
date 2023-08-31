package org.snomed.snowstormmicro.service;

import org.apache.lucene.document.*;
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
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

@Service
public class CodeSystemService {

	public static final String TYPE = "_type";

	private IndexSearcher indexSearcher;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public CodeSystem getCodeSystem() throws IOException {
		TopDocs docs = indexSearcher.search(new TermQuery(new Term(TYPE, CodeSystem.DOC_TYPE)), 1);
		if (docs.totalHits.value == 0) {
			return null;
		}
		Document codeSystemDoc = indexSearcher.doc(docs.scoreDocs[0].doc);
		return getCodeSystemFromDoc(codeSystemDoc);
	}

	private CodeSystem getCodeSystemFromDoc(Document codeSystemDoc) {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setVersionDate(codeSystemDoc.get(CodeSystem.FieldNames.VERSION_DATE));
		codeSystem.setVersionUri(codeSystemDoc.get(CodeSystem.FieldNames.VERSION_URI));
		return codeSystem;
	}

	public Long getConceptIdFromDoc(Document conceptDoc) {
		return Long.parseLong(conceptDoc.get(Concept.FieldNames.ID));
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

	public Long getConceptIdFromDescriptionDoc(Document descriptionDoc) {
		return Long.parseLong(descriptionDoc.get(Description.FieldNames.CONCEPT_ID));
	}

	public List<Document> getDocs(Concept concept) {
		List<Document> docs = new ArrayList<>();
		docs.add(getConceptDoc(concept));
		for (Description description : concept.getDescriptions()) {
			docs.add(getDescriptionDoc(concept, description));
		}
		return docs;
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
			// For display store each description with PT flags
			String serialisedDescription = serialiseDescription(description);
			conceptDoc.add(new StoredField(Concept.FieldNames.TERM_STORED, serialisedDescription));
		}

		return conceptDoc;
	}

	private Document getDescriptionDoc(Concept concept, Description description) {
		Document doc = new Document();
		doc.add(new StringField(TYPE, Description.DOC_TYPE, Field.Store.YES));
		doc.add(new StringField(Description.FieldNames.CONCEPT_ID, concept.getConceptId(), Field.Store.YES));

		// For search store just language and term
		String fieldName = format("%s_%s", Description.FieldNames.TERM, description.getLang());
		doc.add(new TextField(fieldName, description.getTerm(), Field.Store.YES));
		doc.add(new NumericDocValuesField(Description.FieldNames.TERM_LENGTH, description.getTerm().length()));

		return doc;
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

	public Document getCodeSystemDoc(ComponentFactoryImpl componentFactory, String versionUri) {
		Document codeSystemDoc = new Document();
		codeSystemDoc.add(new StringField(TYPE, CodeSystem.DOC_TYPE, Field.Store.YES));
		Integer maxDate = componentFactory.getMaxDate();
		logger.info("Detected release date is {}", maxDate);
		if (maxDate != null) {
			codeSystemDoc.add(new StringField(CodeSystem.FieldNames.VERSION_DATE, maxDate.toString(), Field.Store.YES));
			codeSystemDoc.add(new StringField(CodeSystem.FieldNames.VERSION_URI, versionUri, Field.Store.YES));
		}
		return codeSystemDoc;
	}

	public void setIndexSearcher(IndexSearcher indexSearcher) {
		this.indexSearcher = indexSearcher;
	}
}
