package org.snomed.snowstormlite.service.ecl;

import org.snomed.langauges.ecl.ECLObjectFactory;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.DottedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.RefinedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.ConceptFilterConstraint;
import org.snomed.langauges.ecl.domain.filter.DescriptionFilterConstraint;
import org.snomed.langauges.ecl.domain.filter.HistorySupplement;
import org.snomed.langauges.ecl.domain.filter.MemberFilterConstraint;
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

	@Override
	public HistorySupplement getHistorySupplement() {
		throwEclFeatureNotSupported("History supplement");
		return null;
	}

	@Override
	public ConceptFilterConstraint getConceptFilterConstraint() {
		throwEclFeatureNotSupported("Concept filter");
		return null;
	}

	@Override
	public DescriptionFilterConstraint getDescriptionFilterConstraint() {
		throwEclFeatureNotSupported("Description filter");
		return null;
	}

	@Override
	public MemberFilterConstraint getMemberFilterConstraint() {
		throwEclFeatureNotSupported("Member filter");
		return null;
	}
}
