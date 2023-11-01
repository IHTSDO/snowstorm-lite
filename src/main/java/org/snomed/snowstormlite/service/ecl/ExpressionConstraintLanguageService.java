package org.snomed.snowstormlite.service.ecl;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.IndexIOProvider;
import org.snomed.snowstormlite.service.QueryHelper;
import org.snomed.snowstormlite.service.ecl.constraint.SConstraint;
import org.snomed.snowstormlite.service.ecl.constraint.SSubExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRHelper.exception;

@Service
public class ExpressionConstraintLanguageService {

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
			SConstraint constraint = (SConstraint) eclQueryBuilder.createQuery(ecl);
			return constraint.addQuery(new BooleanQuery.Builder(), this);
		} catch (ECLException eclException) {
			throw exception(format("ECL syntax error. %s", eclException.getMessage()), OperationOutcome.IssueType.INVARIANT, 400);
		}
	}

	public FHIRConcept getConcept(String conceptId) throws IOException {
		return codeSystemRepository.getConcept(conceptId);
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
}
