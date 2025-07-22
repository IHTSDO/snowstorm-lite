package org.snomed.snowstormlite.service;

import org.hl7.fhir.r4.model.Parameters;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.TestService;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

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
	void testSubsumesOperation() throws IOException, ReleaseImportException {
		// Import test data
		testService.importRF2Int();

		// Get the code system
		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		assertNotNull(codeSystem, "Code system should be available");

		// Test case 1: Same code subsumes itself
		Parameters result1 = codeSystemService.subsumes(codeSystem, "404684003", "404684003");
		assertNotNull(result1);
		assertEquals("subsumes", getParameterValue(result1, "outcome"));
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
		assertEquals("not-subsumes", getParameterValue(result5, "outcome"));
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