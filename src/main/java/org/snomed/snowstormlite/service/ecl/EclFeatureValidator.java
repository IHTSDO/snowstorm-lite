package org.snomed.snowstormlite.service.ecl;

import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.DottedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.RefinedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.EclAttribute;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeSet;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.snomed.snowstormlite.service.ecl.ECLConstraintHelper.throwEclFeatureNotSupported;
import static org.snomed.snowstormlite.util.CollectionUtils.orEmpty;

@Component
public class EclFeatureValidator {

	public void validate(ExpressionConstraint expressionConstraint) {
		if (expressionConstraint instanceof SubExpressionConstraint subExpressionConstraint) {
			validateSubExpression(subExpressionConstraint);
		} else if (expressionConstraint instanceof CompoundExpressionConstraint compoundExpressionConstraint) {
			validateCompound(compoundExpressionConstraint);
		} else if (expressionConstraint instanceof RefinedExpressionConstraint refinedExpressionConstraint) {
			validateSubExpression(refinedExpressionConstraint.getSubexpressionConstraint());
			validateRefinement(refinedExpressionConstraint.getEclRefinement());
		} else if (expressionConstraint instanceof DottedExpressionConstraint dottedExpressionConstraint) {
			validateSubExpression(dottedExpressionConstraint.getSubExpressionConstraint());
			for (SubExpressionConstraint dottedAttribute : orEmpty(dottedExpressionConstraint.getDottedAttributes())) {
				validateSubExpression(dottedAttribute);
			}
		}
	}

	private void validateCompound(CompoundExpressionConstraint compoundExpressionConstraint) {
		List<SubExpressionConstraint> conjunctionExpressionConstraints = compoundExpressionConstraint.getConjunctionExpressionConstraints();
		if (conjunctionExpressionConstraints != null) {
			for (SubExpressionConstraint constraint : conjunctionExpressionConstraints) {
				validateSubExpression(constraint);
			}
			return;
		}
		List<SubExpressionConstraint> disjunctionExpressionConstraints = compoundExpressionConstraint.getDisjunctionExpressionConstraints();
		if (disjunctionExpressionConstraints != null) {
			for (SubExpressionConstraint constraint : disjunctionExpressionConstraints) {
				validateSubExpression(constraint);
			}
			return;
		}
		if (compoundExpressionConstraint.getExclusionExpressionConstraints() != null) {
			validateSubExpression(compoundExpressionConstraint.getExclusionExpressionConstraints().getFirst());
			validateSubExpression(compoundExpressionConstraint.getExclusionExpressionConstraints().getSecond());
		}
	}

	private void validateSubExpression(SubExpressionConstraint subExpressionConstraint) {
		if (subExpressionConstraint.getMemberFieldsToReturn() != null || subExpressionConstraint.isReturnAllMemberFields()) {
			throwEclFeatureNotSupported("Member fields");
		}
		if (subExpressionConstraint.getHistorySupplement() != null && subExpressionConstraint.isWildcard()) {
			throwEclFeatureNotSupported("Wildcard with history supplements");
		}
		if (subExpressionConstraint.getConceptFilterConstraints() != null) {
			throwEclFeatureNotSupported("Concept filter");
		}
		if (subExpressionConstraint.getDescriptionFilterConstraints() != null) {
			throwEclFeatureNotSupported("Description filter");
		}
		if (subExpressionConstraint.getMemberFilterConstraints() != null) {
			throwEclFeatureNotSupported("Member filter");
		}
		if (subExpressionConstraint.getNestedExpressionConstraint() != null) {
			validate(subExpressionConstraint.getNestedExpressionConstraint());
		}
	}

	private void validateRefinement(EclRefinement eclRefinement) {
		if (eclRefinement == null) {
			return;
		}
		validateSubRefinement(eclRefinement.getSubRefinement());
		for (SubRefinement conjunctionSubRefinement : orEmpty(eclRefinement.getConjunctionSubRefinements())) {
			validateSubRefinement(conjunctionSubRefinement);
		}
		for (SubRefinement disjunctionSubRefinement : orEmpty(eclRefinement.getDisjunctionSubRefinements())) {
			validateSubRefinement(disjunctionSubRefinement);
		}
	}

	private void validateSubRefinement(SubRefinement subRefinement) {
		if (subRefinement == null) {
			return;
		}
		if (subRefinement.getEclAttributeGroup() != null) {
			throwEclFeatureNotSupported("Attribute group");
		}
		if (subRefinement.getEclAttributeSet() != null) {
			validateAttributeSet(subRefinement.getEclAttributeSet());
		}
		if (subRefinement.getEclRefinement() != null) {
			validateRefinement(subRefinement.getEclRefinement());
		}
	}

	private void validateAttributeSet(EclAttributeSet eclAttributeSet) {
		validateSubAttributeSet(eclAttributeSet.getSubAttributeSet());
		for (SubAttributeSet attributeSet : orEmpty(eclAttributeSet.getConjunctionAttributeSet())) {
			validateSubAttributeSet(attributeSet);
		}
		for (SubAttributeSet attributeSet : orEmpty(eclAttributeSet.getDisjunctionAttributeSet())) {
			validateSubAttributeSet(attributeSet);
		}
	}

	private void validateSubAttributeSet(SubAttributeSet subAttributeSet) {
		if (subAttributeSet == null) {
			return;
		}
		if (subAttributeSet.getAttribute() != null) {
			validateAttribute(subAttributeSet.getAttribute());
		}
		if (subAttributeSet.getAttributeSet() != null) {
			validateAttributeSet(subAttributeSet.getAttributeSet());
		}
	}

	private void validateAttribute(EclAttribute attribute) {
		if (attribute.getCardinalityMin() != 1 || attribute.getCardinalityMax() != null) {
			throwEclFeatureNotSupported("Attribute cardinality");
		}
		if (attribute.isReverse()) {
			throwEclFeatureNotSupported("Reverse flag");
		}
		if (attribute.getNumericComparisonOperator() != null || attribute.getStringComparisonOperator() != null
				|| attribute.getBooleanComparisonOperator() != null) {
			throwEclFeatureNotSupported("Non-expression comparison operator");
		}
		if (attribute.getAttributeName() != null) {
			validate(attribute.getAttributeName());
		}
		if (attribute.getValue() != null) {
			validate(attribute.getValue());
		}
	}

}
