package org.snomed.snowstormlite.service.ecl.filter;

import org.snomed.langauges.ecl.domain.ConceptReference;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.snowstormlite.service.ecl.constraint.SSubExpressionConstraint;

import java.util.List;

public class ECLToStringUtil {

	private ECLToStringUtil() {
	}

	public static void toString(StringBuffer buffer, ConceptReference conceptReference) {
		buffer.append(conceptReference.getConceptId());
		if (conceptReference.getTerm() != null) {
			buffer.append(" |").append(conceptReference.getTerm()).append("|");
		}
	}

	public static void toString(StringBuffer buffer, List<ConceptReference> conceptReferences) {
		if (conceptReferences != null) {
			buffer.append(" ");
			if (conceptReferences.size() > 1) {
				buffer.append("(");
			}
			int i = 0;
			for (ConceptReference conceptReference : conceptReferences) {
				if (i++ > 0) {
					buffer.append(" ");
				}
				toString(buffer, conceptReference);
			}
			if (conceptReferences.size() > 1) {
				buffer.append(")");
			}
		}
	}

	public static void toString(StringBuffer buffer, SubExpressionConstraint subExpressionConstraint) {
		if (subExpressionConstraint != null) {
			buffer.append(" ");
			((SSubExpressionConstraint) subExpressionConstraint).toString(buffer);
		}
	}

}
