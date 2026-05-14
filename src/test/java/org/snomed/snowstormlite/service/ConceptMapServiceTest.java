package org.snomed.snowstormlite.service;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.ConceptMap;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.TestService;
import org.snomed.snowstormlite.domain.conceptmap.FHIRConceptMap;
import org.snomed.snowstormlite.domain.conceptmap.FHIRMapElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ConceptMapServiceTest {

	@Autowired
	private ConceptMapService conceptMapService;

	@Autowired
	private FhirContext fhirContext;

	@Autowired
	private TestService testService;

	@Test
	void createOrUpdateAndFindConceptMap() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		ConceptMap cm = fhirContext.newJsonParser().parseResource(ConceptMap.class, """
				{"resourceType":"ConceptMap","id":"cm-test-1","url":"http://example.org/fhir/ConceptMap/test","version":"1",
				"name":"TestMap","title":"Test","status":"draft","group":[{
					"source":"http://loinc.org","target":"http://snomed.info/sct",
					"element":[{"code":"12345","target":[{"code":"20516002","equivalence":"equivalent"}]}]
				}]}""");

		assertEquals(0, conceptMapService.findAllStored().size());
		conceptMapService.createOrUpdateConceptMap(cm);
		List<FHIRConceptMap> all = conceptMapService.findAllStored();
		assertEquals(1, all.size());
		assertEquals("cm-test-1", all.get(0).getId());
		assertEquals("http://example.org/fhir/ConceptMap/test", all.get(0).getUrl());

		FHIRConceptMap byId = conceptMapService.findById("cm-test-1");
		assertNotNull(byId);
		List<FHIRMapElement> elements = conceptMapService.findMapElements(byId,
				new org.hl7.fhir.r4.model.Coding("http://loinc.org", "12345", null), "http://snomed.info/sct");
		assertEquals(1, elements.size());
		assertEquals("20516002", elements.get(0).getTarget().get(0).getCode());
	}

	@Test
	void findMapsForTranslateFiltersByUrl() throws IOException, ReleaseImportException {
		testService.importRF2Int();
		ConceptMap cm = fhirContext.newJsonParser().parseResource(ConceptMap.class, """
				{"resourceType":"ConceptMap","url":"http://example.org/fhir/ConceptMap/x","version":"1","group":[{
					"source":"http://loinc.org","target":"http://snomed.info/sct",
					"element":[{"code":"99","target":[{"code":"20516002"}]}]
				}]}""");
		conceptMapService.createOrUpdateConceptMap(cm);

		var coding = new org.hl7.fhir.r4.model.Coding("http://loinc.org", "99", null);
		assertEquals(1, conceptMapService.findMapsForTranslate("http://example.org/fhir/ConceptMap/x", coding, "http://snomed.info/sct").size());
		assertTrue(conceptMapService.findMapsForTranslate("http://other.org/cm", coding, "http://snomed.info/sct").isEmpty());
	}

	@AfterEach
	void after() throws IOException {
		testService.tearDown();
	}
}
