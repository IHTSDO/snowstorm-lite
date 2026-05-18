package org.snomed.snowstormlite.fhir;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FhirBatchBundleLookupTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void postBatchBundleReturnsBatchResponseWithLookupParameters() {
		ResponseEntity<String> csProbe = restTemplate.getForEntity("/fhir/CodeSystem", String.class);
		Assumptions.assumeTrue(csProbe.getStatusCode().is2xxSuccessful());
		String csBody = csProbe.getBody();
		Assumptions.assumeTrue(csBody != null && !csBody.contains("\"total\":0"), "No CodeSystems loaded in test index; skipping Batch Bundle lookup assertions.");

		String batchJson = """
				{
				  "resourceType": "Bundle",
				  "type": "batch",
				  "entry": [
				    {
				      "request": {
				        "method": "GET",
				        "url": "CodeSystem/$lookup?system=http%3A%2F%2Fsnomed.info%2Fsct&code=138875005"
				      }
				    },
				    {
				      "request": {
				        "method": "GET",
				        "url": "CodeSystem/$lookup?system=http%3A%2F%2Fsnomed.info%2Fsct&code=404040404040404040"
				      }
				    }
				  ]
				}
				""";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/fhir+json"));
		HttpEntity<String> entity = new HttpEntity<>(batchJson, headers);

		ResponseEntity<String> response = restTemplate.exchange("/fhir/", HttpMethod.POST, entity, String.class);

		assertTrue(response.getStatusCode().is2xxSuccessful(), "Batch POST should succeed: " + response.getStatusCode());
		String body = response.getBody();
		assertNotNull(body);
		assertTrue(body.contains("\"type\":\"batch-response\""), () -> "Expected batch-response Bundle, got: " + body.substring(0, Math.min(400, body.length())));
		assertTrue(body.contains("\"resourceType\":\"Parameters\""), () -> "Expected at least one Parameters lookup result in batch response");
		assertTrue(body.contains("\"name\":\"code\""), () -> "Expected lookup Parameters to include code parameter");
	}
}
