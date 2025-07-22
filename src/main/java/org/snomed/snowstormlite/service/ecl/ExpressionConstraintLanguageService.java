package org.snomed.snowstormlite.service.ecl;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.filter.HistoryProfile;
import org.snomed.langauges.ecl.domain.filter.HistorySupplement;
import org.snomed.snowstormlite.domain.Concepts;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.IndexIOProvider;
import org.snomed.snowstormlite.service.QueryHelper;
import org.snomed.snowstormlite.service.ecl.constraint.SConstraint;
import org.snomed.snowstormlite.service.ecl.constraint.SSubExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRHelper.exception;

@Service
public class ExpressionConstraintLanguageService {

	public static final Set<String> HISTORY_PROFILE_MIN = Collections.singleton(Concepts.REFSET_SAME_AS_ASSOCIATION);
	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private IndexIOProvider indexIOProvider;

	private final ECLQueryBuilder eclQueryBuilder;

	public ExpressionConstraintLanguageService() {
		eclQueryBuilder = new ECLQueryBuilder(new SECLObjectFactory());
	}

	public BooleanQuery.Builder getEclConstraints(String ecl) throws IOException {
		try {
			SConstraint constraint = getEclConstraintRaw(ecl);
			return constraint.addQuery(new BooleanQuery.Builder(), this);
		} catch (ECLException eclException) {
			throw exception(format("ECL syntax error. %s", eclException.getMessage()), OperationOutcome.IssueType.INVARIANT, 400);
		}
	}

	public SConstraint getEclConstraintRaw(String ecl) {
		return (SConstraint) eclQueryBuilder.createQuery(ecl);
	}

	public FHIRConcept getConcept(String conceptId) throws IOException {
		return codeSystemRepository.getConcept(conceptId);
	}

	public Set<String> extractFromConcepts(Collection<String> conceptIds, Function<FHIRConcept, Set<String>> mappingExtractor) throws IOException {
		return codeSystemRepository.extractFromConcepts(conceptIds, mappingExtractor);
	}

	public Set<Long> getConceptIds(SConstraint expressionConstraint) throws IOException {
		if (expressionConstraint instanceof SSubExpressionConstraint subExpressionConstraint) {
			if (subExpressionConstraint.isSingleConcept()) {
				HashSet<Long> longs = new HashSet<>();
				longs.add(Long.parseLong(subExpressionConstraint.getConceptId()));
				return longs;
			}
		}

		BooleanQuery.Builder builder = expressionConstraint.addQuery(new BooleanQuery.Builder(), this);
		BooleanQuery booleanQuery = new BooleanQuery.Builder()
				.add(new TermQuery(new Term(QueryHelper.TYPE, FHIRConcept.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(builder.build(), BooleanClause.Occur.MUST)
				.build();
		try {
			Set<Long> codes = new LongOpenHashSet();
			IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
			StoredFields storedFields = indexSearcher.getIndexReader().storedFields();
			TopDocs queryResult = indexSearcher.search(booleanQuery, Integer.MAX_VALUE);
			for (ScoreDoc scoreDoc : queryResult.scoreDocs) {
				Long conceptId = codeSystemRepository.getConceptIdFromDoc(storedFields.document(scoreDoc.doc));
				codes.add(conceptId);
			}
			return codes;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Set<String> getHistoricAssociationTypes(HistorySupplement historySupplement) throws IOException {
		Set<String> associations;
		SConstraint expressionConstraint = null;
		if (historySupplement.getHistorySubset() != null) {
			expressionConstraint = (SConstraint) historySupplement.getHistorySubset();
		} else if (historySupplement.getHistoryProfile() == null || historySupplement.getHistoryProfile() == HistoryProfile.MAX) {
			expressionConstraint = getEclConstraintRaw("< 900000000000522004 |Historical association reference set|");
		}
		if (expressionConstraint != null) {
			associations = getConceptIds(expressionConstraint).stream().map(Object::toString).collect(Collectors.toSet());
		} else {
			if (historySupplement.getHistoryProfile() == HistoryProfile.MIN) {
				associations = HISTORY_PROFILE_MIN;
			} else {
				associations = Set.of(
						Concepts.REFSET_SAME_AS_ASSOCIATION,
						Concepts.REFSET_REPLACED_BY_ASSOCIATION,
						Concepts.REFSET_WAS_A_ASSOCIATION,
						Concepts.REFSET_PARTIALLY_EQUIVALENT_TO_ASSOCIATION);
			}
		}
		return associations;
	}
}
