package org.snomed.snowstormmicro;

import org.hl7.fhir.r4.model.ValueSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstormmicro.service.AppSetupService;
import org.snomed.snowstormmicro.service.ValueSetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ECLTest {

	@Autowired
	private AppSetupService appSetupService;

	@Autowired
	private ValueSetService valueSetService;

	private boolean setupComplete;

	@Test
	void testECLWildcard() throws IOException {
		assertEquals(17, valueSetService.expand("http://snomed.info/sct?fhir_vs=ecl/*", null, 0, 20).getExpansion().getTotal());
	}

	@Test
	void testSelf() throws IOException {
		assertCodesEqual("[404684003]", getCodes("404684003 |Clinical finding|").toString());
	}

	@Test
	void testDescendants() throws IOException {
		assertCodesEqual("[362969004]", getCodes("< 404684003 |Clinical finding|").toString());
	}

	@Test
	void testDescendantsOrSelf() throws IOException {
		assertCodesEqual("[404684003, 362969004]", getCodes("<< 404684003 |Clinical finding|").toString());
	}

	@Test
	void testChildOf() throws IOException {
		assertCodesEqual("[362969004]", getCodes("<! 404684003 |Clinical finding|").toString());
	}

	@Test
	void testChildOrSelf() throws IOException {
		assertCodesEqual("[404684003, 362969004]", getCodes("<<! 404684003 |Clinical finding|").toString());
	}

	@Test
	void testAncestors() throws IOException {
		assertCodesEqual("[138875005, 404684003]", getCodes("> 362969004").toString());
	}

	@Test
	void testAncestorsOrSelf() throws IOException {
		assertCodesEqual("[138875005, 362969004, 404684003]", getCodes(">> 362969004").toString());
	}

	@Test
	void testParents() throws IOException {
		assertCodesEqual("[404684003]", getCodes(">! 362969004").toString());
	}

	@Test
	void testParentsOrSelf() throws IOException {
		assertCodesEqual("[362969004, 404684003]", getCodes(">>! 362969004").toString());
	}

	@Test
	void testAnd() throws IOException {
		assertCodesEqual("[138875005]", getCodes(">> 900000000000441003 AND >> 362969004").toString());
	}

	@Test
	void testOr() throws IOException {
		assertCodesEqual("[138875005, 362969004, 404684003, 900000000000441003]", getCodes(">> 900000000000441003 OR >> 362969004").toString());
	}

	@Test
	void testMinus() throws IOException {
		assertCodesEqual("[900000000000441003]", getCodes(">> 900000000000441003 MINUS >> 362969004").toString());
		assertCodesEqual("[362969004, 404684003]", getCodes(">> 362969004 MINUS >> 900000000000441003").toString());
	}

	@Test
	void testECLFeatureNotSupportedError() throws IOException {
		try {
			valueSetService.expand("http://snomed.info/sct?fhir_vs=ecl/* {{ C definitionStatus = primitive }}", null, 0, 20);
			fail();
		} catch (IllegalArgumentException e) {
			assertEquals("The 'Concept filter' ECL feature is not supported by this implementation.", e.getMessage());
		}
	}

	@BeforeEach
	public void importRF2AndRestart() throws IOException, ReleaseImportException {
		if (!setupComplete) {
			String pathname = "src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2/Snapshot";
			File zipFile = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines(pathname);
			appSetupService.setLoadReleaseArchives(zipFile.getAbsolutePath());
			appSetupService.setLoadVersionUri("http://snomed.info/sct/900000000000207008/version/20200731");
			appSetupService.run();
		}
		setupComplete = true;

		appSetupService.setLoadReleaseArchives(null);
		appSetupService.run();
	}

	private List<String> getCodes(String ecl) throws IOException {
		ValueSet.ValueSetExpansionComponent expansion = valueSetService.expand("http://snomed.info/sct?fhir_vs=ecl/" + ecl, null, 0, 100).getExpansion();
		return expansion.getContains().stream().map(ValueSet.ValueSetExpansionContainsComponent::getCode).collect(Collectors.toList());
	}

	private void assertCodesEqual(String setOne, String setTwo) {
		setOne = sort(setOne);
		setTwo = sort(setTwo);
		assertEquals(setOne, setTwo);
	}

	private static String sort(String setOne) {
		setOne = setOne.substring(1);
		setOne = setOne.substring(0, setOne.length() - 1);
		String[] codes = setOne.split(",");
		return Arrays.stream(codes).map(String::trim).sorted().collect(Collectors.toList()).toString();
	}



}
