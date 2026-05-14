package org.snomed.snowstormlite.fhir;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstormlite.config.FHIRConceptMapImplicitConfig;
import org.snomed.snowstormlite.domain.conceptmap.FHIRConceptMap;
import org.snomed.snowstormlite.domain.conceptmap.FHIRConceptMapGroup;
import org.snomed.snowstormlite.domain.conceptmap.FHIRMapElement;
import org.snomed.snowstormlite.domain.conceptmap.FHIRMapTarget;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.ConceptMapService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConceptMapProviderTranslateTest {

	@Mock
	private CodeSystemRepository codeSystemRepository;

	@Mock
	private FHIRConceptMapImplicitConfig implicitConfig;

	@Mock
	private ConceptMapService conceptMapService;

	@InjectMocks
	private ConceptMapProvider provider;

	@BeforeEach
	void init() {
		when(implicitConfig.getSnomedCorrelationToFhirEquivalenceMap()).thenReturn(Collections.emptyMap());
		when(implicitConfig.getImplicitMaps()).thenReturn(Collections.emptyList());
		provider.loadCorrelationEquivalence();
	}

	@Test
	void translateUsesInlineConceptMap() throws IOException {
		ConceptMap posted = new ConceptMap();
		posted.setUrl("http://example.org/cm");
		posted.setVersion("1");
		ConceptMap.ConceptMapGroupComponent g = posted.addGroup();
		g.setSource("http://loinc.org");
		g.setTarget("http://snomed.info/sct");
		ConceptMap.SourceElementComponent el = g.addElement();
		el.setCode("100");
		el.addTarget().setCode("20516002").setEquivalence(Enumerations.ConceptMapEquivalence.EQUIVALENT);

		Parameters p = provider.lookupImplicit(
				null, null,
				null,
				posted,
				null,
				"100",
				"http://loinc.org",
				null,
				null,
				null,
				null,
				null,
				null,
				null);

		assertTrue(p.hasParameter("result"));
		assertTrue(p.getParameterBool("result"));
		assertTrue(p.getParameter().stream().anyMatch(param -> "match".equals(param.getName())));
	}

	@Test
	void translateByUrlUsesStoredMap() throws IOException {
		FHIRConceptMapGroup g = new FHIRConceptMapGroup();
		g.setSource("http://loinc.org");
		g.setTarget("http://snomed.info/sct");
		FHIRMapElement el = new FHIRMapElement();
		el.setCode("200");
		el.setTarget(List.of(new FHIRMapTarget()));
		el.getTarget().get(0).setCode("20516002");
		el.getTarget().get(0).setEquivalence(Enumerations.ConceptMapEquivalence.EQUIVALENT.toCode());
		g.setElement(List.of(el));

		FHIRConceptMap stored = new FHIRConceptMap();
		stored.setUrl("http://example.org/stored");
		stored.setGroup(List.of(g));

		when(conceptMapService.findMapsForTranslate(eq("http://example.org/stored"), any(Coding.class), isNull()))
				.thenReturn(List.of(stored));

		Parameters p = provider.lookupImplicit(
				null, null,
				new UriType("http://example.org/stored"),
				null,
				null,
				"200",
				"http://loinc.org",
				null,
				null,
				null,
				null,
				null,
				null,
				null);

		assertTrue(p.getParameterBool("result"));
	}
}
