package org.snomed.snowstormlite;

import org.hl7.fhir.r4.model.ValueSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstormlite.domain.CodeSystem;
import org.snomed.snowstormlite.service.AppSetupService;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.ValueSetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class IntegrationTest {

	@Autowired
	private AppSetupService appSetupService;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private ValueSetService valueSetService;

	@Test
	void testImportExpand() throws IOException, ReleaseImportException {
		importRF2AndRestart();

		CodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		assertNotNull(codeSystem);
		assertEquals("20200731", codeSystem.getVersionDate());


		ValueSet expandAll = valueSetService.expand("http://snomed.info/sct?fhir_vs", null, 0, 20);
		assertEquals(18, expandAll.getExpansion().getTotal());


		ValueSet expandFind = valueSetService.expand("http://snomed.info/sct?fhir_vs", "find", 0, 20);
		assertEquals(2, expandFind.getExpansion().getTotal());
		for (ValueSet.ValueSetExpansionContainsComponent component : expandFind.getExpansion().getContains()) {
			System.out.println("code: " + component.getCode());
			System.out.println("display: " + component.getDisplay());
		}
		assertFalse(expandFind.getExpansion().getContains().isEmpty());
		ValueSet.ValueSetExpansionContainsComponent findingSite = expandFind.getExpansion().getContains().get(0);
		assertEquals("Finding site", findingSite.getDisplay());
	}

	private void importRF2AndRestart() throws IOException, ReleaseImportException {
		String pathname = "src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2/Snapshot";
		File zipFile = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines(pathname);
		appSetupService.setLoadReleaseArchives(zipFile.getAbsolutePath());
		appSetupService.setLoadVersionUri("http://snomed.info/sct/900000000000207008/version/20200731");
		appSetupService.run();

		appSetupService.setLoadReleaseArchives(null);
		appSetupService.run();
	}

}
