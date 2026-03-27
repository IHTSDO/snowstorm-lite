package org.snomed.snowstormlite.service;

import org.hl7.fhir.r4.model.ValueSet;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.TestService;
import org.snomed.snowstormlite.fhir.CodeSystemProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.snomed.snowstormlite.TestService.EN_LANGUAGE_DIALECTS;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class SemanticSearchTest {

	@Autowired
	private TestService testService;

	@Autowired
	private ValueSetService valueSetService;

	@Autowired
	private EmbeddingIndexService embeddingIndexService;

	@Autowired
	private CodeSystemProvider codeSystemProvider;

	@Test
	void semanticExpandRespectsEclFilterAndRanking() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		embeddingIndexService.indexEmbeddings(EmbeddingIndexService.DEFAULT_MODEL_ID, List.of(
				new EmbeddingIndexService.EmbeddingInput("404684003", new float[]{1f, 0f}),
				new EmbeddingIndexService.EmbeddingInput("362969004", new float[]{0.8f, 0.2f}),
				new EmbeddingIndexService.EmbeddingInput("313005", new float[]{0f, 1f}),
				new EmbeddingIndexService.EmbeddingInput("138875005", new float[]{0.99f, 0.01f})
		), true);

		ValueSet semanticExpand = valueSetService.expand(
				"http://snomed.info/sct?fhir_vs=ecl/<< 404684003 |Clinical finding|",
				null,
				EN_LANGUAGE_DIALECTS,
				false,
				0,
				10,
				SemanticQueryRequest.enabled(EmbeddingIndexService.DEFAULT_MODEL_ID, new float[]{1f, 0f}));

		List<String> returnedCodes = semanticExpand.getExpansion().getContains().stream()
				.map(ValueSet.ValueSetExpansionContainsComponent::getCode)
				.toList();
		assertEquals(List.of("404684003", "362969004", "313005"), returnedCodes);
		assertTrue(returnedCodes.stream().noneMatch("138875005"::equals));

		for (ValueSet.ValueSetExpansionContainsComponent containsComponent : semanticExpand.getExpansion().getContains()) {
			assertNotNull(containsComponent.getExtensionByUrl(ValueSetService.SEMANTIC_SCORE_EXTENSION_URL));
		}
	}

	@Test
	void binaryIndexOperationSupportsSemanticSearch() throws IOException, ReleaseImportException {
		testService.importRF2Int();

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setContentType("application/octet-stream");
		request.setParameter("model", "binary-test-model");
		request.setParameter("replace", "true");
		request.setContent(buildBinaryPayload(2, new long[]{404684003L, 362969004L, 313005L}, new float[][]{
				{1f, 0f},
				{0.8f, 0.2f},
				{0f, 1f}
		}));

		codeSystemProvider.indexEmbeddingsBinary(request, null, null);

		ValueSet semanticExpand = valueSetService.expand(
				"http://snomed.info/sct?fhir_vs=ecl/<< 404684003 |Clinical finding|",
				null,
				EN_LANGUAGE_DIALECTS,
				false,
				0,
				10,
				SemanticQueryRequest.enabled("binary-test-model", new float[]{1f, 0f}));

		List<String> returnedCodes = semanticExpand.getExpansion().getContains().stream()
				.map(ValueSet.ValueSetExpansionContainsComponent::getCode)
				.toList();
		assertEquals(List.of("404684003", "362969004", "313005"), returnedCodes);
	}

	@AfterEach
	public void after() throws IOException {
		testService.tearDown();
	}

	private byte[] buildBinaryPayload(int dimension, long[] conceptIds, float[][] vectors) {
		ByteBuffer buffer = ByteBuffer
				.allocate(4 + Integer.BYTES + Integer.BYTES + conceptIds.length * (Long.BYTES + (dimension * Float.BYTES)))
				.order(ByteOrder.LITTLE_ENDIAN);

		buffer.put((byte) 'S');
		buffer.put((byte) 'L');
		buffer.put((byte) 'E');
		buffer.put((byte) '1');
		buffer.putInt(dimension);
		buffer.putInt(conceptIds.length);
		for (int i = 0; i < conceptIds.length; i++) {
			buffer.putLong(conceptIds[i]);
			for (int d = 0; d < dimension; d++) {
				buffer.putFloat(vectors[i][d]);
			}
		}
		return buffer.array();
	}
}
