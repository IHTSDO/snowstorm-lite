package org.snomed.snowstormlite.syndication;

import jakarta.xml.bind.JAXBException;
import org.springframework.lang.Nullable;
import org.xml.sax.SAXParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces concise, non-technical text for syndication feed fetch/parse failures surfaced in the FHIR dashboard.
 */
public final class SyndicationFeedUserMessage {

	public static final String PREFIX = "Syndication feed format issue";
	public static final String EMPTY_BODY = PREFIX + ": the feed response was empty.";
	public static final String GENERIC_PARSE = PREFIX + ": the feed response could not be parsed.";

	private SyndicationFeedUserMessage() {
	}

	public static String describeFeedFailure(@Nullable Throwable failure) {
		if (failure == null) {
			return GENERIC_PARSE;
		}
		String message = failure.getMessage();
		if (message != null && message.startsWith(PREFIX)) {
			return message.strip();
		}
		List<Throwable> chain = causeChain(failure);

		SAXParseException sax = lastOfType(chain, SAXParseException.class);
		if (sax != null) {
			return fromSaxParse(sax);
		}
		if (lastOfType(chain, JAXBException.class) != null) {
			return GENERIC_PARSE;
		}
		if (message != null && message.contains("Empty response body")) {
			return EMPTY_BODY;
		}
		if (message != null && !message.isBlank() && looksSafeUserText(message)) {
			return message.strip();
		}
		return GENERIC_PARSE;
	}

	private static String fromSaxParse(SAXParseException sax) {
		StringBuilder sb = new StringBuilder(PREFIX);
		sb.append(": XML parse error at line ").append(sax.getLineNumber());
		sb.append(", column ").append(sax.getColumnNumber());
		String detail = sax.getMessage();
		if (detail != null && !detail.isBlank() && looksSafeUserText(detail) && detail.length() <= 120) {
			sb.append(" (").append(detail.strip()).append(')');
		}
		sb.append('.');
		return sb.toString();
	}

	private static List<Throwable> causeChain(Throwable root) {
		List<Throwable> chain = new ArrayList<>();
		Throwable t = root;
		while (t != null && chain.size() < 32) {
			if (chain.contains(t)) {
				break;
			}
			chain.add(t);
			t = t.getCause();
		}
		return chain;
	}

	private static <T extends Throwable> T lastOfType(List<Throwable> chain, Class<T> type) {
		T found = null;
		for (Throwable t : chain) {
			if (type.isInstance(t)) {
				found = type.cast(t);
			}
		}
		return found;
	}

	private static boolean looksSafeUserText(String s) {
		String lower = s.toLowerCase();
		if (lower.contains("<!doctype") || lower.contains("<html")) {
			return false;
		}
		if (s.contains("org.springframework.") || s.contains("java.lang.") || s.contains("HttpClientErrorException")) {
			return false;
		}
		return true;
	}
}
