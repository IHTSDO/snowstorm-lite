package org.snomed.snowstormlite.service.ecl;

import static java.lang.String.format;
import static org.snomed.snowstormlite.fhir.FHIRHelper.exceptionNotSupported;

public class ECLConstraintHelper {

	public static void throwEclFeatureNotSupported(String name) {
		throw exceptionNotSupported(format("The '%s' ECL feature is not supported by this implementation.", name));
	}

}
