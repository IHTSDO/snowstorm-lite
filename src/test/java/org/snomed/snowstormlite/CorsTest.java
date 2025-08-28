package org.snomed.snowstormlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testCorsHeadersForFhirCodeSystem() {
        // Test CORS headers for /fhir/CodeSystem endpoint
        ResponseEntity<String> response = restTemplate.exchange(
                "/fhir/CodeSystem",
                HttpMethod.GET,
                null,
                String.class
        );

        // Verify the response status (should be 200 or 404 depending on data availability)
        assertTrue(response.getStatusCode().is2xxSuccessful() || response.getStatusCode() == HttpStatus.NOT_FOUND,
                "Response should be successful or not found");

        // Check for CORS headers
        HttpHeaders headers = response.getHeaders();
        
        // Check for Access-Control-Allow-Origin header
        assertTrue(headers.containsKey("Access-Control-Allow-Origin") || 
                   headers.containsKey("access-control-allow-origin"),
                "CORS Access-Control-Allow-Origin header should be present");
        
        // Check for Access-Control-Allow-Methods header
        assertTrue(headers.containsKey("Access-Control-Allow-Methods") || 
                   headers.containsKey("access-control-allow-methods"),
                "CORS Access-Control-Allow-Methods header should be present");
        
        // Check for Access-Control-Allow-Headers header
        assertTrue(headers.containsKey("Access-Control-Allow-Headers") || 
                   headers.containsKey("access-control-allow-headers"),
                "CORS Access-Control-Allow-Headers header should be present");
    }

    @Test
    void testCorsHeadersForFhirPartialHierarchy() {
        // Test CORS headers for /fhir/partial-hierarchy endpoint (redirected to Spring MVC)
        ResponseEntity<String> response = restTemplate.exchange(
                "/fhir/partial-hierarchy",
                HttpMethod.POST,
                null,
                String.class
        );

        // Verify the response status (should be 400 due to missing request body, but CORS headers should still be present)
        assertTrue(response.getStatusCode().is4xxClientError(),
                "Response should be client error due to missing request body");

        // Check for CORS headers
        HttpHeaders headers = response.getHeaders();
        
        // Check for Access-Control-Allow-Origin header
        assertTrue(headers.containsKey("Access-Control-Allow-Origin") || 
                   headers.containsKey("access-control-allow-origin"),
                "CORS Access-Control-Allow-Origin header should be present");
        
        // Check for Access-Control-Allow-Methods header
        assertTrue(headers.containsKey("Access-Control-Allow-Methods") || 
                   headers.containsKey("access-control-allow-methods"),
                "CORS Access-Control-Allow-Methods header should be present");
        
        // Check for Access-Control-Allow-Headers header
        assertTrue(headers.containsKey("Access-Control-Allow-Headers") || 
                   headers.containsKey("access-control-allow-headers"),
                "CORS Access-Control-Allow-Headers header should be present");
    }

    @Test
    void testCorsHeadersForPartialHierarchy() {
        // Test CORS headers for /partial-hierarchy endpoint (Spring MVC endpoint)
        ResponseEntity<String> response = restTemplate.exchange(
                "/partial-hierarchy",
                HttpMethod.POST,
                null,
                String.class
        );

        // Verify the response status (should be 400 due to missing request body, but CORS headers should still be present)
        assertTrue(response.getStatusCode().is4xxClientError(),
                "Response should be client error due to missing request body");

        // Check for CORS headers
        HttpHeaders headers = response.getHeaders();
        
        // Check for Access-Control-Allow-Origin header
        assertTrue(headers.containsKey("Access-Control-Allow-Origin") || 
                   headers.containsKey("access-control-allow-origin"),
                "CORS Access-Control-Allow-Origin header should be present");
        
        // Check for Access-Control-Allow-Methods header
        assertTrue(headers.containsKey("Access-Control-Allow-Methods") || 
                   headers.containsKey("access-control-allow-methods"),
                "CORS Access-Control-Allow-Methods header should be present");
        
        // Check for Access-Control-Allow-Headers header
        assertTrue(headers.containsKey("Access-Control-Allow-Headers") || 
                   headers.containsKey("access-control-allow-headers"),
                "CORS Access-Control-Allow-Headers header should be present");
    }

    @Test
    void testCorsPreflightRequest() {
        // Test CORS preflight request (OPTIONS method) for FHIR endpoint
        ResponseEntity<String> response = restTemplate.exchange(
                "/fhir/CodeSystem",
                HttpMethod.OPTIONS,
                null,
                String.class
        );

        // Preflight requests should return 200 OK or 400 BAD_REQUEST (depending on how FHIR handles OPTIONS)
        assertTrue(response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.BAD_REQUEST,
                "CORS preflight request should return 200 OK or 400 BAD_REQUEST");

        // Check for CORS headers
        HttpHeaders headers = response.getHeaders();
        
        // Check for Access-Control-Allow-Origin header
        assertTrue(headers.containsKey("Access-Control-Allow-Origin") || 
                   headers.containsKey("access-control-allow-origin"),
                "CORS Access-Control-Allow-Origin header should be present");
        
        // Check for Access-Control-Allow-Methods header
        assertTrue(headers.containsKey("Access-Control-Allow-Methods") || 
                   headers.containsKey("access-control-allow-methods"),
                "CORS Access-Control-Allow-Methods header should be present");
        
        // Check for Access-Control-Allow-Headers header
        assertTrue(headers.containsKey("Access-Control-Allow-Headers") || 
                   headers.containsKey("access-control-allow-headers"),
                "CORS Access-Control-Allow-Headers header should be present");
        
        // Check for Access-Control-Max-Age header
        assertTrue(headers.containsKey("Access-Control-Max-Age") || 
                   headers.containsKey("access-control-max-age"),
                "CORS Access-Control-Max-Age header should be present");
    }
}
