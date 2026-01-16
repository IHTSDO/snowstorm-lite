package org.snomed.snowstormlite.mcp.service;

import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.FHIRDescription;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.snomed.snowstormlite.mcp.exception.McpException;
import org.snomed.snowstormlite.mcp.model.*;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.CodeSystemService;
import org.snomed.snowstormlite.service.SnomedIdentifierHelper;
import org.snomed.snowstormlite.service.ValueSetService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP Tool Service providing SNOMED CT terminology operations via the Model Context Protocol.
 * <p>
 * This service exposes four core tools:
 * <ul>
 *   <li>lookup_snomed_code - Retrieve detailed information about a SNOMED CT concept</li>
 *   <li>search_snomed_codes - Search SNOMED CT concepts by code or description</li>
 *   <li>validate_snomed_code - Verify if a SNOMED CT code exists and is active</li>
 *   <li>check_snomed_subsumption - Check hierarchical relationships between concepts</li>
 * </ul>
 */
@Service
public class McpToolService {

	private static final Logger log = LoggerFactory.getLogger(McpToolService.class);

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private ValueSetService valueSetService;

	/**
	 * Retrieves detailed information about a SNOMED CT concept.
	 * <p>
	 * This method returns comprehensive concept details including display names,
	 * descriptions in multiple languages, hierarchical relationships, and metadata.
	 *
	 * @param code The SNOMED CT concept identifier (SCTID), must be a valid numeric ID
	 * @param language Optional language code for localized display names (default: "en").
	 *                 Supported languages include en, es, fr, de, ja, zh, sv, da, etc.
	 * @return ConceptDetails object containing all available information about the concept
	 * @throws McpException if the concept is not found or an error occurs during retrieval
	 */
	@Tool(description = "Retrieve detailed information about a SNOMED CT concept including display name, status, parents, children, relationships, and descriptions")
	public ConceptDetails lookup_snomed_code(String code, String language) {
		try {
			log.info("Looking up SNOMED CT code: {} with language: {}", code, language);

			// Default language
			if (language == null || language.isEmpty()) {
				language = "en";
			}

			// Create language dialect
			List<LanguageDialect> dialects = createLanguageDialects(language);

			// Retrieve concept from repository
			FHIRConcept concept = codeSystemRepository.getConcept(code);

			if (concept == null) {
				throw new McpException(
						"Concept not found: " + code,
						"CONCEPT_NOT_FOUND",
						404
				);
			}

			// Get code system for version info
			FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();

			// Build response
			return ConceptDetails.builder()
					.conceptId(concept.getConceptId())
					.display(concept.getPT(dialects))
					.fsn(getFSN(concept, language))
					.active(concept.isActive())
					.effectiveTime(concept.getEffectiveTime())
					.moduleId(concept.getModuleId())
					.defined(concept.isDefined())
					.descriptions(mapDescriptions(concept.getDescriptions()))
					.parentCodes(concept.getParentCodes())
					.childCodes(concept.getChildCodes())
					.ancestorCodes(concept.getAncestorCodes())
					.normalForm(concept.getNormalFormTerse())
					.build();

		} catch (IOException e) {
			log.error("Failed to retrieve concept: {}", code, e);
			throw new McpException(
					"Failed to retrieve concept: " + e.getMessage(),
					"INTERNAL_ERROR",
					500,
					e
			);
		}
	}

	/**
	 * Search SNOMED CT concepts by code prefix or description text.
	 *
	 * @param query Search query: either a SCTID prefix (e.g., '7321') or description text (e.g., 'diabetes')
	 * @param activeOnly If true, return only active concepts (default: true)
	 * @param language Language code for display names (default: "en")
	 * @param limit Maximum number of results to return (default: 20, max: 100)
	 * @return ConceptSearchResult containing matching concepts with display names and active status
	 * @throws McpException if search fails
	 */
	@Tool(description = "Search SNOMED CT concepts by code prefix or description text. Returns matching concepts with display names and active status")
	public ConceptSearchResult search_snomed_codes(String query, Boolean activeOnly, String language, Integer limit) {
		try {
			log.info("Searching SNOMED CT with query: {}, activeOnly: {}, language: {}, limit: {}",
					query, activeOnly, language, limit);

			// Default parameters
			if (language == null || language.isEmpty()) {
				language = "en";
			}
			if (activeOnly == null) {
				activeOnly = true;
			}
			if (limit == null) {
				limit = 20;
			}
			if (limit > 100) {
				limit = 100;
			}

			List<LanguageDialect> dialects = createLanguageDialects(language);

			// Build implicit ValueSet for search
			// Use ECL: * for all concepts
			String valueSetUrl = "http://snomed.info/sct?fhir_vs=ecl/*";

			// Use ValueSetService to perform search
			ValueSet expanded = valueSetService.expand(
					valueSetUrl,
					query,  // term filter
					dialects,
					false,  // includeDesignations
					0,      // offset
					limit   // count
			);

			// Convert to search result
			List<ConceptSearchResult.ConceptSummary> summaries = new ArrayList<>();
			for (ValueSet.ValueSetExpansionContainsComponent contain : expanded.getExpansion().getContains()) {

				// Filter by active status if requested
				Boolean inactive = contain.getInactive();
				if (activeOnly && inactive != null && inactive.booleanValue()) {
					continue;
				}

				summaries.add(ConceptSearchResult.ConceptSummary.builder()
						.conceptId(contain.getCode())
						.display(contain.getDisplay())
						.active(inactive == null || !inactive.booleanValue())
						.matchedTerm(contain.getDisplay())
						.build());
			}

			return ConceptSearchResult.builder()
					.totalResults((int) expanded.getExpansion().getTotal())
					.limit(limit)
					.concepts(summaries)
					.build();

		} catch (IOException e) {
			log.error("Search failed for query: {}", query, e);
			throw new McpException(
					"Search failed: " + e.getMessage(),
					"SEARCH_ERROR",
					500,
					e
			);
		}
	}

