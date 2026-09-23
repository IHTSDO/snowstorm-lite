package org.snomed.snowstormlite.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppBasicAuthenticationEntryPointTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void unauthenticatedApiRequestGetsBasicChallenge() {
		ResponseEntity<String> response = postInstall(new HttpHeaders());

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertEquals("Basic realm=snomedtools", response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
	}

	@Test
	void unauthenticatedDashboardRequestGetsJsonWithoutChallenge() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Requested-With", "XMLHttpRequest");
		ResponseEntity<String> response = postInstall(headers);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
		assertNotNull(response.getHeaders().getContentType());
		assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"message\""));
	}

	private ResponseEntity<String> postInstall(HttpHeaders headers) {
		headers.setContentType(MediaType.APPLICATION_JSON);
		return restTemplate.exchange("/syndication/install", HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
	}
}
