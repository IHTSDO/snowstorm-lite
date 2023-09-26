package org.snomed.snowstormlite.service.ecl.constraint;

import org.apache.lucene.search.BooleanQuery;
import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;

public class SSubRefinement extends SubRefinement implements SConstraint {

	@Override
	public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		if (getEclRefinement() != null) {
			return getEclRefinement().addQuery(builder, eclService);
		} else {
			return getEclAttributeSet().addQuery(builder, eclService);
		}
	}

	@Override
	public SEclRefinement getEclRefinement() {
		return (SEclRefinement) super.getEclRefinement();
	}

	@Override
	public SEclAttributeSet getEclAttributeSet() {
		return (SEclAttributeSet) super.getEclAttributeSet();
	}
}
