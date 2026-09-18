package org.snomed.snowstormlite.service.ecl.constraint;

import org.apache.lucene.search.BooleanQuery;
import org.snomed.langauges.ecl.domain.expressionconstraint.RefinedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;
import org.snomed.snowstormlite.service.ecl.deserializer.ECLModelDeserializer;

import java.io.IOException;

public class SRefinedExpressionConstraint extends RefinedExpressionConstraint implements SConstraint, EclExpressionConstraint {

	@SuppressWarnings("unused")
	private SRefinedExpressionConstraint() {
		super(null, null);
	}

	public SRefinedExpressionConstraint(SubExpressionConstraint subExpressionConstraint, EclRefinement eclRefinement) {
		super(subExpressionConstraint, eclRefinement);
	}

	@Override
	public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		getSubexpressionConstraint().addQuery(builder, eclService);
		getEclRefinement().addQuery(builder, eclService);
		return builder;
	}

	@Override
	public SSubExpressionConstraint getSubexpressionConstraint() {
		return (SSubExpressionConstraint) super.getSubexpressionConstraint();
	}

	@Override
	public SEclRefinement getEclRefinement() {
		return (SEclRefinement) super.getEclRefinement();
	}

	@Override
	public String toEclString() {
		return toString(new StringBuilder()).toString();
	}

	public StringBuilder toString(StringBuilder buffer) {
		ECLModelDeserializer.expressionConstraintToString(subexpressionConstraint, buffer);
		buffer.append(" : ");
		ECLModelDeserializer.refinementToString(getEclRefinement(), buffer);
		return buffer;
	}
}
