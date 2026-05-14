package org.snomed.snowstormlite.fhir;

import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstormlite.config.FHIRConceptMapImplicitConfig;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRSnomedImplicitMap;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.ConceptMapService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConceptMapProviderSearchTest {

	@Mock
	private CodeSystemRepository codeSystemRepository;

	@Mock
	private FHIRConceptMapImplicitConfig implicitConfig;

	@Mock
	private ConceptMapService conceptMapService;

	@InjectMocks
	private ConceptMapProvider provider;

	@BeforeEach
	void setupCorrelationEquivalence() {
		when(implicitConfig.getSnomedCorrelationToFhirEquivalenceMap()).thenReturn(Collections.emptyMap());
		provider.loadCorrelationEquivalence();
	}

	@Test
	void searchReturnsEmptyWhenNoCodeSystemLoaded() throws IOException {
		when(codeSystemRepository.getCodeSystem()).thenReturn(null);
		when(conceptMapService.findAllStored()).thenReturn(Collections.emptyList());
		assertTrue(provider.search(null).isEmpty());
	}

	@Test
	void searchListsImplicitMapsWhenCodeSystemLoaded() throws IOException {
		when(codeSystemRepository.getCodeSystem()).thenReturn(new FHIRCodeSystem());
		when(conceptMapService.findAllStored()).thenReturn(Collections.emptyList());
		FHIRSnomedImplicitMap implicitMap = new FHIRSnomedImplicitMap(
				"447562003",
				"SNOMED CT to ICD-10 extended map",
				"http://snomed.info/sct",
				"http://hl7.org/fhir/sid/icd-10",
				null);
		when(implicitConfig.getImplicitMaps()).thenReturn(List.of(implicitMap));

		List<ConceptMap> maps = provider.search(null);
		assertEquals(1, maps.size());
		ConceptMap cm = maps.get(0);
		assertEquals("snomed_implicit_map_447562003", cm.getIdElement().getIdPart());
		assertEquals("http://snomed.info/sct?fhir_cm=447562003", cm.getUrl());
		assertEquals("SNOMED CT to ICD-10 extended map", cm.getName());
		assertEquals("http://snomed.info/sct?fhir_vs", ((UriType) cm.getSource()).getValueAsString());
		assertEquals("http://hl7.org/fhir/sid/icd-10?fhir_vs", ((UriType) cm.getTarget()).getValueAsString());
		assertEquals(Enumerations.PublicationStatus.ACTIVE, cm.getStatus());
		assertTrue(cm.getGroup().isEmpty());
		assertNotNull(cm.getText());
	}

	@Test
	void searchFiltersByUrlWithSnomedVersionNormalization() throws IOException {
		when(codeSystemRepository.getCodeSystem()).thenReturn(new FHIRCodeSystem());
		when(conceptMapService.findAllStored()).thenReturn(Collections.emptyList());
		FHIRSnomedImplicitMap implicitMap = new FHIRSnomedImplicitMap(
				"447562003",
				"SNOMED CT to ICD-10 extended map",
				"http://snomed.info/sct",
				"http://hl7.org/fhir/sid/icd-10",
				null);
		when(implicitConfig.getImplicitMaps()).thenReturn(List.of(implicitMap));

		String versionedUrl = "http://snomed.info/sct/900000000000207008/version/20250101?fhir_cm=447562003";
		assertEquals(1, provider.search(versionedUrl).size());

		assertTrue(provider.search("http://snomed.info/sct?fhir_cm=999").isEmpty());
	}

}
