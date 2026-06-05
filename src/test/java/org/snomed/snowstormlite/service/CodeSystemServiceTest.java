package org.snomed.snowstormlite.service;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.TestService;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class CodeSystemServiceTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private TestService testService;

	@Test
	void testValidateCodeWithValidCode() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		assertNotNull(codeSystem);

		Set<Coding> codings = Collections.singleton(new Coding(SNOMED_URI, "404684003", null));
		Parameters result = codeSystemService.validateCode(codeSystem, codings, List.of(), null);

		assertEquals("true", getParameterValue(result, "result"));
		assertEquals("404684003", getParameterValue(result, "code"));
		assertEquals(SNOMED_URI, getParameterValue(result, "system"));
		assertEquals("Clinical finding", getParameterValue(result, "display"));
		assertEquals("false", getParameterValue(result, "inactive"));
	}

	@Test
	void testValidateCodeWithInvalidCode() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		Set<Coding> codings = Collections.singleton(new Coding(SNOMED_URI, "99999999", null));
		Parameters result = codeSystemService.validateCode(codeSystem, codings, List.of(), null);

		assertEquals("false", getParameterValue(result, "result"));
		assertTrue(getParameterValue(result, "message").contains("99999999"));
	}

	@Test
	void testValidateCodeWithMatchingDisplay() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		Set<Coding> codings = Collections.singleton(new Coding(SNOMED_URI, "313005", "Déjà vu"));
		Parameters result = codeSystemService.validateCode(codeSystem, codings, List.of(new LanguageDialect("en", null)), "en");

		assertEquals("true", getParameterValue(result, "result"));
		assertTrue(getParameterValue(result, "message").contains("display matched"));
	}

	@Test
	void testValidateCodeWithNonMatchingDisplay() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		Set<Coding> codings = Collections.singleton(new Coding(SNOMED_URI, "313005", "Wrong display"));
		Parameters result = codeSystemService.validateCode(codeSystem, codings, List.of(new LanguageDialect("en", null)), "en");

		assertEquals("false", getParameterValue(result, "result"));
		assertTrue(getParameterValue(result, "message").contains("did not match any designations"));
	}

	@Test
	void testValidateCodeWithUnknownSystem() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		Set<Coding> codings = Collections.singleton(new Coding("http://loinc.org", "1000-9", null));
		Parameters result = codeSystemService.validateCode(codeSystem, codings, List.of(), null);

		assertEquals("false", getParameterValue(result, "result"));
		assertTrue(getParameterValue(result, "message").contains("not known"));
	}

	@Test
	void testValidateCodeWithCodeableConcept() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		Set<Coding> codings = new LinkedHashSet<>();
		codings.add(new Coding("http://loinc.org", "1000-9", null));
		codings.add(new Coding(SNOMED_URI, "404684003", null));
		Parameters result = codeSystemService.validateCode(codeSystem, codings, List.of(), null);

		assertEquals("true", getParameterValue(result, "result"));
	}

	@Test
	void testSubsumesOperation() throws IOException, ReleaseImportException {
		// Import test data
		testService.importRF2Int();

		// Get the code system
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		assertNotNull(codeSystem, "Code system should be available");

		// Test case 1: Same code subsumes itself
		Parameters result1 = codeSystemService.subsumes(codeSystem, "404684003", "404684003");
		assertNotNull(result1);
		assertEquals("equivalent", getParameterValue(result1, "outcome"));
		assertEquals("404684003", getParameterValue(result1, "codeA"));
		assertEquals("404684003", getParameterValue(result1, "codeB"));

		// Test case 2: Root concept subsumes child (Clinical finding)
		// This should work because 138875005 is the root and 404684003 is a child
		Parameters result2 = codeSystemService.subsumes(codeSystem, "138875005", "404684003");
		assertNotNull(result2);
		assertEquals("subsumes", getParameterValue(result2, "outcome"));

		// Test case 3: Clinical finding subsumes its child (Déjà vu)
		// This should work because 404684003 is the parent of 313005
		Parameters result3 = codeSystemService.subsumes(codeSystem, "404684003", "313005");
		assertNotNull(result3);
		assertEquals("subsumes", getParameterValue(result3, "outcome"));

		// Test case 4: Root concept subsumes its child (Déjà vu)
		Parameters result4 = codeSystemService.subsumes(codeSystem, "138875005", "313005");
		assertNotNull(result4);
		assertEquals("subsumes", getParameterValue(result4, "outcome"));

		// Test case 5: Child does not subsume parent (Déjà vu does not subsume Clinical finding)
		Parameters result5 = codeSystemService.subsumes(codeSystem, "313005", "404684003");
		assertNotNull(result5);
		assertEquals("subsumed-by", getParameterValue(result5, "outcome"));
	}

	@Test
	void testSubsumesOperationWithNonExistentCodes() throws IOException, ReleaseImportException {
		// Import test data
		testService.importRF2Int();

		// Get the code system
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		assertNotNull(codeSystem, "Code system should be available");

		// Test with non-existent code A
		assertThrows(RuntimeException.class, () -> {
			codeSystemService.subsumes(codeSystem, "99999999", "404684003");
		}, "Should throw exception when code A does not exist");

		// Test with non-existent code B
		assertThrows(RuntimeException.class, () -> {
			codeSystemService.subsumes(codeSystem, "404684003", "99999999");
		}, "Should throw exception when code B does not exist");
	}

	@Test
	void testSubsumesOperationResponseStructure() throws IOException, ReleaseImportException {
		// Import test data
		testService.importRF2Int();

		// Get the code system
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		assertNotNull(codeSystem, "Code system should be available");

		// Test response structure
		Parameters result = codeSystemService.subsumes(codeSystem, "138875005", "404684003");
		assertNotNull(result);
		
		// Check that all required parameters are present
		assertNotNull(getParameter(result, "outcome"));
		assertNotNull(getParameter(result, "codeA"));
		assertNotNull(getParameter(result, "codeB"));
		assertNotNull(getParameter(result, "system"));
		assertNotNull(getParameter(result, "version"));
		
		// Check system and version values
		assertEquals("http://snomed.info/sct", getParameterValue(result, "system"));
		assertTrue(getParameterValue(result, "version").contains("version"));
	}

	private String getParameterValue(Parameters parameters, String name) {
		Parameters.ParametersParameterComponent param = getParameter(parameters, name);
		return param != null ? param.getValue().primitiveValue() : null;
	}

	private Parameters.ParametersParameterComponent getParameter(Parameters parameters, String name) {
		return parameters.getParameter().stream()
				.filter(p -> p.getName().equals(name))
				.findFirst()
				.orElse(null);
	}

	@AfterEach
	public void after() throws IOException {
		testService.tearDown();
	}
} 