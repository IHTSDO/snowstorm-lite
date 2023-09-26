package org.snomed.snowstormlite.service.ecl;

import org.snomed.langauges.ecl.ECLObjectFactory;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.DottedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.RefinedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.*;
import org.snomed.snowstormlite.service.ecl.constraint.*;

import static org.snomed.snowstormlite.service.ecl.ECLConstraintHelper.throwEclFeatureNotSupported;

public class SECLObjectFactory extends ECLObjectFactory {

	@Override
	protected SubExpressionConstraint getSubExpressionConstraint(Operator operator) {
		return new SSubExpressionConstraint(operator);
	}

	@Override
	protected CompoundExpressionConstraint getCompoundExpressionConstraint() {
		return new SCompoundExpressionConstraint();
	}

	@Override
	protected RefinedExpressionConstraint getRefinedExpressionConstraint(SubExpressionConstraint subExpressionConstraint, EclRefinement eclRefinement) {
		return new SRefinedExpressionConstraint(subExpressionConstraint, eclRefinement);
	}

	@Override
	protected EclRefinement getRefinement() {
		return new SEclRefinement();
	}

	@Override
	protected SubRefinement getSubRefinement() {
		return new SSubRefinement();
	}

	@Override
	protected EclAttributeSet getEclAttributeSet() {
		return new SEclAttributeSet();
	}

	@Override
	protected SubAttributeSet getSubAttributeSet() {
		return new SSubAttributeSet();
	}

	@Override
	protected EclAttributeGroup getAttributeGroup() {
		throwEclFeatureNotSupported("Attribute group");
		return null;
	}

	@Override
	protected EclAttribute getAttribute() {
		return new SEclAttribute();
	}

	@Override
	protected DottedExpressionConstraint getDottedExpressionConstraint(SubExpressionConstraint subExpressionConstraint) {
		throwEclFeatureNotSupported("Dotted expression constraint");
		return null;
	}
}
