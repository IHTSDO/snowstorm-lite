package org.snomed.snowstormmicro.service.ecl;

import org.snomed.langauges.ecl.ECLObjectFactory;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.Operator;

public class SECLObjectFactory extends ECLObjectFactory {

	@Override
	protected SubExpressionConstraint getSubExpressionConstraint(Operator operator) {
		return new SSubExpressionConstraint(operator);
	}
}
