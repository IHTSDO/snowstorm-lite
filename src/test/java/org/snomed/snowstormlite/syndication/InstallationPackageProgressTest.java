package org.snomed.snowstormlite.syndication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstallationPackageProgressTest {

	@Test
	void combinedImportUsesSharedStartSoInstallPercentMatches() {
		long start = 1_000_000L;
		long estMs = 60_000L;
		InstallationPackageProgress a = new InstallationPackageProgress("a", "A", 100);
		InstallationPackageProgress b = new InstallationPackageProgress("b", "B", 200);
		a.beginImportEstimate(estMs, start);
		b.beginImportEstimate(estMs, start);

		assertEquals(a.getInstallPercent(), b.getInstallPercent());
		assertEquals(InstallationPackageProgress.PHASE_IMPORTING, a.getPhase());
		assertEquals(InstallationPackageProgress.PHASE_IMPORTING, b.getPhase());
	}
}
