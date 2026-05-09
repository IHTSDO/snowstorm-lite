package et.medrafa.terminology.service.ecl;

import static java.lang.String.format;
import static et.medrafa.terminology.fhir.FHIRHelper.exceptionNotSupported;

public class ECLConstraintHelper {

	public static void throwEclFeatureNotSupported(String name) {
		throw exceptionNotSupported(format("The '%s' ECL feature is not supported by this implementation.", name));
	}

}
