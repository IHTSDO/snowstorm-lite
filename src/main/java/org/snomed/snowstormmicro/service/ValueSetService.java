package org.snomed.snowstormmicro.service;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ValueSet;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.domain.Description;
import org.snomed.snowstormmicro.fhir.FHIRConstants;
import org.snomed.snowstormmicro.fhir.FHIRHelper;
import org.snomed.snowstormmicro.fhir.FHIRServerResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.snomed.snowstormmicro.fhir.FHIRHelper.exception;

@Service
public class ValueSetService {

	public static final String TYPE = "_type";

	@Autowired
	private CodeSystemService codeSystemService;

	private IndexSearcher indexSearcher;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ValueSet expand(String url, String termFilter, int offset, int count) throws IOException {
		String snomedVS = FHIRConstants.SNOMED_URI + "?fhir_vs";
		if (url.startsWith(snomedVS)) {
			String type = url.replace(snomedVS, "");

			String ancestor = null;
			if (type.startsWith("=isa/")) {
				ancestor = type.replace("=isa/", "");
			} else if (!type.isEmpty()) {
				throw getValueSetNotFound();
			}

			List<Long> conceptIdsFromTermSearch = getTermFilterMatches(termFilter);

			BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
			queryBuilder.add(new TermQuery(new Term(TYPE, Concept.DOC_TYPE)), BooleanClause.Occur.MUST);
			if (conceptIdsFromTermSearch != null) {
				queryBuilder.add(new TermInSetQuery(Concept.FieldNames.ID, conceptIdsFromTermSearch.stream()
								.map(Object::toString).map(BytesRef::new).collect(Collectors.toList())), BooleanClause.Occur.MUST);
			}
			if (ancestor != null) {
				BooleanQuery.Builder ancestorQueryBuilder = new BooleanQuery.Builder();
				ancestorQueryBuilder.add(new TermQuery(new Term(Concept.FieldNames.ID, ancestor)), BooleanClause.Occur.SHOULD);
				ancestorQueryBuilder.add(new TermQuery(new Term(Concept.FieldNames.ANCESTORS, ancestor)), BooleanClause.Occur.SHOULD);
				queryBuilder.add(ancestorQueryBuilder.build(), BooleanClause.Occur.MUST);
			}
			BooleanQuery query = queryBuilder.build();
			TopDocs queryResult = indexSearcher.search(query, 100_000, new Sort(
					new SortedNumericSortField(Concept.FieldNames.ACTIVE_SORT, SortField.Type.INT, true),
					new SortedNumericSortField(Concept.FieldNames.PT_WORD_COUNT, SortField.Type.INT),
					new SortField(Concept.FieldNames.PT_STORED, SortField.Type.STRING)));

			List<ValueSet.ValueSetExpansionContainsComponent> contains = new ArrayList<>();
			int offsetReached = 0;

			List<Concept> conceptPage = new ArrayList<>();

			if (conceptIdsFromTermSearch != null) {
				Map<Long, Integer> matchedConcepts = new HashMap<>();
				// Load all concept ids
				for (ScoreDoc scoreDoc : queryResult.scoreDocs) {
					if (offsetReached < offset) {
						offsetReached++;
						continue;
					}
					Long conceptId = codeSystemService.getConceptIdFromDoc(indexSearcher.doc(scoreDoc.doc));
					matchedConcepts.put(conceptId, scoreDoc.doc);
				}

				// Collect concepts in order from term search
				for (Long conceptId : conceptIdsFromTermSearch) {
					Integer docId = matchedConcepts.get(conceptId);
					if (docId != null) {
						Concept concept = codeSystemService.getConceptFromDoc(indexSearcher.doc(docId));
						conceptPage.add(concept);
						if (conceptPage.size() == count) {
							break;
						}
					}
				}
			} else {
				for (ScoreDoc scoreDoc : queryResult.scoreDocs) {
					if (offsetReached < offset) {
						offsetReached++;
						continue;
					}
					Concept concept = codeSystemService.getConceptFromDoc(indexSearcher.doc(scoreDoc.doc));
					conceptPage.add(concept);
					if (conceptPage.size() == count) {
						break;
					}
				}

			}

			for (Concept concept : conceptPage) {
				ValueSet.ValueSetExpansionContainsComponent component = new ValueSet.ValueSetExpansionContainsComponent()
						.setSystem(FHIRConstants.SNOMED_URI)
						.setCode(concept.getConceptId())
						.setDisplay(concept.getPT());
				if (!concept.isActive()) {
					component.setInactive(true);
				}
				contains.add(component);
			}

			ValueSet valueSet = new ValueSet();
			valueSet.setUrl(url);
			valueSet.setCopyright(FHIRConstants.SNOMED_VALUESET_COPYRIGHT);
			valueSet.setStatus(Enumerations.PublicationStatus.ACTIVE);
			valueSet.setExperimental(false);
			ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
			expansion.setIdentifier(UUID.randomUUID().toString());
			expansion.setTimestamp(new Date());
			expansion.setTotal((int) queryResult.totalHits.value);
			expansion.setContains(contains);
			valueSet.setExpansion(expansion);
			return valueSet;
		} else {
			throw getValueSetNotFound();
		}
	}

