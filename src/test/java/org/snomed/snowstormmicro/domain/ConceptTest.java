package org.snomed.snowstormmicro.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ConceptTest {

	@Test
	void testNormalFormDefined() throws IOException {
		Concept concept = new Concept();
		concept.setConceptId("348315009");
		concept.setDefined(true);

		concept.addParentCode("778591001");
		concept.addRelationship(0, "411116001", "421026006");
		concept.addRelationship(0, "763032000", "732936001");
		concept.addRelationship(0, "1142139005", "#2");

		concept.addRelationship(1, "762949000", "255641001");
		concept.addRelationship(1, "732943007", "255641001");
		concept.addRelationship(1, "1142135004", "#65");
		concept.addRelationship(1, "732945000", "258684004");
		concept.addRelationship(1, "1142136003", "#1");
		concept.addRelationship(1, "732947008", "732936001");

		concept.addRelationship(2, "762949000", "387517004");
		concept.addRelationship(2, "732943007", "387517004");
		concept.addRelationship(2, "1142135004", "#500");
		concept.addRelationship(2, "732945000", "258684004");
		concept.addRelationship(2, "1142136003", "#1");
		concept.addRelationship(2, "732947008", "732936001");

		assertEquals("===778591001:411116001=421026006,763032000=732936001,1142139005=#2," +
				"{732943007=255641001,732945000=258684004,732947008=732936001,762949000=255641001,1142135004=#65,1142136003=#1}," +
				"{732943007=387517004,732945000=258684004,732947008=732936001,762949000=387517004,1142135004=#500,1142136003=#1}", concept.getNormalFormTerse());
	}

	@Test
	void testNormalFormPrimitive() throws IOException {
		Concept concept = new Concept();
		concept.setConceptId("255641001");
		concept.setDefined(false);

		concept.addParentCode("259553003");
		concept.addParentCode("301054007");
		concept.addParentCode("387459000");
		concept.addParentCode("373692009");
		concept.addRelationship(0, "726542003", "734552005");

		assertEquals("<<<255641001:726542003=734552005", concept.getNormalFormTerse());
	}

}
