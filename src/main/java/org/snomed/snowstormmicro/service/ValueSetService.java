package org.snomed.snowstormmicro.service;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.fhir.FHIRConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
					queryBuilder.add(new WildcardQuery(new Term(Concept.FieldNames.TERM, searchToken + "*")), BooleanClause.Occur.MUST);
				}
			}
			BooleanQuery query = queryBuilder.build();
			System.out.println(query.toString());
			TopDocs docs = indexSearcher.search(query, count);
			List<ValueSet.ValueSetExpansionContainsComponent> contains = new ArrayList<>();
			for (ScoreDoc scoreDoc : docs.scoreDocs) {
				Concept concept = codeSystemService.getConceptFromDoc(indexSearcher.doc(scoreDoc.doc));
				contains.add(new ValueSet.ValueSetExpansionContainsComponent()
						.setSystem(FHIRConstants.SNOMED_URI)
						.setCode(concept.getConceptId())
						.setDisplay(concept.getPT())
				);
			}

			ValueSet valueSet = new ValueSet();
			valueSet.setId(UUID.randomUUID().toString());
			valueSet.setUrl(url);
			ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
			expansion.setId(UUID.randomUUID().toString());
			expansion.setTimestamp(new Date());
			expansion.setContains(contains);
			valueSet.setExpansion(expansion);
			return valueSet;
		} else {
			throw exception("Value Set not found.", OperationOutcome.IssueType.NOTFOUND, 404);
		}
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
