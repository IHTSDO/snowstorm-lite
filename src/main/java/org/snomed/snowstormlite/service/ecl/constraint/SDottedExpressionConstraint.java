package org.snomed.snowstormlite.service.ecl.constraint;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.snomed.langauges.ecl.domain.expressionconstraint.DottedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.FHIRRelationship;
import org.snomed.snowstormlite.service.QueryHelper;
import org.snomed.snowstormlite.service.ecl.ECLResultProvider;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SDottedExpressionConstraint extends DottedExpressionConstraint implements SConstraint {

	private final ECLResultProvider eclResultProvider;

	public SDottedExpressionConstraint(SubExpressionConstraint subExpressionConstraint, ECLResultProvider eclResultProvider) {
		super(subExpressionConstraint);
		this.eclResultProvider = eclResultProvider;
	}

	@Override
	public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {

		if (getSubExpressionConstraint().isWildcard()) {
			throw new UnsupportedOperationException("Dotted expression using wildcard focus concept is not supported.");
		}

		Set<Long> conceptIds = eclResultProvider.getConceptIds((SConstraint) getSubExpressionConstraint());

		Set<Long> allRelationshipTargets = new HashSet<>();
		for (SubExpressionConstraint dottedAttribute : dottedAttributes) {
			SSubExpressionConstraint aDottedAttribute = (SSubExpressionConstraint) dottedAttribute;
			Set<Long> attributeTypeIds = new HashSet<>(eclResultProvider.getConceptIds(aDottedAttribute));

			Function<FHIRConcept, Set<Long>> relationshipFilterTargetExtractor = concept -> {
				Set<Long> relationshipTargets = new HashSet<>();
				for (Set<FHIRRelationship> relationshipGroup : concept.getRelationships().values()) {
					for (FHIRRelationship fhirRelationship : relationshipGroup) {
						if (attributeTypeIds.contains(fhirRelationship.getType())) {
							relationshipTargets.add(fhirRelationship.getTarget());
						}
					}
				}
				return relationshipTargets;
			};

			Set<String> idStrings = conceptIds.stream().map(Object::toString).collect(Collectors.toSet());
			allRelationshipTargets = eclResultProvider.extractFromConcepts(idStrings, relationshipFilterTargetExtractor);
		}

		Set<String> allRelationshipTargetStrings = allRelationshipTargets.stream().map(Objects::toString).collect(Collectors.toSet());
		builder.add(QueryHelper.termsQuery(FHIRConcept.FieldNames.ID, allRelationshipTargetStrings), BooleanClause.Occur.MUST);

		return builder;
	}
}