	/**
	 * Verify if a SNOMED CT code exists and check its active status.
	 *
	 * @param code The SNOMED CT concept identifier (SCTID) to validate
	 * @return ValidationResult with existence and active status information
	 * @throws McpException if validation process fails
	 */
	@Tool(description = "Verify if a SNOMED CT code exists and check its active status")
	public ValidationResult validate_snomed_code(String code) {
		try {
			log.info("Validating SNOMED CT code: {}", code);

			// Validate format first
			if (!SnomedIdentifierHelper.isConceptId(code)) {
				return ValidationResult.builder()
						.exists(false)
						.active(false)
						.message("Invalid SNOMED CT concept ID format: " + code)
						.conceptId(code)
						.build();
			}

			// Check if concept exists
			FHIRConcept concept = codeSystemRepository.getConcept(code);

			if (concept == null) {
				return ValidationResult.builder()
						.exists(false)
						.active(false)
						.message("Concept not found: " + code)
						.conceptId(code)
						.build();
			}

			List<LanguageDialect> dialects = createLanguageDialects("en");

			return ValidationResult.builder()
					.exists(true)
					.active(concept.isActive())
					.message(concept.isActive() ? "Valid and active" : "Valid but inactive")
					.conceptId(concept.getConceptId())
					.display(concept.getPT(dialects))
					.build();

		} catch (IOException e) {
			log.error("Validation failed for code: {}", code, e);
			throw new McpException(
					"Validation failed: " + e.getMessage(),
					"VALIDATION_ERROR",
					500,
					e
			);
		}
	}

	/**
	 * Check if one SNOMED CT concept subsumes another (i.e., is an ancestor of).
	 *
	 * @param codeA First SNOMED CT concept identifier
	 * @param codeB Second SNOMED CT concept identifier
	 * @return SubsumptionResult with outcome: "subsumes", "subsumed-by", "equivalent", or "not-subsumed"
	 * @throws McpException if subsumption check fails
	 */
	@Tool(description = "Check if one SNOMED CT concept subsumes another (i.e., is an ancestor of). Returns 'subsumes', 'subsumed-by', 'equivalent', or 'not-subsumed'")
	public SubsumptionResult check_snomed_subsumption(String codeA, String codeB) {
		try {
			log.info("Checking subsumption between codeA: {} and codeB: {}", codeA, codeB);

			FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();

			// Use existing subsumption logic from CodeSystemService
			Parameters result = codeSystemService.subsumes(codeSystem, codeA, codeB);

			// Extract outcome
			String outcome = result.getParameter().stream()
					.filter(p -> "outcome".equals(p.getName()))
					.findFirst()
					.map(p -> p.getValue().primitiveValue())
					.orElse("unknown");

			// Get display names
			List<LanguageDialect> dialects = createLanguageDialects("en");
			FHIRConcept conceptA = codeSystemRepository.getConcept(codeA);
			FHIRConcept conceptB = codeSystemRepository.getConcept(codeB);

			return SubsumptionResult.builder()
					.outcome(outcome)
					.codeA(codeA)
					.codeB(codeB)
					.displayA(conceptA != null ? conceptA.getPT(dialects) : null)
					.displayB(conceptB != null ? conceptB.getPT(dialects) : null)
					.system(FHIRConstants.SNOMED_URI)
					.version(codeSystem.getVersionUri())
					.build();

		} catch (Exception e) {
			log.error("Subsumption check failed for codeA: {} and codeB: {}", codeA, codeB, e);
			throw new McpException(
					"Subsumption check failed: " + e.getMessage(),
					"SUBSUMPTION_ERROR",
					500,
					e
			);
		}
	}

	// Utility methods

	private List<LanguageDialect> createLanguageDialects(String language) {
		List<LanguageDialect> dialects = new ArrayList<>();

		// Add requested language
		dialects.add(new LanguageDialect(language));

		// Add English as fallback if not already present
		if (!"en".equals(language)) {
			dialects.add(new LanguageDialect("en"));
		}

		return dialects;
	}

	private String getFSN(FHIRConcept concept, String language) {
		for (FHIRDescription desc : concept.getDescriptions()) {
			if (desc.isFsn() && desc.getLang().equals(language)) {
				return desc.getTerm();
			}
		}
		// Fallback to any FSN
		for (FHIRDescription desc : concept.getDescriptions()) {
			if (desc.isFsn()) {
				return desc.getTerm();
			}
		}
		return null;
	}

	private List<ConceptDetails.Description> mapDescriptions(List<FHIRDescription> descriptions) {
		return descriptions.stream()
				.map(d -> new ConceptDetails.Description(
						d.getTerm(),
						d.getLang(),
						d.isFsn() ? "FSN" : (!d.getPreferredLangRefsets().isEmpty() ? "PT" : "SYNONYM")
				))
				.collect(Collectors.toList());
	}
}
