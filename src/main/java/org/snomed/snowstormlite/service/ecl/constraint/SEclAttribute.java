package org.snomed.snowstormlite.service.ecl.constraint;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.snomed.langauges.ecl.domain.refinement.EclAttribute;
import org.snomed.snowstormlite.fhir.FHIRHelper;
import org.snomed.snowstormlite.service.QueryHelper;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import static org.snomed.snowstormlite.service.ecl.ECLConstraintHelper.throwEclFeatureNotSupported;

public class SEclAttribute extends EclAttribute implements SConstraint {

	@Override
	public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		if (cardinalityMin != 1 || cardinalityMax != null) {
			throwEclFeatureNotSupported("Attribute cardinality");
		}
		if (reverse) {
			throwEclFeatureNotSupported("Reverse flag");
		}

		SSubExpressionConstraint attributeName = (SSubExpressionConstraint) getAttributeName();
		Set<? extends Serializable> attributeTypes;
		if (attributeName.isWildcard()) {
			attributeTypes = Collections.singleton("any");
		} else {
			attributeTypes = eclService.getConceptIds(attributeName);
		}

		if (getExpressionComparisonOperator() == null) {
			throw FHIRHelper.exceptionNotSupported("ECL comparison operators other than the expression comparison operator are supported by this implementation.");
		}
		boolean equals = getExpressionComparisonOperator().equals("=");
		SSubExpressionConstraint value = (SSubExpressionConstraint) getValue();
		Set<Long> valueIds = null;
		if (!value.isWildcard()) {
			valueIds = eclService.getConceptIds(value);
		}
		BooleanQuery.Builder disjunctionBuilder = new BooleanQuery.Builder();
		for (Serializable attributeType : attributeTypes) {
			if (value.isWildcard()) {
				disjunctionBuilder.add(new FieldExistsQuery("at_" + attributeType), BooleanClause.Occur.SHOULD);
			} else {
				disjunctionBuilder.add(QueryHelper.termsQueryFromLongs("at_" + attributeType, valueIds), BooleanClause.Occur.SHOULD);
			}
		}
		builder.add(disjunctionBuilder.build(), equals ? BooleanClause.Occur.MUST : BooleanClause.Occur.MUST_NOT);

		return builder;
	}
}
