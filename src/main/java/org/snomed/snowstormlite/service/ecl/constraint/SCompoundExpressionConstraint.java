package org.snomed.snowstormlite.service.ecl.constraint;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.snomed.langauges.ecl.domain.Pair;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class SCompoundExpressionConstraint extends CompoundExpressionConstraint implements SConstraint {

	@Override
	public BooleanQuery.Builder getQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		if (getConjunctionExpressionConstraints() != null) {
			// All conjunction constraints must be met
			for (SSubExpressionConstraint conjunctionExpressionConstraint : getSConjunctionExpressionConstraints()) {
				builder.add(conjunctionExpressionConstraint.getQuery(new BooleanQuery.Builder(), eclService).build(), BooleanClause.Occur.MUST);
			}
		} else if (getDisjunctionExpressionConstraints() != null) {
			// One or more disjunction constraints must be met
			BooleanQuery.Builder disjunctionShouldClauses = new BooleanQuery.Builder();
			for (SSubExpressionConstraint disjunctionExpressionConstraint : getSDisjunctionExpressionConstraints()) {
				disjunctionShouldClauses.add(disjunctionExpressionConstraint.getQuery(new BooleanQuery.Builder(), eclService).build(), BooleanClause.Occur.SHOULD);
			}
			builder.add(disjunctionShouldClauses.build(), BooleanClause.Occur.MUST);
		} else if (getExclusionExpressionConstraints() != null) {
			// First part of exclusion must be met
			// Second part of exclusion must not be met
			Pair<SubExpressionConstraint> pair = getExclusionExpressionConstraints();
			builder.add(((SSubExpressionConstraint)pair.getFirst()).getQuery(new BooleanQuery.Builder(), eclService).build(), BooleanClause.Occur.MUST);
			builder.add(((SSubExpressionConstraint)pair.getSecond()).getQuery(new BooleanQuery.Builder(), eclService).build(), BooleanClause.Occur.MUST_NOT);
		}
		return builder;
	}

	public List<SSubExpressionConstraint> getSConjunctionExpressionConstraints() {
		if (getConjunctionExpressionConstraints() == null) {
			return null;
		}
		return getConjunctionExpressionConstraints().stream().map(constraint -> (SSubExpressionConstraint) constraint).collect(Collectors.toList());
	}

	public List<SSubExpressionConstraint> getSDisjunctionExpressionConstraints() {
		if (getDisjunctionExpressionConstraints() == null) {
			return null;
		}
		return getDisjunctionExpressionConstraints().stream().map(constraint -> (SSubExpressionConstraint) constraint).collect(Collectors.toList());
	}

}
