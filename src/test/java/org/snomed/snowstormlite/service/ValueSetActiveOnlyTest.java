package org.snomed.snowstormlite.service;

import org.hl7.fhir.r4.model.ValueSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.TestService;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSet;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.snowstormlite.TestService.EN_LANGUAGE_DIALECTS;

/**
 * Inactive concept handling in $expand, aligned with Snowstorm.
 * Test data: inactive 75521003 has the synonym "Zebra finding"; active 281615006 has the longer synonym "Zebra exploration action".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ValueSetActiveOnlyTest {

	private static final String INACTIVE = "75521003";
	private static final String ACTIVE = "281615006";

	@Autowired
	private ValueSetService valueSetService;

	@Autowired
	private TestService testService;

	@BeforeEach
	void setup() throws IOException, ReleaseImportException {
		testService.importRF2Int();
	}

	@Test
	void wildcardExcludesInactiveConcepts() throws IOException {
		assertEquals("[" + ACTIVE + "]", codes(valueSetService.expand(FHIRConstants.IMPLICIT_EVERYTHING, "zebra", EN_LANGUAGE_DIALECTS, false, 0, 10)));
		assertEquals("[" + ACTIVE + "]", codes(valueSetService.expand("http://snomed.info/sct?fhir_vs=ecl/*", "zebra", EN_LANGUAGE_DIALECTS, false, 0, 10)));
	}

	@Test
	void inactiveConceptsSortAfterActiveOnes() throws IOException {
		// The inactive concept has the shorter matching term, but active concepts come first
		assertEquals("[" + ACTIVE + ", " + INACTIVE + "]", codes(expand(codeListValueSet(null), null)));
	}

	@Test
	void activeOnlyParameterExcludesInactiveConcepts() throws IOException {
		assertEquals("[" + ACTIVE + "]", codes(expand(codeListValueSet(null), true)));
		assertEquals("[" + ACTIVE + ", " + INACTIVE + "]", codes(expand(codeListValueSet(null), false)));
	}

	@Test
	void composeInactiveFalseExcludesInactiveConcepts() throws IOException {
		assertEquals("[" + ACTIVE + "]", codes(expand(codeListValueSet(false), null)));
		// activeOnly can still remove inactive concepts that the ValueSet includes
		assertEquals("[" + ACTIVE + "]", codes(expand(codeListValueSet(true), true)));
		assertEquals("[" + ACTIVE + ", " + INACTIVE + "]", codes(expand(codeListValueSet(true), null)));
	}

	private ValueSet expand(ValueSet valueSet, Boolean activeOnly) throws IOException {
		return valueSetService.expand(new FHIRValueSet(valueSet), "zebra", EN_LANGUAGE_DIALECTS, false,
				Collections.emptyList(), activeOnly, 0, 10, null).getFirst();
	}

	private static ValueSet codeListValueSet(Boolean composeInactive) {
		ValueSet valueSet = new ValueSet();
		valueSet.setUrl("http://example.com/fhir/ValueSet/zebra");
		ValueSet.ConceptSetComponent include = valueSet.getCompose().addInclude().setSystem("http://snomed.info/sct");
		include.addConcept().setCode(INACTIVE);
		include.addConcept().setCode(ACTIVE);
		if (composeInactive != null) {
			valueSet.getCompose().setInactive(composeInactive);
		}
		return valueSet;
	}

	private static String codes(ValueSet valueSet) {
		return valueSet.getExpansion().getContains().stream().map(ValueSet.ValueSetExpansionContainsComponent::getCode).toList().toString();
	}

	@AfterEach
	void after() throws IOException {
		testService.tearDown();
	}
}
