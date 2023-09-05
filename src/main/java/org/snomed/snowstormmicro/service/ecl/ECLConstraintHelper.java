package org.snomed.snowstormmicro.service.ecl;

import static java.lang.String.format;

public class ECLConstraintHelper {

	public static void throwEclFeatureNotSupported(String name) {
		throw new IllegalArgumentException(format("The '%s' ECL feature is not supported by this implementation.", name));
	}

}
