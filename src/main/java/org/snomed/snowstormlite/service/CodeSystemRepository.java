package org.snomed.snowstormlite.service;

import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.snomed.snowstormlite.domain.*;
import org.snomed.snowstormlite.fhir.FHIRHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

import static java.lang.String.format;
import static org.snomed.snowstormlite.util.CollectionUtils.orEmpty;

@Service
public class CodeSystemRepository implements TermProvider {

	public static final String TYPE = "_type";

	@Autowired
	private IndexIOProvider indexIOProvider;

	private FHIRCodeSystem codeSystem;

	@Override
	public Map<String, String> getTerms(Collection<String> codes) throws IOException {
		if (codes.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, String> termsMap = new HashMap<>();
		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
		TopDocs docs = indexSearcher.search(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(TYPE, FHIRConcept.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(QueryHelper.termsQuery(FHIRConcept.FieldNames.ID, codes), BooleanClause.Occur.MUST)
				.build(), codes.size());
		StoredFields storedFields = indexSearcher.storedFields();
		for (ScoreDoc scoreDoc : docs.scoreDocs) {
			FHIRConcept concept = getConceptFromDoc(storedFields.document(scoreDoc.doc), true);
			termsMap.put(concept.getConceptId(), concept.getPT());
		}
		return termsMap;
	}

	public FHIRConcept getConcept(String code) throws IOException {
		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
		TopDocs docs = indexSearcher.search(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(TYPE, FHIRConcept.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(FHIRConcept.FieldNames.ID, code)), BooleanClause.Occur.MUST)
				.build(), 1);
		if (docs.totalHits.value == 0) {
			return null;
		}
		Document conceptDoc = indexSearcher.storedFields().document(docs.scoreDocs[0].doc);
		return getConceptFromDoc(conceptDoc);
	}

	public FHIRCodeSystem getCodeSystem() {
		if (codeSystem == null) {
			try {
				IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
				TopDocs docs = indexSearcher.search(new TermQuery(new Term(TYPE, FHIRCodeSystem.DOC_TYPE)), 1);
				if (docs.totalHits.value == 0) {
					return null;
				}
				Document codeSystemDoc = indexSearcher.storedFields().document(docs.scoreDocs[0].doc);
				codeSystem = getCodeSystemFromDoc(codeSystemDoc);
			} catch (IOException e) {
				throw FHIRHelper.exceptionWithErrorLogging("Failed to load CodeSystem.", OperationOutcome.IssueType.EXCEPTION, 500, e);
			}
		}
		return codeSystem;
	}

	public void findByMapping(String refsetId, String code, boolean toSnomed) throws IOException {
		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
		TopDocs docs = indexSearcher.search(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(TYPE, FHIRConcept.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(FHIRConcept.FieldNames.MAPPING, code)), BooleanClause.Occur.MUST)
				.build(), 1);

	}

