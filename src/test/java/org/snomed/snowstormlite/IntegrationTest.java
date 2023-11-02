package org.snomed.snowstormlite;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.ValueSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.domain.FHIRMapping;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSet;
import org.snomed.snowstormlite.fhir.ConceptMapProvider;
import org.snomed.snowstormlite.service.AppSetupService;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.IndexIOProvider;
import org.snomed.snowstormlite.service.ValueSetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

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

	@Autowired
	private IndexIOProvider indexIOProvider;

	@Test
	void testImportExpand() throws IOException, ReleaseImportException {
		importRF2();

		FHIRCodeSystem codeSystem = codeSystemRepository.getCodeSystem();
		assertNotNull(codeSystem);
		assertEquals("20200731", codeSystem.getVersionDate());

		ValueSet expandAll = valueSetService.expand("http://snomed.info/sct?fhir_vs", null, false, 0, 20);
		assertEquals(20, expandAll.getExpansion().getTotal());


		ValueSet expandFind = valueSetService.expand("http://snomed.info/sct?fhir_vs", "find", false, 0, 20);
		assertEquals(2, expandFind.getExpansion().getTotal());
		for (ValueSet.ValueSetExpansionContainsComponent component : expandFind.getExpansion().getContains()) {
			System.out.println("code: " + component.getCode());
			System.out.println("display: " + component.getDisplay());
		}
		assertFalse(expandFind.getExpansion().getContains().isEmpty());
		ValueSet.ValueSetExpansionContainsComponent findingSite = expandFind.getExpansion().getContains().get(0);
		assertEquals("Finding site", findingSite.getDisplay());

		FHIRConcept concept = codeSystemRepository.getConcept("12481008");
		List<FHIRMapping> mappings = concept.getMappings();
		assertEquals(3, mappings.size());
		mappings.sort(Comparator.comparing(FHIRMapping::getMessage));
		FHIRMapping mapping = mappings.get(0);
		assertEquals("Please observe the following map advice. Group:1, Priority:1, " +
				"Rule:IFA 445518008 | Age at onset of clinical finding (observable entity) | >= 12.0 years " +
				"AND IFA 445518008 | Age at onset of clinical finding (observable entity) | < 19.0 years, " +
				"Advice:'IF AGE AT ONSET OF CLINICAL FINDING ON OR AFTER 12.0 YEARS AND IF AGE AT ONSET OF " +
				"CLINICAL FINDING BEFORE 19.0 YEARS CHOOSE Z00.3 | MAP OF SOURCE CONCEPT IS CONTEXT DEPENDENT', " +
				"Map Category:'447639009'.", mapping.getMessage());
	}

	@Test
	void testImportAddValueSet() throws ReleaseImportException, IOException {
		importRF2();
		ValueSet set = new ValueSet();
		set.setUrl("test");
		set.setVersion("1");
		set.setName("my name");
		set.setTitle("my title");
		set.setStatus(Enumerations.PublicationStatus.ACTIVE);
		set.setExperimental(true);
		set.setDescription("my description");
		set.setCompose(new ValueSet.ValueSetComposeComponent()
				.addInclude(new ValueSet.ConceptSetComponent()
						.setSystem("http://snomed.info/sct")
						.addConcept(new ValueSet.ConceptReferenceComponent(new CodeType("100")))
						.addConcept(new ValueSet.ConceptReferenceComponent(new CodeType("200")))
						.addConcept(new ValueSet.ConceptReferenceComponent(new CodeType("300")))
				));
		valueSetService.createOrUpdateValueset(set);

		List<FHIRValueSet> all = valueSetService.findAll();
		assertEquals(1, all.size());
		FHIRValueSet v1Internal = valueSetService.find("test", "1");
		assertNotNull(v1Internal);
		ValueSet v1 = v1Internal.toHapi();
		assertEquals("test", v1.getUrl());
		assertEquals("1", v1.getVersion());
		assertEquals("my name", v1.getName());
		assertEquals("my title", v1.getTitle());
		assertEquals(Enumerations.PublicationStatus.ACTIVE, v1.getStatus());
		assertEquals("my description", v1.getDescription());
		assertEquals("[100, 200, 300]", v1.getCompose().getInclude().get(0).getConcept().stream().map(ValueSet.ConceptReferenceComponent::getCode).toList().toString());

		assertNull(valueSetService.find("test", "2"));

		ValueSet set2 = new ValueSet();
		set2.setUrl("test");
		set2.setVersion("2");
		valueSetService.createOrUpdateValueset(set2);

		all = valueSetService.findAll();
		assertEquals(2, all.size());
		assertNotNull(valueSetService.find("test", "1"));
		assertNotNull(valueSetService.find("test", "2"));
	}

	private void importRF2() throws IOException, ReleaseImportException {
		String pathname = "src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2/Snapshot";
		File zipFile = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines(pathname);
		appSetupService.setLoadReleaseArchives(zipFile.getAbsolutePath());
		appSetupService.setLoadVersionUri("http://snomed.info/sct/900000000000207008/version/20200731");
		appSetupService.run();
	}

	@AfterEach
	public void after() throws IOException {
		indexIOProvider.deleteDocuments(new MatchAllDocsQuery());
	}

}
