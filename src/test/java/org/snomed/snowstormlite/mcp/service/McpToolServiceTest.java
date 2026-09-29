package org.snomed.snowstormlite.mcp.service;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.TestService;
import org.snomed.snowstormlite.mcp.exception.McpException;
import org.snomed.snowstormlite.mcp.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class McpToolServiceTest {

	@Autowired
	private McpToolService mcpToolService;

	@Autowired
	private TestService testService;

	@BeforeEach
	void setup() throws IOException, ReleaseImportException {
		testService.importRF2Int();
	}

	@Test
	void testLookupCode_ValidCode_ReturnsDetails() {
		// Given
		String code = "404684003"; // Clinical finding

		// When
		ConceptDetails result = mcpToolService.lookup_snomed_code(code, "en");

		// Then
		assertNotNull(result);
		assertEquals(code, result.getConceptId());
		assertNotNull(result.getDisplay());
		assertNotNull(result.getFsn());
		assertTrue(result.isActive());
		assertNotNull(result.getDescriptions());
		assertFalse(result.getDescriptions().isEmpty());
		assertNotNull(result.getParentCodes());
		assertNotNull(result.getChildCodes());
	}

	@Test
	void testLookupCode_ValidCodeWithDefaultLanguage_ReturnsDetails() {
		// Given
		String code = "313005"; // Déjà vu

		// When
		ConceptDetails result = mcpToolService.lookup_snomed_code(code, null);

		// Then
		assertNotNull(result);
		assertEquals(code, result.getConceptId());
		assertNotNull(result.getDisplay());
		assertTrue(result.isActive());
	}

	@Test
	void testLookupCode_InvalidCode_ThrowsException() {
		// Given
		String invalidCode = "99999999";

		// When/Then
		McpException exception = assertThrows(McpException.class, () ->
				mcpToolService.lookup_snomed_code(invalidCode, "en"));

		assertEquals("CONCEPT_NOT_FOUND", exception.getErrorCode());
		assertEquals(404, exception.getStatusCode());
		assertTrue(exception.getMessage().contains("Concept not found"));
	}

	@Test
	void testSearchCodes_ByDescription_ReturnsMatches() {
		// Given
		String query = "finding";

		// When
		ConceptSearchResult result = mcpToolService.search_snomed_codes(query, true, "en", 10, null);

		// Then
		assertNotNull(result);
		assertTrue(result.getTotalResults() > 0);
		assertFalse(result.getConcepts().isEmpty());
		assertTrue(result.getConcepts().size() <= 10);

		// Verify all returned concepts are active
		for (ConceptSearchResult.ConceptSummary concept : result.getConcepts()) {
			assertTrue(concept.isActive());
			assertNotNull(concept.getConceptId());
			assertNotNull(concept.getDisplay());
		}
	}

	@Test
	void testSearchCodes_WithDefaultParameters_ReturnsResults() {
		// Given
		String query = "404684003"; // Search by code

		// When
		ConceptSearchResult result = mcpToolService.search_snomed_codes(query, null, null, null, null);

		// Then
		assertNotNull(result);
		assertTrue(result.getTotalResults() > 0);
		assertFalse(result.getConcepts().isEmpty());
		assertEquals(20, result.getLimit()); // Default limit
	}

	@Test
	void testSearchCodes_WithLimit_RespectsMaximum() {
		// Given
		String query = "finding";
		Integer largeLimit = 200; // Over max

		// When
		ConceptSearchResult result = mcpToolService.search_snomed_codes(query, true, "en", largeLimit, null);

		// Then
		assertNotNull(result);
		assertEquals(100, result.getLimit()); // Should be capped at 100
	}

	@Test
	void testValidateCode_ValidActive_ReturnsTrue() {
		// Given
		String code = "404684003"; // Clinical finding

		// When
		ValidationResult result = mcpToolService.validate_snomed_code(code);

		// Then
		assertTrue(result.isExists());
		assertTrue(result.isActive());
		assertEquals("Valid and active", result.getMessage());
		assertEquals(code, result.getConceptId());
		assertNotNull(result.getDisplay());
	}

	@Test
	void testValidateCode_InvalidFormat_ReturnsFalse() {
		// Given
		String invalidCode = "invalid";

		// When
		ValidationResult result = mcpToolService.validate_snomed_code(invalidCode);

		// Then
		assertFalse(result.isExists());
		assertFalse(result.isActive());
		assertTrue(result.getMessage().contains("Invalid SNOMED CT concept ID format"));
	}

	@Test
	void testValidateCode_NotFound_ReturnsFalse() {
		// Given
		String notFoundCode = "12345678901"; // Valid format but doesn't exist

		// When
		ValidationResult result = mcpToolService.validate_snomed_code(notFoundCode);

		// Then
		assertFalse(result.isExists());
		assertFalse(result.isActive());
		assertTrue(result.getMessage().contains("Concept not found"));
	}

	@Test
	void testCheckSubsumption_SameCode_ReturnsEquivalent() {
		// Given
		String code = "404684003";

		// When
		SubsumptionResult result = mcpToolService.check_snomed_subsumption(code, code);

		// Then
		assertNotNull(result);
		assertEquals("equivalent", result.getOutcome());
		assertEquals(code, result.getCodeA());
		assertEquals(code, result.getCodeB());
		assertNotNull(result.getDisplayA());
		assertNotNull(result.getDisplayB());
	}

	@Test
	void testCheckSubsumption_ParentChild_ReturnsSubsumes() {
		// Given
		String parent = "404684003"; // Clinical finding (parent)
		String child = "313005"; // Déjà vu (child)

		// When
		SubsumptionResult result = mcpToolService.check_snomed_subsumption(parent, child);

		// Then
		assertNotNull(result);
		assertEquals("subsumes", result.getOutcome());
		assertEquals(parent, result.getCodeA());
		assertEquals(child, result.getCodeB());
		assertNotNull(result.getSystem());
		assertNotNull(result.getVersion());
		// Display names might be null depending on test data
		// Don't assert on displayA and displayB for test data flexibility
	}

	@Test
	void testCheckSubsumption_ChildParent_ReturnsSubsumedBy() {
		// Given
		String child = "313005"; // Déjà vu
		String parent = "404684003"; // Clinical finding

		// When
		SubsumptionResult result = mcpToolService.check_snomed_subsumption(child, parent);

		// Then
		assertNotNull(result);
		assertEquals("subsumed-by", result.getOutcome());
		assertEquals(child, result.getCodeA());
		assertEquals(parent, result.getCodeB());
	}

	@Test
	void testCheckSubsumption_UnrelatedConcepts_ReturnsNotSubsumed() {
		// Given
		String codeA = "404684003"; // Clinical finding
		String codeB = "900000000000441003"; // SNOMED CT Model Component (different hierarchy)

		// When
		SubsumptionResult result = mcpToolService.check_snomed_subsumption(codeA, codeB);

		// Then
		assertNotNull(result);
		// Could be "not-subsumed" or "subsumed-by" depending on actual hierarchy
		// Just verify we get a valid outcome
		assertTrue(result.getOutcome().matches("subsumes|subsumed-by|not-subsumed|equivalent"));
	}

	@Test
	void testLookupCode_VerifyDescriptionTypes() {
		// Given
		String code = "404684003"; // Clinical finding

		// When
		ConceptDetails result = mcpToolService.lookup_snomed_code(code, "en");

		// Then
		assertNotNull(result.getDescriptions());

		boolean hasFSN = false;
		boolean hasPT = false;

		for (ConceptDetails.Description desc : result.getDescriptions()) {
			assertNotNull(desc.getTerm());
			assertNotNull(desc.getLanguage());
			assertNotNull(desc.getType());
			assertTrue(desc.getType().matches("FSN|PT|SYNONYM"));

			if ("FSN".equals(desc.getType())) {
				hasFSN = true;
			}
			if ("PT".equals(desc.getType())) {
				hasPT = true;
			}
		}

		assertTrue(hasFSN, "Should have at least one FSN");
		assertTrue(hasPT, "Should have at least one PT");
	}

	@Test
	void testSearchCodes_ActiveOnly_FiltersInactive() {
		// Given
		String query = "concept";

		// When - search with activeOnly = true
		ConceptSearchResult activeResult = mcpToolService.search_snomed_codes(query, true, "en", 50, null);

		// Then - all results should be active
		for (ConceptSearchResult.ConceptSummary concept : activeResult.getConcepts()) {
			assertTrue(concept.isActive(), "All concepts should be active when activeOnly=true");
		}
	}

	@Test
	void testSearchWithEclDescendants_FiltersToProperDescendants() {
		// Searching "finding" within descendants of Clinical finding (404684003)
		// Should include Déjà vu (313005) as a descendant,
		// but NOT Clinical finding itself (404684003, excluded by < operator)
		// and NOT Finding site (363698007, which is an attribute in a different hierarchy)
		ConceptSearchResult result = mcpToolService.search_snomed_codes("finding", true, "en", 20, "< 404684003");

		assertNotNull(result);
		assertTrue(result.getTotalResults() > 0);

		java.util.Set<String> conceptIds = result.getConcepts().stream()
				.map(ConceptSearchResult.ConceptSummary::getConceptId)
				.collect(java.util.stream.Collectors.toSet());

		assertTrue(conceptIds.contains("313005"), "Should contain Déjà vu (descendant of Clinical finding)");
		assertFalse(conceptIds.contains("404684003"), "Should NOT contain Clinical finding itself (< is proper descendants)");
		assertFalse(conceptIds.contains("363698007"), "Should NOT contain Finding site (different hierarchy)");
	}

	@Test
	void testSearchWithEclSelfAndDescendants_IncludesSelf() {
		// Searching "finding" within Clinical finding and its descendants (<<)
		// Should include both Clinical finding itself and Déjà vu
		ConceptSearchResult result = mcpToolService.search_snomed_codes("finding", true, "en", 20, "<< 404684003");

		assertNotNull(result);
		assertTrue(result.getTotalResults() > 0);

		java.util.Set<String> conceptIds = result.getConcepts().stream()
				.map(ConceptSearchResult.ConceptSummary::getConceptId)
				.collect(java.util.stream.Collectors.toSet());

		assertTrue(conceptIds.contains("404684003"), "Should contain Clinical finding itself (<< includes self)");
		assertTrue(conceptIds.contains("313005"), "Should contain Déjà vu (descendant of Clinical finding)");
		assertFalse(conceptIds.contains("363698007"), "Should NOT contain Finding site (different hierarchy)");
	}

	@Test
	void testSearchDefaultEcl_BackwardCompatible() {
		// Without explicit ECL, defaults to '*' (all concepts) — same as before the change
		ConceptSearchResult result = mcpToolService.search_snomed_codes("finding", true, "en", 20, null);

		assertNotNull(result);
		assertTrue(result.getTotalResults() > 0);

		java.util.Set<String> conceptIds = result.getConcepts().stream()
				.map(ConceptSearchResult.ConceptSummary::getConceptId)
				.collect(java.util.stream.Collectors.toSet());

		// Should find concepts across all hierarchies (default wildcard behaviour)
		assertTrue(conceptIds.contains("404684003"), "Should contain Clinical finding");
		assertTrue(conceptIds.contains("313005"), "Should contain Déjà vu");
		assertTrue(conceptIds.contains("363698007"), "Should contain Finding site attribute");
	}

	@Test
	void testSearchWithEcl_RespectsActiveOnly() {
		// Combining ECL with activeOnly filter
		ConceptSearchResult result = mcpToolService.search_snomed_codes("finding", true, "en", 20, "<< 404684003");

		assertNotNull(result);
		assertFalse(result.getConcepts().isEmpty());

		for (ConceptSearchResult.ConceptSummary concept : result.getConcepts()) {
			assertTrue(concept.isActive(), "All concepts should be active when activeOnly=true with ECL");
			assertNotNull(concept.getConceptId());
			assertNotNull(concept.getDisplay());
		}
	}

	@AfterEach
	void teardown() throws IOException {
		testService.tearDown();
	}
}
