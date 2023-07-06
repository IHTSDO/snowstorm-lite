package org.snomed.snowstormmicro;

import org.hl7.fhir.r4.model.ValueSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstormmicro.domain.CodeSystem;
import org.snomed.snowstormmicro.loading.ImportService;
import org.snomed.snowstormmicro.service.AppSetupService;
import org.snomed.snowstormmicro.service.CodeSystemService;
import org.snomed.snowstormmicro.service.ValueSetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class IntegrationTest {

	@Autowired
	private AppSetupService appSetupService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ValueSetService valueSetService;

	@Test
	void test() throws IOException, ReleaseImportException {
		String pathname = "src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2/Snapshot";
		File zipFile = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines(pathname);
		appSetupService.setLoadReleaseArchives(zipFile.getAbsolutePath());
		appSetupService.run();

		appSetupService.setLoadReleaseArchives(null);
		appSetupService.run();

		CodeSystem codeSystem = codeSystemService.getCodeSystem();
		assertNotNull(codeSystem);
		assertEquals("20200731", codeSystem.getVersionDate());

		ValueSet expand = valueSetService.expand("http://snomed.info/sct?fhir_vs", "", 0, 20);
		assertEquals(14, expand.getExpansion().getTotal());
		for (ValueSet.ValueSetExpansionContainsComponent component : expand.getExpansion().getContains()) {
			System.out.println("display: " + component.getDisplay());
		}
		ValueSet.ValueSetExpansionContainsComponent component = expand.getExpansion().getContains().get(0);
		assertEquals("Clinical finding", component.getDisplay());
	}

}