	public Document getCodeSystemDoc(String versionUri) {
		Document codeSystemDoc = new Document();
		codeSystemDoc.add(new StringField(TYPE, FHIRCodeSystem.DOC_TYPE, Field.Store.YES));

		Matcher matcher = FHIRHelper.SNOMED_URI_MODULE_AND_VERSION_PATTERN.matcher(versionUri);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("SNOMED CT Edition version URI does not match expected format.");
		}
		String moduleId = matcher.group(1);
		String versionDate = matcher.group(2);
		codeSystemDoc.add(new StringField(FHIRCodeSystem.FieldNames.URI_MODULE, moduleId, Field.Store.YES));
		codeSystemDoc.add(new StringField(FHIRCodeSystem.FieldNames.VERSION_DATE, versionDate, Field.Store.YES));
		codeSystemDoc.add(new LongField(FHIRCodeSystem.FieldNames.LAST_UPDATED, new Date().getTime(), Field.Store.YES));
		return codeSystemDoc;
	}

	private FHIRCodeSystem getCodeSystemFromDoc(Document codeSystemDoc) {
		FHIRCodeSystem codeSystem = new FHIRCodeSystem();
		codeSystem.setUriModule(codeSystemDoc.get(FHIRCodeSystem.FieldNames.URI_MODULE));
		codeSystem.setVersionDate(codeSystemDoc.get(FHIRCodeSystem.FieldNames.VERSION_DATE));
		String lastUpdated = codeSystemDoc.get(FHIRCodeSystem.FieldNames.LAST_UPDATED);
		if (lastUpdated != null) {
			codeSystem.setLastUpdated(new Date(Long.parseLong(lastUpdated)));
		}
		return codeSystem;
	}

	public Long getConceptIdFromDoc(Document conceptDoc) {
		return Long.parseLong(conceptDoc.get(FHIRConcept.FieldNames.ID));
	}

	public FHIRConcept getConceptFromDoc(Document conceptDoc) {
		return getConceptFromDoc(conceptDoc, false);
	}

	private FHIRConcept getConceptFromDoc(Document conceptDoc, boolean descriptionsOnly) {
		FHIRConcept concept = new FHIRConcept();
		concept.setConceptId(conceptDoc.get(FHIRConcept.FieldNames.ID));
		if (!descriptionsOnly) {
			concept.setActive(conceptDoc.get(FHIRConcept.FieldNames.ACTIVE).equals("1"));
			concept.setEffectiveTime(conceptDoc.get(FHIRConcept.FieldNames.EFFECTIVE_TIME));
			concept.setModuleId(conceptDoc.get(FHIRConcept.FieldNames.MODULE));
			concept.setDefined(conceptDoc.get(FHIRConcept.FieldNames.DEFINED).equals("1"));
			for (IndexableField parent : conceptDoc.getFields(FHIRConcept.FieldNames.PARENTS)) {
				concept.addParentCode(parent.stringValue());
			}
			for (IndexableField ancestor : conceptDoc.getFields(FHIRConcept.FieldNames.ANCESTORS)) {
				concept.addAncestorCode(ancestor.stringValue());
			}
			for (IndexableField child : conceptDoc.getFields(FHIRConcept.FieldNames.CHILDREN)) {
				concept.addChildCode(child.stringValue());
			}
			for (IndexableField mapping : conceptDoc.getFields(FHIRConcept.FieldNames.MAPPING)) {
				concept.addMapping(FHIRMapping.fromIndexString(mapping.stringValue()));
			}
			deserialiseRelationships(conceptDoc.get(FHIRConcept.FieldNames.REL_STORED), concept);

		}
		for (IndexableField termField : conceptDoc.getFields(FHIRConcept.FieldNames.TERM_STORED)) {
			concept.addDescription(deserialiseDescription(termField.stringValue()));
		}
		return concept;
	}

	public List<Document> getDocs(List<FHIRConcept> concepts) {
		List<Document> docs = new ArrayList<>();
		for (FHIRConcept concept : concepts) {
			docs.add(getConceptDoc(concept));
		}
		return docs;
	}

	public Document getConceptDoc(FHIRConcept concept) {
		Document conceptDoc = new Document();
		conceptDoc.add(new StringField(TYPE, FHIRConcept.DOC_TYPE, Field.Store.YES));
		conceptDoc.add(new StringField(FHIRConcept.FieldNames.ID, concept.getConceptId(), Field.Store.YES));
		conceptDoc.add(new StringField(FHIRConcept.FieldNames.ACTIVE, concept.isActive() ? "1" : "0", Field.Store.YES));
		conceptDoc.add(new StringField(FHIRConcept.FieldNames.DEFINED, concept.isDefined() ? "1" : "0", Field.Store.YES));
		conceptDoc.add(new StringField(FHIRConcept.FieldNames.EFFECTIVE_TIME, concept.getEffectiveTime(), Field.Store.YES));
		conceptDoc.add(new StringField(FHIRConcept.FieldNames.MODULE, concept.getModuleId(), Field.Store.YES));
		conceptDoc.add(new NumericDocValuesField(FHIRConcept.FieldNames.ACTIVE_SORT, concept.isActive() ? 1 : 0));
		for (FHIRConcept parent : concept.getParents()) {
			conceptDoc.add(new StringField(FHIRConcept.FieldNames.PARENTS, parent.getConceptId(), Field.Store.YES));
		}
		for (String ancestor : concept.getAncestors()) {
			conceptDoc.add(new StringField(FHIRConcept.FieldNames.ANCESTORS, ancestor, Field.Store.YES));
		}
		for (String childCode : concept.getChildCodes()) {
			conceptDoc.add(new StringField(FHIRConcept.FieldNames.CHILDREN, childCode, Field.Store.YES));
		}
		for (Set<FHIRRelationship> group : concept.getRelationships().values()) {
			for (FHIRRelationship relationship : group) {
				if (!relationship.isConcrete()) {
					conceptDoc.add(new StringField(FHIRConcept.FieldNames.ATTRIBUTE_PREFIX + relationship.getType(), relationship.getTarget().toString(), Field.Store.NO));
					conceptDoc.add(new StringField(FHIRConcept.FieldNames.ATTRIBUTE_PREFIX + "any", relationship.getTarget().toString(), Field.Store.NO));
				}
			}
		}
		for (String refsetId : concept.getMembership()) {
			conceptDoc.add(new StringField(FHIRConcept.FieldNames.MEMBERSHIP, refsetId, Field.Store.YES));
		}
		List<FHIRMapping> fhirMappings = orEmpty(concept.getMappings());
		fhirMappings.sort(Comparator.comparing(FHIRMapping::getMessage, Comparator.nullsFirst(String::compareTo)));
		for (FHIRMapping mapping : fhirMappings) {
			conceptDoc.add(new StringField(FHIRConcept.FieldNames.MAPPING, mapping.toIndexString(), Field.Store.YES));
		}
		conceptDoc.add(new StoredField(FHIRConcept.FieldNames.REL_STORED, serialiseRelationships(concept.getRelationships())));

		int fsnTermLength = 0;
		int ptTermLength = 0;
		for (FHIRDescription description : concept.getDescriptions()) {
			String term = description.getTerm();
			if (description.isFsn()) {
				fsnTermLength = term.length();
			}
			if (!description.getPreferredLangRefsets().isEmpty()) {
				ptTermLength = term.length();
			}
			conceptDoc.add(new TextField(FHIRConcept.FieldNames.TERM, term, Field.Store.YES));
			// For display store each description with PT flags
			String serialisedDescription = serialiseDescription(description);
			conceptDoc.add(new StoredField(FHIRConcept.FieldNames.TERM_STORED, serialisedDescription));
		}
		conceptDoc.add(new SortedNumericDocValuesField(FHIRConcept.FieldNames.PT_AND_FSN_TERM_LENGTH, ((long) ptTermLength * 1000) + fsnTermLength));

		return conceptDoc;
	}

	private String serialiseRelationships(Map<Integer, Set<FHIRRelationship>> relationships) {
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<Integer, Set<FHIRRelationship>> group : relationships.entrySet()) {
			builder.append(group.getKey());
			builder.append("{");
			for (FHIRRelationship relationship : group.getValue()) {
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

	private void deserialiseRelationships(String serialisedRels, FHIRConcept concept) {
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

	private static String serialiseDescription(FHIRDescription description) {
		String serialisedDescription;
		if (description.isFsn()) {
			serialisedDescription = format("fsn|%s|%s", description.getLang(), description.getTerm());
		} else {
			serialisedDescription = format("syn|%s|%s|%s", description.getLang(), description.getTerm(), String.join(",", description.getPreferredLangRefsets()));
		}
		return serialisedDescription;
	}

	private static FHIRDescription deserialiseDescription(String serialisedDescription) {
		FHIRDescription description = new FHIRDescription();
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

	public void clearCache() {
		codeSystem = null;
	}
}
