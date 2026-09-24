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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstormlite.TestService.EN_LANGUAGE_DIALECTS;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {"search.description-sort-index.enabled=false", "index.path=target/test-lucene-index-no-description-sort"})
class DescriptionSortIndexDisabledTest {

	@Autowired
	private ValueSetService valueSetService;

	@Autowired
	private DescriptionSortIndex descriptionSortIndex;

	@Autowired
	private TestService testService;

	@Value("${index.path}")
	private String indexPath;

	@Test
	void notBuiltAndSearchUsesTheRelevanceSortWindow() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		assertFalse(descriptionSortIndex.isUsable());
		assertFalse(Files.exists(Paths.get(indexPath, DescriptionSortIndex.DIRECTORY_NAME)));

		ValueSet expand = valueSetService.expand(FHIRConstants.IMPLICIT_EVERYTHING, "find", EN_LANGUAGE_DIALECTS, false, 0, 10);
		assertEquals(3, expand.getExpansion().getTotal());
		assertEquals("Finding site", expand.getExpansion().getContains().get(0).getDisplay());
	}

	@AfterEach
	void after() throws IOException {
		testService.tearDown();
	}
}
