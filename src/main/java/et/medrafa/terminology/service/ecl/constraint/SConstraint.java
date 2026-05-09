package et.medrafa.terminology.service.ecl.constraint;

import org.apache.lucene.search.BooleanQuery;
import et.medrafa.terminology.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;

public interface SConstraint {

	static BooleanQuery getQuery(SConstraint constraint, ExpressionConstraintLanguageService eclService) throws IOException {
		return constraint.addQuery(new BooleanQuery.Builder(), eclService).build();
	}

	BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException;

}
