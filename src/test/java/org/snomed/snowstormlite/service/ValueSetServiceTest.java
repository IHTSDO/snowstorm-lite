package org.snomed.snowstormlite.service;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.ValueSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.TestService;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ValueSetServiceTest {

	@Autowired
	private ValueSetService valueSetService;

	@Autowired
	private FhirContext fhirContext;

	@Autowired
	private TestService testService;

	@Test
	void createOrUpdateValueset() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		ValueSet valueSetA = fhirContext.newJsonParser().parseResource(ValueSet.class, "{\"resourceType\":\"ValueSet\",\"id\":\"bahmni-A\"," +
				"\"url\":\"http://bahmni.org/fhir/ValueSet/bahmni-A\",\"version\":\"1\",\"name\":\"bahmni-procedures-fluoroscopy\"," +
				"\"title\":\"first\",\"description\":\"List of possible procedures on the bahmni-procedures-fluoroscopy\",\"status\":\"draft\"," +
				"\"experimental\":true,\"compose\":{\"include\":[{\"system\":\"http://snomed.info/sct\",\"concept\":[{\"code\":\"20516002\"}]}]}}");
		ValueSet valueSetB = fhirContext.newJsonParser().parseResource(ValueSet.class, "{\"resourceType\":\"ValueSet\",\"id\":\"bahmni-B\"," +
				"\"url\":\"http://bahmni.org/fhir/ValueSet/bahmni-B\",\"version\":\"1\",\"name\":\"bahmni-procedures-fluoroscopy\"," +
				"\"title\":\"first\",\"description\":\"List of possible procedures on the bahmni-procedures-fluoroscopy\",\"status\":\"draft\"," +
				"\"experimental\":true,\"compose\":{\"include\":[{\"system\":\"http://snomed.info/sct\",\"concept\":[{\"code\":\"20516002\"}]}]}}");
		ValueSet valueSetC = fhirContext.newJsonParser().parseResource(ValueSet.class, "{\"resourceType\":\"ValueSet\",\"id\":\"bahmni-C\"," +
				"\"url\":\"http://bahmni.org/fhir/ValueSet/bahmni-C\",\"version\":\"1\",\"name\":\"bahmni-procedures-fluoroscopy\"," +
				"\"title\":\"first\",\"description\":\"List of possible procedures on the bahmni-procedures-fluoroscopy\",\"status\":\"draft\"," +
				"\"experimental\":true,\"compose\":{\"include\":[{\"system\":\"http://snomed.info/sct\",\"concept\":[{\"code\":\"20516002\"}]}]}}");

		// Start with none
		assertEquals(0, valueSetService.findAll().size());

		// Create
		valueSetService.createOrUpdateValueset(valueSetA);
		valueSetService.createOrUpdateValueset(valueSetB);
		valueSetService.createOrUpdateValueset(valueSetC);
		List<FHIRValueSet> created = valueSetService.findAll();
		assertEquals(3, created.size());
		assertEquals("first", created.get(0).getTitle());
		assertEquals("first", created.get(1).getTitle());
		assertEquals("first", created.get(2).getTitle());

		// Update
		valueSetA.setTitle("second");
		valueSetB.setTitle("second");
		valueSetC.setTitle("second");
		valueSetService.createOrUpdateValueset(valueSetA);
		valueSetService.createOrUpdateValueset(valueSetB);
		valueSetService.createOrUpdateValueset(valueSetC);
		List<FHIRValueSet> updated = valueSetService.findAll();
		assertEquals(3, updated.size());
		assertEquals("second", updated.get(0).getTitle());
		assertEquals("second", updated.get(1).getTitle());
		assertEquals("second", updated.get(2).getTitle());
	}

	@AfterEach
	public void after() throws IOException {
		testService.tearDown();
	}

}