package org.snomed.snowstormlite.syndication;

import org.junit.jupiter.api.Test;
import org.snomed.snowstormlite.syndication.client.SyndicationFeedEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyndicationServiceDisplayTitleTest {

	@Test
	void koreaExtensionWithSpacedHyphenBeforeRegion() {
		assertEquals(
				"SNOMED CT Extension - Korea",
				SyndicationService.formatSyndicationTitle("SNOMED CT Extension - Korea-June 2026 v1.0"));
	}

	@Test
	void argentinaEditionWithIsoDateSuffix() {
		assertEquals(
				"SNOMED CT Edición Argentina",
				SyndicationService.formatSyndicationTitle("SNOMED CT Edición Argentina-2019-05-31"));
	}

	@Test
	void netherlandsEditionWithLongSuffix() {
		assertEquals(
				"SNOMED CT Netherlands/Nederlands Edition",
				SyndicationService.formatSyndicationTitle(
						"SNOMED CT Netherlands/Nederlands Edition-SNOMED CT Netherlands Edition March 2026 v1.0"));
	}

	@Test
	void irishExtension() {
		assertEquals(
				"Irish SNOMED CT Extension",
				SyndicationService.formatSyndicationTitle("Irish SNOMED CT Extension-March 2026 v1.0"));
	}

	@Test
	void internationalEdition() {
		assertEquals(
				"SNOMED CT International Edition",
				SyndicationService.formatSyndicationTitle("SNOMED CT International Edition-January 2026 v1.0"));
	}

	@Test
	void koreaDecemberRelease() {
		assertEquals(
				"SNOMED CT Extension - Korea",
				SyndicationService.formatSyndicationTitle("SNOMED CT Extension - Korea-December 2025 v1.0"));
	}

	@Test
	void nullAndEmpty() {
		assertEquals("", SyndicationService.formatSyndicationTitle(null));
		assertEquals("", SyndicationService.formatSyndicationTitle(""));
	}

	@Test
	void feedEntryGetTitleCleanedDelegatesToSameFormatter() {
		SyndicationFeedEntry entry = new SyndicationFeedEntry();
		entry.setTitle("SNOMED CT Extension - Korea-June 2026 v1.0");
		assertEquals("SNOMED CT Extension - Korea", entry.getTitleCleaned());
	}
}
