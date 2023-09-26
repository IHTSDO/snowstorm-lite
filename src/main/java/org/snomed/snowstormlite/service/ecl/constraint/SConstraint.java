package org.snomed.snowstormlite.service.ecl.constraint;

import org.apache.lucene.search.BooleanQuery;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;

public interface SConstraint {

	static BooleanQuery getQuery(SConstraint constraint, ExpressionConstraintLanguageService eclService) throws IOException {
		return constraint.addQuery(new BooleanQuery.Builder(), eclService).build();
	}

	BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException;

}
