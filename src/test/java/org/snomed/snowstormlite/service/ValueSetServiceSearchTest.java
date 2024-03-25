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

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ValueSetServiceSearchTest {

	@Autowired
	private ValueSetService valueSetService;

	@Autowired
	private TestService testService;

	@Test
	void testFilter() throws IOException, ReleaseImportException {
		testService.importRF2SE();

		// English search, normal characters
		assertEquals("[363698007|Finding site, 404684003|Clinical finding, 313005|Déjà vu]",
				expandWithFilter("find", List.of(new LanguageDialect("en"))));

		// English search, accented characters in search string should match accented characters in content
		assertEquals("[313005|Déjà vu]",
				expandWithFilter("Déjà", List.of(new LanguageDialect("en"))));

		// English search, folded characters in search string should also match accented characters in content
		assertEquals("[313005|Déjà vu]",
				expandWithFilter("deja", List.of(new LanguageDialect("en"))));

		// sv - incision i mellanöra
		// Swedish search, character ö should not be folded
		// Searching without ö character should not match the description
		assertEquals("[]",
				expandWithFilter("mellanora", List.of(new LanguageDialect("sv"))));
		// Searching with ö character should match the description
		assertEquals("[12481008|incision i mellanöra]",
				expandWithFilter("mellanöra", List.of(new LanguageDialect("sv"))));

	}

	private String expandWithFilter(String termFilter, List<LanguageDialect> displayLanguages) throws IOException {
		ValueSet expand = valueSetService.expand(FHIRConstants.IMPLICIT_EVERYTHING, termFilter, displayLanguages, false, 0, 10);
		return expand.getExpansion().getContains().stream().map(comp -> comp.getCode() + "|" + comp.getDisplay()).toList().toString();
	}

	@AfterEach
	public void after() throws IOException {
		testService.tearDown();
	}

}