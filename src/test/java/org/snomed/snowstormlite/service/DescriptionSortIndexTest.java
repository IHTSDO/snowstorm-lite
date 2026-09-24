package org.snomed.snowstormlite.service;

import org.hl7.fhir.r4.model.ValueSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.TestService;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstormlite.TestService.EN_LANGUAGE_DIALECTS;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class DescriptionSortIndexTest {

	@Autowired
	private ValueSetService valueSetService;

	@Autowired
	private DescriptionSortIndex descriptionSortIndex;

	@Autowired
	private TestService testService;

	@Test
	void builtOnImportAndDeletedWithSnomed() throws IOException, ReleaseImportException {
		testService.importRF2Int();
		assertTrue(descriptionSortIndex.isUsable());

		descriptionSortIndex.delete();
		assertFalse(descriptionSortIndex.isUsable());
		// Filtered search still works, using the relevance sort window
		assertEquals(3, expand("find", 0, 10).getExpansion().getTotal());

		descriptionSortIndex.rebuild();
		assertTrue(descriptionSortIndex.isUsable());
	}

	@Test
	void pagingIsStableAndTotalIsExact() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		ValueSet all = expand("a", 0, 100);
		List<String> allCodes = codes(all);
		int total = all.getExpansion().getTotal();
		assertEquals(allCodes.size(), total);
		assertTrue(total > 4, "test needs more than one page");

		List<String> paged = new ArrayList<>();
		for (int offset = 0; offset < total; offset += 3) {
			ValueSet page = expand("a", offset, 3);
			assertEquals(total, page.getExpansion().getTotal());
			paged.addAll(codes(page));
		}
		assertEquals(allCodes, paged);
	}

	@Test
	void largeCandidateSetsGiveTheSameResultsAsTheIdFilter() throws IOException, ReleaseImportException {
		testService.importRF2Int();
		// Descendants of the root: an ECL ValueSet (not the wildcard), so it uses the candidate path
		String rootDescendants = "http://snomed.info/sct?fhir_vs=ecl/<<138875005";
		List<String> withIdFilter = codes(valueSetService.expand(rootDescendants, "a", EN_LANGUAGE_DIALECTS, false, 0, 100));
		assertTrue(withIdFilter.size() > 4, "test needs more than two pages");

		// Force checking membership in batches, as for large candidate sets
		ReflectionTestUtils.setField(valueSetService, "candidateIdFilterLimit", 0);
		try {
			assertEquals(withIdFilter, codes(valueSetService.expand(rootDescendants, "a", EN_LANGUAGE_DIALECTS, false, 0, 100)));
			List<String> paged = new ArrayList<>();
			for (int offset = 0; offset < withIdFilter.size(); offset += 2) {
				paged.addAll(codes(valueSetService.expand(rootDescendants, "a", EN_LANGUAGE_DIALECTS, false, offset, 2)));
			}
			assertEquals(withIdFilter, paged);
		} finally {
			ReflectionTestUtils.setField(valueSetService, "candidateIdFilterLimit", 5_000);
		}
	}

	@Test
	void edgeCases() throws IOException, ReleaseImportException {
		testService.importRF2Int();
		assertTrue(descriptionSortIndex.isUsable());
		int total = expand("a", 0, 100).getExpansion().getTotal();

		// Count only
		ValueSet countOnly = expand("a", 0, 0);
		assertEquals(total, countOnly.getExpansion().getTotal());
		assertTrue(countOnly.getExpansion().getContains().isEmpty());

		// Offset beyond the total
		ValueSet beyond = expand("a", total + 10, 10);
		assertEquals(total, beyond.getExpansion().getTotal());
		assertTrue(beyond.getExpansion().getContains().isEmpty());

		// Fuzzy search: "fnding~" matches "finding"
		assertTrue(codes(expand("fnding~", 0, 10)).contains("404684003"));

		// Concept id filter is an exact lookup
		assertEquals(List.of("404684003"), codes(expand("404684003", 0, 10)));
	}

	@Test
	void languageSpecificFolding() throws IOException, ReleaseImportException {
		testService.importRF2SE();
		assertTrue(descriptionSortIndex.isUsable());
		List<LanguageDialect> swedish = List.of(new LanguageDialect("sv"));
		// Swedish: ö is not folded, so "mellanora" does not match "mellanöra"
		assertEquals(List.of(), codes(valueSetService.expand(FHIRConstants.IMPLICIT_EVERYTHING, "mellanora", swedish, false, 0, 10)));
		assertEquals(List.of("12481008"), codes(valueSetService.expand(FHIRConstants.IMPLICIT_EVERYTHING, "mellanöra", swedish, false, 0, 10)));
		// English: accents are folded, "deja" matches "Déjà vu"
		assertEquals(List.of("313005"), codes(expand("deja", 0, 10)));
	}

	private ValueSet expand(String filter, int offset, int count) throws IOException {
		return valueSetService.expand(FHIRConstants.IMPLICIT_EVERYTHING, filter, EN_LANGUAGE_DIALECTS, false, offset, count);
	}

	private static List<String> codes(ValueSet valueSet) {
		return valueSet.getExpansion().getContains().stream().map(ValueSet.ValueSetExpansionContainsComponent::getCode).toList();
	}

	@AfterEach
	void after() throws IOException {
		testService.tearDown();
	}
}
