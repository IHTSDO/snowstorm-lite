package org.snomed.snowstormlite.service.ecl;

import org.snomed.langauges.ecl.ECLObjectFactory;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstormlite.service.ecl.constraint.SCompoundExpressionConstraint;
import org.snomed.snowstormlite.service.ecl.constraint.SSubExpressionConstraint;

public class SECLObjectFactory extends ECLObjectFactory {

	@Override
	protected SubExpressionConstraint getSubExpressionConstraint(Operator operator) {
		return new SSubExpressionConstraint(operator);
	}

	@Override
	protected CompoundExpressionConstraint getCompoundExpressionConstraint() {
		return new SCompoundExpressionConstraint();
	}
}
