package org.snomed.snowstormmicro.service;

import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.snomed.snowstormmicro.domain.CodeSystem;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.domain.Description;
import org.snomed.snowstormmicro.fhir.FHIRHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import static java.lang.String.format;

@Service
public class CodeSystemRepository {

	public static final String TYPE = "_type";

	private IndexSearcher indexSearcher;

	public Concept getConcept(String code) throws IOException {
		TopDocs docs = indexSearcher.search(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(TYPE, Concept.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(Concept.FieldNames.ID, code)), BooleanClause.Occur.MUST)
				.build(), 1);
		if (docs.totalHits.value == 0) {
			return null;
		}
		Document conceptDoc = indexSearcher.doc(docs.scoreDocs[0].doc);
		return getConceptFromDoc(conceptDoc);

	}

	public CodeSystem getCodeSystem() throws IOException {
		TopDocs docs = indexSearcher.search(new TermQuery(new Term(TYPE, CodeSystem.DOC_TYPE)), 1);
		if (docs.totalHits.value == 0) {
			return null;
		}
		Document codeSystemDoc = indexSearcher.doc(docs.scoreDocs[0].doc);
		return getCodeSystemFromDoc(codeSystemDoc);
	}

	public Document getCodeSystemDoc(String versionUri) {
		Document codeSystemDoc = new Document();
		codeSystemDoc.add(new StringField(TYPE, CodeSystem.DOC_TYPE, Field.Store.YES));

		Matcher matcher = FHIRHelper.SNOMED_URI_MODULE_AND_VERSION_PATTERN.matcher(versionUri);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("SNOMED CT Edition version URI does not match expected format.");
		}
		String moduleId = matcher.group(1);
		String versionDate = matcher.group(2);
		codeSystemDoc.add(new StringField(CodeSystem.FieldNames.URI_MODULE, moduleId, Field.Store.YES));
		codeSystemDoc.add(new StringField(CodeSystem.FieldNames.VERSION_DATE, versionDate, Field.Store.YES));
		return codeSystemDoc;
	}

	private CodeSystem getCodeSystemFromDoc(Document codeSystemDoc) {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUriModule(codeSystemDoc.get(CodeSystem.FieldNames.URI_MODULE));
		codeSystem.setVersionDate(codeSystemDoc.get(CodeSystem.FieldNames.VERSION_DATE));
		return codeSystem;
	}

	public Long getConceptIdFromDoc(Document conceptDoc) {
		return Long.parseLong(conceptDoc.get(Concept.FieldNames.ID));
	}

	public Concept getConceptFromDoc(Document conceptDoc) {
		Concept concept = new Concept();
		concept.setConceptId(conceptDoc.get(Concept.FieldNames.ID));
		concept.setActive(conceptDoc.get(Concept.FieldNames.ACTIVE).equals("1"));
		concept.setEffectiveTime(conceptDoc.get(Concept.FieldNames.EFFECTIVE_TIME));
		concept.setModuleId(conceptDoc.get(Concept.FieldNames.MODULE));
		concept.setDefined(conceptDoc.get(Concept.FieldNames.DEFINED).equals("1"));
		for (IndexableField termField : conceptDoc.getFields(Concept.FieldNames.TERM_STORED)) {
			concept.addDescription(deserialiseDescription(termField.stringValue()));
		}
		for (IndexableField parent : conceptDoc.getFields(Concept.FieldNames.PARENTS)) {
			concept.addParentCode(parent.stringValue());
		}
		for (IndexableField ancestor : conceptDoc.getFields(Concept.FieldNames.ANCESTORS)) {
			concept.addAncestorCode(ancestor.stringValue());
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
		conceptDoc.add(new StringField(Concept.FieldNames.DEFINED, concept.isDefined() ? "1" : "0", Field.Store.YES));
		conceptDoc.add(new StringField(Concept.FieldNames.EFFECTIVE_TIME, concept.getEffectiveTime(), Field.Store.YES));
		conceptDoc.add(new StringField(Concept.FieldNames.MODULE, concept.getModuleId(), Field.Store.YES));
		conceptDoc.add(new NumericDocValuesField(Concept.FieldNames.ACTIVE_SORT, concept.isActive() ? 1 : 0));
		for (Concept parent : concept.getParents()) {
			conceptDoc.add(new StringField(Concept.FieldNames.PARENTS, parent.getConceptId(), Field.Store.YES));
		}
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

	public void setIndexSearcher(IndexSearcher indexSearcher) {
		this.indexSearcher = indexSearcher;
	}
}
