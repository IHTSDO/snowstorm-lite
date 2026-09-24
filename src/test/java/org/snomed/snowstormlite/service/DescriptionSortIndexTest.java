package org.snomed.snowstormlite.service;

import org.hl7.fhir.r4.model.ValueSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.TestService;
import org.snomed.snowstormlite.fhir.FHIRConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
