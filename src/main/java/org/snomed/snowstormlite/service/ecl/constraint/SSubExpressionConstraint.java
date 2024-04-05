package org.snomed.snowstormlite.service.ecl.constraint;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.ConceptFilterConstraint;
import org.snomed.langauges.ecl.domain.filter.DescriptionFilterConstraint;
import org.snomed.langauges.ecl.domain.filter.HistorySupplement;
import org.snomed.langauges.ecl.domain.filter.MemberFilterConstraint;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.FHIRMapping;
import org.snomed.snowstormlite.service.QueryHelper;
import org.snomed.snowstormlite.service.ecl.ECLConstraintHelper;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.snomed.snowstormlite.service.QueryHelper.*;
import static org.snomed.snowstormlite.service.ecl.ECLConstraintHelper.throwEclFeatureNotSupported;

public class SSubExpressionConstraint extends SubExpressionConstraint implements SConstraint {

	public SSubExpressionConstraint(Operator operator) {
		super(operator);
	}

	@Override
	public BooleanQuery.Builder addQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		// Check features supported
		if (getMemberFieldsToReturn() != null || isReturnAllMemberFields()) {
			ECLConstraintHelper.throwEclFeatureNotSupported("Member fields");
		}

		HistorySupplement historySupplement = getHistorySupplement();
		if (historySupplement != null) {
			// Fetching required
			if (wildcard) {
				ECLConstraintHelper.throwEclFeatureNotSupported("Wildcard with history supplements");
			}
			SSubExpressionConstraint clone = cloneWithoutFiltersOrSupplements();
			Set<Long> conceptIds = eclService.getConceptIds(clone);

			Set<String> historicAssociationTypes = eclService.getHistoricAssociationTypes(historySupplement);

			Function<FHIRConcept, Set<String>> mappingExtractor = fhirConcept ->
					fhirConcept.getMappings().stream()
							.filter(mapping -> mapping.isInverse() && historicAssociationTypes.contains(mapping.getRefsetId()))
							.map(FHIRMapping::getCode)
							.collect(Collectors.toSet());

			List<String> conceptIdsStrings = conceptIds.stream().map(Object::toString).collect(Collectors.toList());
			Set<String> inactiveLinkedConcepts = eclService.extractFromConcepts(conceptIdsStrings, mappingExtractor);
			conceptIdsStrings.addAll(inactiveLinkedConcepts);
			builder.add(QueryHelper.termsQuery(FHIRConcept.FieldNames.ID, conceptIdsStrings), BooleanClause.Occur.MUST);
			return builder;
		} else {
			return doAddQuery(builder, eclService);
		}
	}

	private BooleanQuery.Builder doAddQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		if (wildcard) {
			builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
		} else if (conceptId != null) {
			addConstraint(conceptId, builder, eclService);
		} else if (nestedExpressionConstraint != null) {
			((SConstraint) nestedExpressionConstraint).addQuery(builder, eclService);
		}
		return builder;
	}

	private void addConstraint(String conceptId, BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException {
		if (operator == null) {
			builder.add(termQuery(FHIRConcept.FieldNames.ID, conceptId), BooleanClause.Occur.MUST);
		} else {
			switch (operator) {
				case descendantof:
					builder.add(termQuery(FHIRConcept.FieldNames.ANCESTORS, conceptId), BooleanClause.Occur.MUST);
					break;
				case descendantorselfof:
					BooleanQuery.Builder shouldBuilder = new BooleanQuery.Builder();
					shouldBuilder.add(termQuery(FHIRConcept.FieldNames.ID, conceptId), BooleanClause.Occur.SHOULD);
					shouldBuilder.add(termQuery(FHIRConcept.FieldNames.ANCESTORS, conceptId), BooleanClause.Occur.SHOULD);
					builder.add(shouldBuilder.build(), BooleanClause.Occur.MUST);
					break;
				case childof:
					builder.add(termQuery(FHIRConcept.FieldNames.PARENTS, conceptId), BooleanClause.Occur.MUST);
					break;
				case childorselfof:
					BooleanQuery.Builder childShouldClauses = new BooleanQuery.Builder();
					childShouldClauses.add(termQuery(FHIRConcept.FieldNames.ID, conceptId), BooleanClause.Occur.SHOULD);
					childShouldClauses.add(termQuery(FHIRConcept.FieldNames.PARENTS, conceptId), BooleanClause.Occur.SHOULD);
					builder.add(childShouldClauses.build(), BooleanClause.Occur.MUST);
					break;
				case ancestorof:
					FHIRConcept concept = eclService.getConcept(conceptId);
					if (concept != null) {
						builder.add(termsQuery(FHIRConcept.FieldNames.ID, concept.getAncestorCodes()), BooleanClause.Occur.MUST);
					} else {
						forceNoMatch(builder);
					}
					break;
				case ancestororselfof:
					FHIRConcept conceptA = eclService.getConcept(conceptId);
					if (conceptA != null) {
						Set<String> codes = new HashSet<>(conceptA.getAncestorCodes());
						codes.add(conceptId);
						builder.add(termsQuery(FHIRConcept.FieldNames.ID, codes), BooleanClause.Occur.MUST);
					} else {
						forceNoMatch(builder);
					}
					break;
				case parentof:
					FHIRConcept conceptB = eclService.getConcept(conceptId);
					if (conceptB != null) {
						builder.add(termsQuery(FHIRConcept.FieldNames.ID, conceptB.getParentCodes()), BooleanClause.Occur.MUST);
					} else {
						forceNoMatch(builder);
					}
					break;
				case parentorselfof:
					FHIRConcept conceptC = eclService.getConcept(conceptId);
					if (conceptC != null) {
						Set<String> parentCodes = new HashSet<>(conceptC.getParentCodes());
						parentCodes.add(conceptId);
						builder.add(termsQuery(FHIRConcept.FieldNames.ID, parentCodes), BooleanClause.Occur.MUST);
					} else {
						forceNoMatch(builder);
					}
					break;
				case memberOf:
					builder.add(termQuery(FHIRConcept.FieldNames.MEMBERSHIP, conceptId), BooleanClause.Occur.MUST);
					break;
			}
		}
	}

	public boolean isSingleConcept() {
		return !isWildcard() && operator == null && conceptId != null && getHistorySupplement() == null;
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

	private SSubExpressionConstraint cloneWithoutFiltersOrSupplements() {
		SSubExpressionConstraint clone = new SSubExpressionConstraint(operator);
		clone.setConceptId(conceptId);
		clone.setTerm(term);
		clone.setWildcard(wildcard);
		clone.setNestedExpressionConstraint(nestedExpressionConstraint);
		clone.setMemberFieldsToReturn(getMemberFieldsToReturn());
		clone.setReturnAllMemberFields(isReturnAllMemberFields());
		return clone;
	}

}
