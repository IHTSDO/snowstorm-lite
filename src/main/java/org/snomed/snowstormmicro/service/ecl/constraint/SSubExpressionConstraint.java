package org.snomed.snowstormmicro.service.ecl.constraint;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.ConceptFilterConstraint;
import org.snomed.langauges.ecl.domain.filter.DescriptionFilterConstraint;
import org.snomed.langauges.ecl.domain.filter.MemberFilterConstraint;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.snomed.snowstormmicro.service.ecl.ECLConstraintHelper.throwEclFeatureNotSupported;
import static org.snomed.snowstormmicro.service.QueryHelper.*;

public class SSubExpressionConstraint extends SubExpressionConstraint implements SConstraint {

	public SSubExpressionConstraint(Operator operator) {
		super(operator);
	}

	@Override
	public BooleanQuery.Builder getQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		if (wildcard) {
			return null;
		} else if (conceptId != null) {
			if (operator == null) {
				builder.add(termQuery(Concept.FieldNames.ID, conceptId), BooleanClause.Occur.MUST);
			} else {
				switch (operator) {
					case descendantof:
						builder.add(termQuery(Concept.FieldNames.ANCESTORS, conceptId), BooleanClause.Occur.MUST);
						break;
					case descendantorselfof:
						BooleanQuery.Builder shouldBuilder = new BooleanQuery.Builder();
						shouldBuilder.add(termQuery(Concept.FieldNames.ID, conceptId), BooleanClause.Occur.SHOULD);
						shouldBuilder.add(termQuery(Concept.FieldNames.ANCESTORS, conceptId), BooleanClause.Occur.SHOULD);
						builder.add(shouldBuilder.build(), BooleanClause.Occur.MUST);
						break;
					case childof:
						builder.add(termQuery(Concept.FieldNames.PARENTS, conceptId), BooleanClause.Occur.MUST);
						break;
					case childorselfof:
						BooleanQuery.Builder childShouldClauses = new BooleanQuery.Builder();
						childShouldClauses.add(termQuery(Concept.FieldNames.ID, conceptId), BooleanClause.Occur.SHOULD);
						childShouldClauses.add(termQuery(Concept.FieldNames.PARENTS, conceptId), BooleanClause.Occur.SHOULD);
						builder.add(childShouldClauses.build(), BooleanClause.Occur.MUST);
						break;
					case ancestorof:
						Concept concept = eclService.getConcept(conceptId);
						if (concept != null) {
							builder.add(termsQuery(Concept.FieldNames.ID, concept.getAncestorCodes()), BooleanClause.Occur.MUST);
						} else {
							forceNoMatch(builder);
						}
						break;
					case ancestororselfof:
						Concept conceptA = eclService.getConcept(conceptId);
						if (conceptA != null) {
							Set<String> codes = new HashSet<>(conceptA.getAncestorCodes());
							codes.add(conceptId);
							builder.add(termsQuery(Concept.FieldNames.ID, codes), BooleanClause.Occur.MUST);
						} else {
							forceNoMatch(builder);
						}
						break;
					case parentof:
						Concept conceptB = eclService.getConcept(conceptId);
						if (conceptB != null) {
							builder.add(termsQuery(Concept.FieldNames.ID, conceptB.getParentCodes()), BooleanClause.Occur.MUST);
						} else {
							forceNoMatch(builder);
						}
						break;
					case parentorselfof:
						Concept conceptC = eclService.getConcept(conceptId);
						if (conceptC != null) {
							Set<String> parentCodes = new HashSet<>(conceptC.getParentCodes());
							parentCodes.add(conceptId);
							builder.add(termsQuery(Concept.FieldNames.ID, parentCodes), BooleanClause.Occur.MUST);
						} else {
							forceNoMatch(builder);
						}
						break;
					case memberOf:
						builder.add(termQuery(Concept.FieldNames.MEMBERSHIP, conceptId), BooleanClause.Occur.MUST);
						break;
				}
			}
		}
		return builder;
	}

	@Override
	public void addConceptFilterConstraint(ConceptFilterConstraint conceptFilterConstraint) {
		throwEclFeatureNotSupported("Concept filter");
	}

	@Override
	public void addDescriptionFilterConstraint(DescriptionFilterConstraint descriptionFilterConstraint) {
		throwEclFeatureNotSupported("Description filter");
	}

	@Override
	public void addMemberFilterConstraint(MemberFilterConstraint memberFilterConstraint) {
		throwEclFeatureNotSupported("Member filter");
	}
}
