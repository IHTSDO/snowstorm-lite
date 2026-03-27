package org.snomed.snowstormlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminFeaturesEndpointTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void featuresEndpointAdvertisesEmbeddingCapabilities() {
		ResponseEntity<Map> response = restTemplate.exchange(
				"/fhir-admin/features",
				HttpMethod.GET,
				null,
				Map.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		Map<String, Object> body = response.getBody();
		assertNotNull(body);
		assertEquals(1, body.get("schemaVersion"));
		assertEquals("snowstorm-lite", body.get("product"));
		assertEquals(Boolean.TRUE, body.get("embeddingsSupported"));
		assertEquals(Boolean.TRUE, body.get("semanticSearchSupported"));

		Map<String, Object> features = (Map<String, Object>) body.get("features");
		assertNotNull(features);
		assertEquals(Boolean.TRUE, features.get("semanticExpand"));
		assertEquals(Boolean.TRUE, features.get("semanticMatchOperation"));
		assertEquals(Boolean.TRUE, features.get("embeddingIndexOperation"));
		assertEquals(Boolean.TRUE, features.get("embeddingBinaryIndexOperation"));
		assertEquals(Boolean.TRUE, features.get("semanticSearchWithinEcl"));
	}
}
