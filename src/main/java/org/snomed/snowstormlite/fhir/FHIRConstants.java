package org.snomed.snowstormlite.fhir;

import org.hl7.fhir.r4.model.Coding;
import org.snomed.snowstormlite.domain.Concepts;

public interface FHIRConstants {

	String SNOMED_URI = "http://snomed.info/sct";
	String SNOMED_URI_UNVERSIONED = "http://snomed.info/xsct";

	String IMPLICIT_ISA = "?fhir_vs=isa/";
	String IMPLICIT_DESCENDANT_OF = "?fhir_vs=descendant-of/";
	String IMPLICIT_REFSET = "?fhir_vs=refset/";
	String IMPLICIT_ECL= "?fhir_vs=ecl/";

	Coding SYNONYM_CODING = new Coding(SNOMED_URI, Concepts.SYNONYM, "Synonym");
	Coding FSN_CODING = new Coding(SNOMED_URI, Concepts.FSN, "Fully specified name");
	Coding FOR_DISPLAY_CODING = new Coding(
			"http://terminology.hl7.org/CodeSystem/designation-usage", "display", null);
	Coding PREFERED_FOR_LANGUAGE_CODING = new Coding(
			"http://terminology.hl7.org/CodeSystem/hl7TermMaintInfra", "preferredForLanguage", "Preferred For Language");

	// Copied from https://www.hl7.org/fhir/snomedct.html
	String SNOMED_VALUESET_COPYRIGHT = "This value set includes content from SNOMED CT, which is copyright © 2002+ International Health Terminology Standards Development " +
			"Organisation (SNOMED International), and distributed by agreement between SNOMED International and HL7. Implementer use of SNOMED CT is not covered by this " +
			"agreement.";


}
