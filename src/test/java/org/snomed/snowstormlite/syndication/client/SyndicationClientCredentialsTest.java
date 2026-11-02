package org.snomed.snowstormlite.syndication.client;

import org.junit.jupiter.api.Test;
import org.springframework.data.util.Pair;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SyndicationClientCredentialsTest {

	@Test
	void usesConfiguredUsernameAndPassword() throws Exception {
		Pair<String, String> result = SyndicationClient.resolveSyndicationCredentials(
				"user", "secret", false, () -> {
					throw new AssertionError("reader must not be called");
				});
		assertEquals("user", result.getFirst());
		assertEquals("secret", result.getSecond());
	}

	@Test
	void missingCredentialsWithoutCliPromptReturnsNullWithoutReading() throws Exception {
		AtomicBoolean readCalled = new AtomicBoolean(false);
		assertNull(SyndicationClient.resolveSyndicationCredentials(
				"", "", false, () -> {
					readCalled.set(true);
					return Pair.of("u", "p");
				}));
		assertFalse(readCalled.get());
	}

	@Test
	void missingCredentialsWithCliPromptInvokesReader() throws Exception {
		Pair<String, String> entered = Pair.of("cli-user", "cli-pass");
		Pair<String, String> result = SyndicationClient.resolveSyndicationCredentials(
				null, null, true, () -> entered);
		assertSame(entered, result);
	}

	@Test
	void blankCliInputReturnsNull() throws Exception {
		assertNull(SyndicationClient.resolveSyndicationCredentials(
				" ", "\t", true, () -> Pair.of("", "")));
	}
}
