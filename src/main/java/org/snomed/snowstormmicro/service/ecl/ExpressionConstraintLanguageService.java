package org.snomed.snowstormmicro.service.ecl;

import org.apache.lucene.search.BooleanQuery;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.service.CodeSystemRepository;
import org.snomed.snowstormmicro.service.ecl.constraint.SConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Set;
import java.util.function.Function;

import static java.lang.String.format;
import static org.snomed.snowstormmicro.fhir.FHIRHelper.exception;

@Service
public class ExpressionConstraintLanguageService {

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	private final ECLQueryBuilder eclQueryBuilder;

	public ExpressionConstraintLanguageService() {
		eclQueryBuilder = new ECLQueryBuilder(new SECLObjectFactory());
	}

	public BooleanQuery.Builder getEclConstraints(String ecl, Function<BooleanQuery, Set<Long>> eclRunner) throws IOException {
		try {
			SConstraint constraint = (SConstraint) eclQueryBuilder.createQuery(ecl);
			return constraint.getQuery(new BooleanQuery.Builder(), this);
		} catch (ECLException eclException) {
			throw exception(format("ECL syntax error. %s", eclException.getMessage()), OperationOutcome.IssueType.INVARIANT, 400);
		}
	}

	public Concept getConcept(String conceptId) throws IOException {
		return codeSystemRepository.getConcept(conceptId);
	}
}
