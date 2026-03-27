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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "snowstorm.embeddings.enabled=false")
class AdminFeaturesDisabledTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void featuresEndpointReportsEmbeddingsDisabled() {
		ResponseEntity<Map> response = restTemplate.exchange(
				"/fhir-admin/features",
				HttpMethod.GET,
				null,
				Map.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		Map<String, Object> body = response.getBody();
		assertNotNull(body);
		assertEquals("snowstorm-lite", body.get("product"));
		assertEquals(Boolean.FALSE, body.get("embeddingsSupported"));
		assertEquals(Boolean.FALSE, body.get("semanticSearchSupported"));

		Map<String, Object> features = (Map<String, Object>) body.get("features");
		assertNotNull(features);
		assertEquals(Boolean.FALSE, features.get("semanticExpand"));
		assertEquals(Boolean.FALSE, features.get("semanticMatchOperation"));
		assertEquals(Boolean.FALSE, features.get("embeddingIndexOperation"));
		assertEquals(Boolean.FALSE, features.get("embeddingBinaryIndexOperation"));
		assertEquals(Boolean.FALSE, features.get("semanticSearchWithinEcl"));

		assertNull(body.get("operations"), "Operations should not be advertised when embeddings are disabled");
	}
}
