package org.snomed.snowstormlite.syndication;

import org.junit.jupiter.api.Test;
import org.snomed.snowstormlite.service.ServiceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyndicationInstallUserMessageTest {

	@Test
	void http403_nestedUnderIoException_excludesHtml() {
		byte[] htmlBody = "<!DOCTYPE html><html><title>Your request cannot be processed</title></html>"
				.getBytes(StandardCharsets.UTF_8);
		HttpClientErrorException forbidden = HttpClientErrorException.create(
				HttpStatus.FORBIDDEN,
				"Forbidden",
				HttpHeaders.EMPTY,
				htmlBody,
				StandardCharsets.UTF_8);
		String described = SyndicationInstallUserMessage.describe(new IOException(forbidden));
		assertTrue(described.contains("(HTTP 403)"), described);
		assertTrue(described.contains("MLDS"), described);
		assertFalse(described.contains("<!DOCTYPE"), described);
		assertFalse(described.contains("<html"), described);
		assertFalse(described.contains("Cannot be processed"));
		assertFalse(described.contains("Html"), described); // casing in title
	}

	@Test
	void serviceExceptionOnly_returnsMessageText() {
		String text = SyndicationInstallUserMessage.describe(
				new ServiceException("Derivative not found in syndication feed: http://example.com/x"));
		assertEquals(
				"Derivative not found in syndication feed: http://example.com/x",
				text);
	}

	@Test
	void httpCause_preferredOver_outerServiceMessage() {
		HttpClientErrorException unauthorized = HttpClientErrorException.create(
				HttpStatus.UNAUTHORIZED,
				"Unauthorized",
				HttpHeaders.EMPTY,
				new byte[0],
				StandardCharsets.UTF_8);
		String described = SyndicationInstallUserMessage.describe(new ServiceException("outer", unauthorized));
		assertTrue(described.contains("(HTTP 401)"), described);
		assertFalse(described.contains("outer"));
	}

	@Test
	void resourceAccessException_networkStyleMessage() {
		String described = SyndicationInstallUserMessage.describe(new ResourceAccessException("read timed out"));
		assertTrue(described.contains("Could not reach the syndication download server"));
	}

	@Test
	void unreadableTechnicalMessage_fallback() {
		String described = SyndicationInstallUserMessage.describe(new IOException(
				"org.springframework.web.client.HttpClientErrorException$Forbidden: boom"));
		assertEquals("Installation failed.", described);
	}

	@Test
	void null_returnsGeneric() {
		assertEquals("Installation failed.", SyndicationInstallUserMessage.describe(null));
	}
}
