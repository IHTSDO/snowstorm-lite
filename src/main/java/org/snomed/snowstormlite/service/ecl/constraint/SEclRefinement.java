package org.snomed.snowstormlite.service.ecl.constraint;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;
import java.util.List;

import static org.snomed.snowstormlite.service.ecl.constraint.SConstraint.getQuery;

public class SEclRefinement extends EclRefinement implements SConstraint {

	@Override
	public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		if (getDisjunctionSubRefinements() != null) {
			BooleanQuery.Builder disjunctionBuilder = new BooleanQuery.Builder();
			disjunctionBuilder.add(getQuery(getSubRefinement(), eclService), BooleanClause.Occur.SHOULD);
			for (SubRefinement disjunctionSubRefinement : getDisjunctionSubRefinements()) {
				disjunctionBuilder.add(getQuery((SConstraint) disjunctionSubRefinement, eclService), BooleanClause.Occur.SHOULD);
			}
			builder.add(disjunctionBuilder.build(), BooleanClause.Occur.MUST);
		} else {
			builder.add(getQuery(getSubRefinement(), eclService), BooleanClause.Occur.MUST);
			for (SubRefinement conjunctionSubRefinement : getConjunctionSubRefinements()) {
				builder.add(getQuery((SConstraint) conjunctionSubRefinement, eclService), BooleanClause.Occur.MUST);
			}
		}
		return builder;
	}

	@Override
	public SSubRefinement getSubRefinement() {
		return (SSubRefinement) super.getSubRefinement();
	}

	public void toString(StringBuilder buffer) {
		((SSubRefinement) subRefinement).toString(buffer);
		List<SubRefinement> conjunctionSubRefinements = getConjunctionSubRefinements();
		if (conjunctionSubRefinements != null) {
			for (SubRefinement conjunctionSubRefinement : conjunctionSubRefinements) {
				buffer.append(", ");
				((SSubRefinement) conjunctionSubRefinement).toString(buffer);
			}
		}
		List<SubRefinement> disjunctionSubRefinements = getDisjunctionSubRefinements();
		if (disjunctionSubRefinements != null) {
			for (SubRefinement disjunctionSubRefinement : disjunctionSubRefinements) {
				buffer.append(" or ");
				((SSubRefinement) disjunctionSubRefinement).toString(buffer);
			}
		}
	}

}
