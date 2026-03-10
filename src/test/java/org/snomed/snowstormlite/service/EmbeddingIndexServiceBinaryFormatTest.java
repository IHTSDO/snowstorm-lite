package org.snomed.snowstormlite.service;

import org.junit.jupiter.api.Test;
import org.snomed.snowstormlite.fhir.FHIRServerResponseException;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddingIndexServiceBinaryFormatTest {

	@Test
	void parseBinaryEmbeddingsParsesValidPayload() {
		EmbeddingIndexService service = new EmbeddingIndexService();
		byte[] payload = buildPayload(3, new long[]{100001L, 100002L}, new float[][]{
				{0.1f, 0.2f, 0.3f},
				{1.1f, 1.2f, 1.3f}
		});

		List<EmbeddingIndexService.EmbeddingInput> embeddings = service.parseBinaryEmbeddings(new ByteArrayInputStream(payload));

		assertEquals(2, embeddings.size());
		assertEquals("100001", embeddings.get(0).conceptId());
		assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, embeddings.get(0).vector(), 0.000001f);
		assertEquals("100002", embeddings.get(1).conceptId());
		assertArrayEquals(new float[]{1.1f, 1.2f, 1.3f}, embeddings.get(1).vector(), 0.000001f);
	}

	@Test
	void parseBinaryEmbeddingsRejectsInvalidHeader() {
		EmbeddingIndexService service = new EmbeddingIndexService();
		byte[] payload = buildPayload(2, new long[]{100001L}, new float[][]{{0.5f, 0.6f}});
		payload[0] = 'X';

		assertThrows(FHIRServerResponseException.class, () -> service.parseBinaryEmbeddings(new ByteArrayInputStream(payload)));
	}

	private byte[] buildPayload(int dimension, long[] conceptIds, float[][] vectors) {
		int count = conceptIds.length;
		ByteBuffer buffer = ByteBuffer
				.allocate(4 + Integer.BYTES + Integer.BYTES + count * (Long.BYTES + (dimension * Float.BYTES)))
				.order(ByteOrder.LITTLE_ENDIAN);

		buffer.put((byte) 'S');
		buffer.put((byte) 'L');
		buffer.put((byte) 'E');
		buffer.put((byte) '1');
		buffer.putInt(dimension);
		buffer.putInt(count);
		for (int i = 0; i < count; i++) {
			buffer.putLong(conceptIds[i]);
			for (int d = 0; d < dimension; d++) {
				buffer.putFloat(vectors[i][d]);
			}
		}
		return buffer.array();
	}
}
