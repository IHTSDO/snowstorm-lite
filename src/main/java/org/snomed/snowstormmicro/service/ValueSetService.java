package org.snomed.snowstormmicro.service;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ValueSet;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.fhir.FHIRConstants;
import org.snomed.snowstormmicro.fhir.FHIRServerResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.apache.lucene.search.SortField.FIELD_SCORE;
import static org.snomed.snowstormmicro.fhir.FHIRHelper.exception;

@Service
public class ValueSetService {

	public static final String TYPE = "_type";

	@Autowired
	private CodeSystemService codeSystemService;

	private IndexSearcher indexSearcher;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ValueSet expand(String url, String filter, int offset, int count) throws IOException {
		String snomedVS = FHIRConstants.SNOMED_URI + "?fhir_vs";
		if (url.startsWith(snomedVS)) {
			String type = url.replace(snomedVS, "");

			String ancestor = null;
			if (type.startsWith("=isa/")) {
				ancestor = type.replace("=isa/", "");
			} else if (!type.isEmpty()) {
				throw getValueSetNotFound();
			}

			BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
			queryBuilder.add(new TermQuery(new Term(TYPE, Concept.DOC_TYPE)), BooleanClause.Occur.MUST);
			if (ancestor != null) {
				BooleanQuery.Builder ancestorQueryBuilder = new BooleanQuery.Builder();
				ancestorQueryBuilder.add(new TermQuery(new Term(Concept.FieldNames.ID, ancestor)), BooleanClause.Occur.SHOULD);
				ancestorQueryBuilder.add(new TermQuery(new Term(Concept.FieldNames.ANCESTORS, ancestor)), BooleanClause.Occur.SHOULD);
				queryBuilder.add(ancestorQueryBuilder.build(), BooleanClause.Occur.MUST);
			}
			if (filter != null) {
				List<String> searchTokens = analyze(filter);
				for (String searchToken : searchTokens) {
					String languageCode = "_en";
					queryBuilder.add(new WildcardQuery(new Term(Concept.FieldNames.TERM + languageCode, searchToken + "*")), BooleanClause.Occur.MUST);
				}
			}
			BooleanQuery query = queryBuilder.build();
			System.out.println(query.toString());
			TopDocs queryResult = indexSearcher.search(query, count, new Sort(
					new SortedNumericSortField(Concept.FieldNames.ACTIVE_SORT, SortField.Type.INT, true),
					new SortedNumericSortField(Concept.FieldNames.PT_WORD_COUNT, SortField.Type.INT),
					new SortField(Concept.FieldNames.PT_STORED, SortField.Type.STRING)
			), false, false);

			List<ValueSet.ValueSetExpansionContainsComponent> contains = new ArrayList<>();
			for (ScoreDoc scoreDoc : queryResult.scoreDocs) {
				Concept concept = codeSystemService.getConceptFromDoc(indexSearcher.doc(scoreDoc.doc));
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
			expansion.setTotal((int) queryResult.totalHits);
			expansion.setContains(contains);
			valueSet.setExpansion(expansion);
			return valueSet;
		} else {
			throw getValueSetNotFound();
		}
	}

	@NotNull
	private static FHIRServerResponseException getValueSetNotFound() {
		return exception("Value Set not found.", OperationOutcome.IssueType.NOTFOUND, 404);
	}

	//
//	private String constructSimpleQueryString(String searchTerm) {
//		return (searchTerm.trim().replace(" ", "* ") + "*").replace("**", "*");
//	}
//	private String constructSearchTerm(List<String> tokens) {
//		StringBuilder builder = new StringBuilder();
//		for (String token : tokens) {
//			builder.append(token);
//			builder.append(" ");
//		}
//		return builder.toString().trim();
//	}

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
