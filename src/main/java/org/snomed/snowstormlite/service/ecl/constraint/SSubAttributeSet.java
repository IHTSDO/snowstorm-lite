package org.snomed.snowstormlite.service.ecl.constraint;

import org.apache.lucene.search.BooleanQuery;
import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;

public class SSubAttributeSet extends SubAttributeSet implements SConstraint {

	@Override
	public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		if (getAttribute() != null) {
			((SConstraint)getAttribute()).addQuery(builder, eclService);
		} else {
			((SConstraint)getAttributeSet()).addQuery(builder, eclService);
		}
		return builder;
	}
}