	private List<Long> getTermFilterMatches(String termFilter) throws IOException {
		if (termFilter == null || termFilter.isEmpty()) {
			return null;
		}

		if (termFilter.length() < 2) {
			throw FHIRHelper.exception("The filter param must be 3 characters or more.", OperationOutcome.IssueType.TOOCOSTLY, 422);
		}

		BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
		queryBuilder.add(new TermQuery(new Term(TYPE, Description.DOC_TYPE)), BooleanClause.Occur.MUST);

		boolean fuzzy = termFilter.lastIndexOf("~") == termFilter.length() - 1;
		if (fuzzy) {
			termFilter = termFilter.substring(0, termFilter.length() - 1);
		}

		List<String> searchTokens = analyze(termFilter);
		for (String searchToken : searchTokens) {
			String languageCode = "_en";
			if (fuzzy) {
				queryBuilder.add(new FuzzyQuery(new Term(Description.FieldNames.TERM + languageCode, searchToken + "~")), BooleanClause.Occur.MUST);
			} else {
				queryBuilder.add(new WildcardQuery(new Term(Description.FieldNames.TERM + languageCode, searchToken + "*")), BooleanClause.Occur.MUST);
			}
		}
		TopDocs queryResult = indexSearcher.search(queryBuilder.build(), 100_000, new Sort(
				new SortedNumericSortField(Description.FieldNames.TERM_LENGTH, SortField.Type.INT),
				new SortField(Description.FieldNames.TERM, SortField.Type.STRING)
		));

		List<Long> conceptIds = new ArrayList<>();
		for (ScoreDoc scoreDoc : queryResult.scoreDocs) {
			Long conceptId = codeSystemService.getConceptIdFromDescriptionDoc(indexSearcher.doc(scoreDoc.doc));
			if (!conceptIds.contains(conceptId)) {
				conceptIds.add(conceptId);
			}
		}
		return conceptIds;
	}

	@NotNull
	private static FHIRServerResponseException getValueSetNotFound() {
		return exception("Value Set not found.", OperationOutcome.IssueType.NOTFOUND, 404);
	}

	private List<String> analyze(String text) {
		List<String> result = new ArrayList<>();
		try (StandardAnalyzer standardAnalyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET)) {
			TokenStream tokenStream = standardAnalyzer.tokenStream("contents", text);
			CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
			tokenStream.reset();
			while (tokenStream.incrementToken()) {
				result.add(attr.toString());
			}
		} catch (IOException e) {
			logger.error("Failed to analyze text {}", text, e);
		}
		return result;
	}

	public void setIndexSearcher(IndexSearcher indexSearcher) {
		this.indexSearcher = indexSearcher;
	}
}
