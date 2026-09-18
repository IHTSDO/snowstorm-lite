package org.snomed.snowstormlite.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstormlite.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
class UtilityControllerTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void testEclStringToModel() {
		ResponseEntity<String> response = apiParseEcl("<< 404684003 |Clinical finding| : 363698007 |Finding site| = *");
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains("eclRefinement"));
	}

	@Test
	void testEclModelToString() {
		ResponseEntity<String> parseResponse = apiParseEcl("<< 404684003 |Clinical finding|");
		assertEquals(HttpStatus.OK, parseResponse.getStatusCode());

		ResponseEntity<String> convertResponse = apiConvertModel(parseResponse.getBody());
		assertEquals(HttpStatus.OK, convertResponse.getStatusCode());
		assertTrue(convertResponse.getBody().contains("eclString"));
		assertFalse(convertResponse.getBody().contains("\"eclString\":\"\""));
	}

	@Test
	void testEclStringToModelWithHistorySupplementPlusSign() {
		ResponseEntity<String> response = apiParseEcl("<< 404684003 |Clinical finding| {{ + HISTORY }}");
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains("historySupplement"));
	}

	@Test
	void testUnsupportedEclReturns501() {
		ResponseEntity<String> response = apiParseEcl("* {{ C definitionStatus = primitive }}");
		assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
	}

	private ResponseEntity<String> apiParseEcl(String ecl) {
		String url = "http://localhost:" + port + "/util/ecl-string-to-model";
		return restTemplate.postForEntity(url, ecl, String.class);
	}

	private ResponseEntity<String> apiConvertModel(String eclModel) {
		String url = "http://localhost:" + port + "/util/ecl-model-to-string";
		return restTemplate.postForEntity(url, eclModel, String.class);
	}

}
