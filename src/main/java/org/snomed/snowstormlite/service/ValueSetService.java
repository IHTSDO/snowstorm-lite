package org.snomed.snowstormlite.service;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
import org.snomed.snowstormlite.domain.Concept;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.snomed.snowstormlite.fhir.FHIRServerResponseException;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static org.snomed.snowstormlite.fhir.FHIRHelper.exception;

@Service
public class ValueSetService {

	public static final String TYPE = "_type";

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private ExpressionConstraintLanguageService eclService;

	private IndexSearcher indexSearcher;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ValueSet expand(String url, String termFilter, int offset, int count) throws IOException {
		String snomedVS = FHIRConstants.SNOMED_URI + "?fhir_vs";
		if (url.startsWith(snomedVS)) {
			String type = url.replace(snomedVS, "");

			String ancestor = null;
			String ecl = null;
			if (type.startsWith("=isa/")) {
				ancestor = type.replace("=isa/", "");
			} else if (type.startsWith("=ecl/")) {
				ecl = URLDecoder.decode(type.replace("=ecl/", ""), StandardCharsets.UTF_8);
			} else if (!type.isEmpty()) {
				throw getValueSetNotFound();
			}

			BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
			queryBuilder.add(new TermQuery(new Term(TYPE, Concept.DOC_TYPE)), BooleanClause.Occur.MUST);
			if (termFilter != null && !termFilter.isBlank()) {
				addTermQuery(queryBuilder, Concept.FieldNames.TERM, termFilter);
			}
			if (ancestor != null) {
				BooleanQuery.Builder ancestorQueryBuilder = new BooleanQuery.Builder();
				ancestorQueryBuilder.add(new TermQuery(new Term(Concept.FieldNames.ID, ancestor)), BooleanClause.Occur.SHOULD);
				ancestorQueryBuilder.add(new TermQuery(new Term(Concept.FieldNames.ANCESTORS, ancestor)), BooleanClause.Occur.SHOULD);
				queryBuilder.add(ancestorQueryBuilder.build(), BooleanClause.Occur.MUST);
			} else if (ecl != null) {
				Function<BooleanQuery, Set<Long>> eclRunner = booleanClauses -> {
					BooleanQuery booleanQuery = new BooleanQuery.Builder()
							.add(new TermQuery(new Term(TYPE, Concept.DOC_TYPE)), BooleanClause.Occur.MUST)
							.add(booleanClauses, BooleanClause.Occur.MUST)
							.build();
					try {
						Set<Long> codes = new LongOpenHashSet();
						TopDocs queryResult = indexSearcher.search(booleanQuery, Integer.MAX_VALUE);
						for (ScoreDoc scoreDoc : queryResult.scoreDocs) {
							Long conceptId = codeSystemRepository.getConceptIdFromDoc(indexSearcher.doc(scoreDoc.doc));
							codes.add(conceptId);
						}
						return codes;
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				};
				BooleanQuery.Builder eclQueryBuilder = eclService.getEclConstraints(ecl, eclRunner);
				if (eclQueryBuilder != null) {
					queryBuilder.add(eclQueryBuilder.build(), BooleanClause.Occur.MUST);
				}
			}
			BooleanQuery query = queryBuilder.build();
			Sort sort = new Sort(
					new SortedNumericSortField(Concept.FieldNames.ACTIVE_SORT, SortField.Type.INT, true),
					new SortedNumericSortField(Concept.FieldNames.PT_AND_FSN_TERM_LENGTH, SortField.Type.INT),
					SortField.FIELD_SCORE);
			TopDocs queryResult = indexSearcher.search(query, offset + count, sort, true);

			List<ValueSet.ValueSetExpansionContainsComponent> contains = new ArrayList<>();
			int offsetReached = 0;

			List<Concept> conceptPage = new ArrayList<>();
			for (ScoreDoc scoreDoc : queryResult.scoreDocs) {
				if (offsetReached < offset) {
					offsetReached++;
					continue;
				}
				Concept concept = codeSystemRepository.getConceptFromDoc(indexSearcher.doc(scoreDoc.doc));
				conceptPage.add(concept);
				if (conceptPage.size() == count) {
					break;
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

	private void addTermQuery(BooleanQuery.Builder queryBuilder, String fieldName, String termFilter) {
		boolean fuzzy = termFilter.lastIndexOf("~") == termFilter.length() - 1;
		if (fuzzy) {
			termFilter = termFilter.substring(0, termFilter.length() - 1);
		}

		List<String> searchTokens = analyze(termFilter);
		for (String searchToken : searchTokens) {
			if (fuzzy) {
				queryBuilder.add(new FuzzyQuery(new Term(fieldName, searchToken + "~")), BooleanClause.Occur.MUST);
			} else {
				queryBuilder.add(new WildcardQuery(new Term(fieldName, searchToken + "*")), BooleanClause.Occur.MUST);
			}
		}
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
