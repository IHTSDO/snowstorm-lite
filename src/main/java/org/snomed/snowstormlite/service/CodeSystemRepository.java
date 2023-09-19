package org.snomed.snowstormlite.service;

import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.snomed.snowstormlite.domain.CodeSystem;
import org.snomed.snowstormlite.domain.Concept;
import org.snomed.snowstormlite.domain.Description;
import org.snomed.snowstormlite.domain.Relationship;
import org.snomed.snowstormlite.fhir.FHIRHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

import static java.lang.String.format;

@Service
public class CodeSystemRepository implements TermProvider {

	public static final String TYPE = "_type";

	@Autowired
	private IndexSearcherProvider indexSearcherProvider;

	@Override
	public Map<String, String> getTerms(Collection<String> codes) throws IOException {
		if (codes.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, String> termsMap = new HashMap<>();
		IndexSearcher indexSearcher = indexSearcherProvider.getIndexSearcher();
		TopDocs docs = indexSearcher.search(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(TYPE, Concept.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(QueryHelper.termsQuery(Concept.FieldNames.ID, codes), BooleanClause.Occur.MUST)
				.build(), codes.size());
		for (ScoreDoc scoreDoc : docs.scoreDocs) {
			Concept concept = getConceptFromDoc(indexSearcher.doc(scoreDoc.doc), true);
			termsMap.put(concept.getConceptId(), concept.getPT());
		}
		return termsMap;
	}

	public Concept getConcept(String code) throws IOException {
		IndexSearcher indexSearcher = indexSearcherProvider.getIndexSearcher();
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
		IndexSearcher indexSearcher = indexSearcherProvider.getIndexSearcher();
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
		return getConceptFromDoc(conceptDoc, false);
	}

	private Concept getConceptFromDoc(Document conceptDoc, boolean descriptionsOnly) {
		Concept concept = new Concept();
		concept.setConceptId(conceptDoc.get(Concept.FieldNames.ID));
		if (!descriptionsOnly) {
			concept.setActive(conceptDoc.get(Concept.FieldNames.ACTIVE).equals("1"));
			concept.setEffectiveTime(conceptDoc.get(Concept.FieldNames.EFFECTIVE_TIME));
			concept.setModuleId(conceptDoc.get(Concept.FieldNames.MODULE));
			concept.setDefined(conceptDoc.get(Concept.FieldNames.DEFINED).equals("1"));
			for (IndexableField parent : conceptDoc.getFields(Concept.FieldNames.PARENTS)) {
				concept.addParentCode(parent.stringValue());
			}
			for (IndexableField ancestor : conceptDoc.getFields(Concept.FieldNames.ANCESTORS)) {
				concept.addAncestorCode(ancestor.stringValue());
			}
			for (IndexableField child : conceptDoc.getFields(Concept.FieldNames.CHILDREN)) {
				concept.addChildCode(child.stringValue());
			}
			deserialiseRelationships(conceptDoc.get(Concept.FieldNames.REL_STORED), concept);

		}
		for (IndexableField termField : conceptDoc.getFields(Concept.FieldNames.TERM_STORED)) {
			concept.addDescription(deserialiseDescription(termField.stringValue()));
		}
		return concept;
	}

	public List<Document> getDocs(Concept concept) {
		List<Document> docs = new ArrayList<>();
		docs.add(getConceptDoc(concept));
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
		for (String childCode : concept.getChildCodes()) {
			conceptDoc.add(new StringField(Concept.FieldNames.CHILDREN, childCode, Field.Store.YES));
		}
		for (String refsetId : concept.getMembership()) {
			conceptDoc.add(new StringField(Concept.FieldNames.MEMBERSHIP, refsetId, Field.Store.YES));
		}
		conceptDoc.add(new StoredField(Concept.FieldNames.REL_STORED, serialiseRelationships(concept.getRelationships())));

		int fsnTermLength = 0;
		int ptTermLength = 0;
		for (Description description : concept.getDescriptions()) {
			String term = description.getTerm();
			if (description.isFsn()) {
				fsnTermLength = term.length();
			}
			if (!description.getPreferredLangRefsets().isEmpty()) {
				ptTermLength = term.length();
			}
			conceptDoc.add(new TextField(Concept.FieldNames.TERM, term, Field.Store.YES));
			// For display store each description with PT flags
			String serialisedDescription = serialiseDescription(description);
			conceptDoc.add(new StoredField(Concept.FieldNames.TERM_STORED, serialisedDescription));
		}
		conceptDoc.add(new SortedNumericDocValuesField(Concept.FieldNames.PT_AND_FSN_TERM_LENGTH, ((long) ptTermLength * 1000) + fsnTermLength));

		return conceptDoc;
	}

	private String serialiseRelationships(Map<Integer, Set<Relationship>> relationships) {
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<Integer, Set<Relationship>> group : relationships.entrySet()) {
			builder.append(group.getKey());
			builder.append("{");
			for (Relationship relationship : group.getValue()) {
				builder.append(relationship.getType());
				builder.append("=");
				if (relationship.getTarget() != null) {
					builder.append(relationship.getTarget());
				} else {
					builder.append(relationship.getConcreteValue());
				}
				builder.append(",");
			}
			builder.deleteCharAt(builder.length() - 1);
			builder.append("}|");
		}
		if (!builder.isEmpty()) {
			builder.deleteCharAt(builder.length() - 1);
		}
		return builder.toString();
	}

	private void deserialiseRelationships(String serialisedRels, Concept concept) {
		if (serialisedRels.isEmpty()) {
			return;
		}
		String[] groups = serialisedRels.split("\\|");
		for (String group : groups) {
			String[] numAndRels = group.split("\\{");
			int groupNum = Integer.parseInt(numAndRels[0]);
			String rels = numAndRels[1].substring(0, numAndRels[1].length() - 1);
			for (String rel : rels.split(",")) {
				String[] parts = rel.split("=");
				Long target = null;
				String concreteValue = null;
				if (parts[1].startsWith("#") || parts[1].startsWith("\"")) {
					concreteValue = parts[1];
				} else {
					target = Long.parseLong(parts[1]);
				}
				concept.addRelationship(groupNum, Long.parseLong(parts[0]), target, concreteValue);
			}
		}
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

}
