package org.snomed.snowstormlite.syndication;

import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyndicationFeedUserMessageTest {

	@Test
	void saxParseException_nestedUnderJaxb_includesLineAndColumn() {
		SAXParseException sax = new SAXParseException(
				"XML document structures must start and end within the same entity.",
				null, null, 378, 137);
		JAXBException jaxb = new JAXBException("Unmarshal failed", sax);
		String described = SyndicationFeedUserMessage.describeFeedFailure(new IOException(jaxb));
		assertTrue(described.startsWith(SyndicationFeedUserMessage.PREFIX), described);
		assertTrue(described.contains("line 378"), described);
		assertTrue(described.contains("column 137"), described);
	}

	@Test
	void emptyBodyMessage() {
		assertEquals(
				SyndicationFeedUserMessage.EMPTY_BODY,
				SyndicationFeedUserMessage.describeFeedFailure(
						new IOException(SyndicationFeedUserMessage.EMPTY_BODY)));
	}

	@Test
	void jaxbWithoutSaxDetails_genericParseMessage() {
		String described = SyndicationFeedUserMessage.describeFeedFailure(new JAXBException("parse failed"));
		assertEquals(SyndicationFeedUserMessage.GENERIC_PARSE, described);
	}

	@Test
	void null_returnsGenericParseMessage() {
		assertEquals(SyndicationFeedUserMessage.GENERIC_PARSE, SyndicationFeedUserMessage.describeFeedFailure(null));
	}

	@Test
	void preformattedMessage_passthrough() {
		String message = SyndicationFeedUserMessage.PREFIX + ": XML parse error at line 1, column 1.";
		assertEquals(message, SyndicationFeedUserMessage.describeFeedFailure(new IOException(message)));
	}
}
