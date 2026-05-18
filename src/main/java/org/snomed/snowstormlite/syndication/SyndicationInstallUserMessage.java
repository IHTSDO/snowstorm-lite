package org.snomed.snowstormlite.syndication;

import org.snomed.snowstormlite.service.ServiceException;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces concise, non-technical text for syndication installation failures surfaced in the FHIR dashboard.
 * Avoids exception class names, raw URLs, HTML response bodies, and other noise from Spring {@code RestTemplate}.
 */
public final class SyndicationInstallUserMessage {

	private static final String GENERIC = "Installation failed.";

	private SyndicationInstallUserMessage() {
	}

	public static String describe(@Nullable Throwable failure) {
		if (failure == null) {
			return GENERIC;
		}
		List<Throwable> chain = causeChain(failure);

		HttpStatusCodeException http = lastOfType(chain, HttpStatusCodeException.class);
		if (http != null) {
			return fromHttp(http.getStatusCode());
		}
		if (lastOfType(chain, ResourceAccessException.class) != null) {
			return "Could not reach the syndication download server (network error or timeout). Check your connection and try again.";
		}
		ServiceException known = lastOfType(chain, ServiceException.class);
		if (known != null) {
			String msg = known.getMessage();
			if (msg != null && !msg.isBlank()) {
				return sanitizeLooseEnds(msg.strip());
			}
		}
		String fallback = failure.getMessage();
		if (fallback != null && !fallback.isBlank() && looksSafeUserText(fallback)) {
			return fallback.strip();
		}
		return GENERIC;
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

	private static String fromHttp(HttpStatusCode statusCode) {
		int code = statusCode.value();
		return switch (code) {
			case 401 -> "The syndication server rejected the credentials (HTTP 401). "
					+ "Check syndication.username and syndication.password.";
			case 403 -> "The syndication server denied access to this release package (HTTP 403). "
					+ "Verify syndication.username and syndication.password and that your MLDS account is allowed to download this edition.";
			case 404 -> "The RF2 package was not found (HTTP 404). The syndication feed may be out of date; try refreshing or picking another version.";
			case 408 -> "The syndication download timed out (HTTP 408). Try again.";
			case 429 -> "The syndication server rate-limited the download (HTTP 429). Wait and try again.";
			default -> {
				if (code >= 500 && code <= 599) {
					yield "The syndication server reported an error (HTTP " + code + "). Try again later.";
				}
				yield "Download failed (HTTP " + code + ").";
			}
		};
	}

	/**
	 * Avoid echoing RestTemplate/Spring messages that embed HTML or exception type names.
	 */
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

	private static String sanitizeLooseEnds(String s) {
		if (!looksSafeUserText(s)) {
			return GENERIC;
		}
		return s;
	}
}
