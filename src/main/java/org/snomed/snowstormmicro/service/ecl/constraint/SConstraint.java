package org.snomed.snowstormmicro.service.ecl.constraint;

import org.apache.lucene.search.BooleanQuery;
import org.snomed.snowstormmicro.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;

public interface SConstraint {

	BooleanQuery.Builder getQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException;

}
