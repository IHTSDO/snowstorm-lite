package org.snomed.snowstormlite.service.ecl.constraint;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeSet;
import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;

import static org.snomed.snowstormlite.service.ecl.constraint.SConstraint.getQuery;

public class SEclAttributeSet extends EclAttributeSet implements SConstraint {

	@Override
	public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		if (getDisjunctionAttributeSet() != null) {
			BooleanQuery.Builder disjunctionBuilder = new BooleanQuery.Builder();
			disjunctionBuilder.add(getQuery((SConstraint) getSubAttributeSet(), eclService), BooleanClause.Occur.SHOULD);
			for (SubAttributeSet attributeSet : getDisjunctionAttributeSet()) {
				disjunctionBuilder.add(getQuery((SConstraint) attributeSet, eclService), BooleanClause.Occur.SHOULD);
			}
			builder.add(disjunctionBuilder.build(), BooleanClause.Occur.MUST);
		} else {
			builder.add(getQuery((SConstraint) getSubAttributeSet(), eclService), BooleanClause.Occur.MUST);
			if (getConjunctionAttributeSet() != null) {
				for (SubAttributeSet attributeSet : getConjunctionAttributeSet()) {
					builder.add(getQuery((SConstraint) attributeSet, eclService), BooleanClause.Occur.MUST);
				}
			}
		}
		return builder;
	}
}
