package org.snomed.snowstormlite.service.ecl.filter;

import org.snomed.langauges.ecl.domain.filter.HistorySupplement;
import org.snomed.snowstormlite.service.ecl.constraint.EclExpressionConstraint;

public class SHistorySupplement extends HistorySupplement {

	public void toString(StringBuilder buffer) {
		buffer.append(" {{ + HISTORY");

		if (getHistorySubset() != null) {
			buffer.append(" (");
			buffer.append(((EclExpressionConstraint) getHistorySubset()).toEclString());
			buffer.append(")");
		} else if (getHistoryProfile() != null) {
			buffer.append("-").append(getHistoryProfile());
		}

		buffer.append(" }}");
	}

}
