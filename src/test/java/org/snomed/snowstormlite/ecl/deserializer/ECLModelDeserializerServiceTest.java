package org.snomed.snowstormlite.ecl.deserializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.fhir.FHIRServerResponseException;
import org.snomed.snowstormlite.service.ecl.EclFeatureValidator;
import org.snomed.snowstormlite.service.ecl.deserializer.ECLModelDeserializerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ECLModelDeserializerServiceTest {

	@Autowired
	private ECLQueryBuilder eclQueryBuilder;

	@Autowired
	private ECLModelDeserializerService eclModelDeserializerService;

	@Autowired
	private EclFeatureValidator eclFeatureValidator;

	private final ObjectMapper objectMapper = new ObjectMapper()
			.setSerializationInclusion(JsonInclude.Include.NON_NULL);

	@Test
	void testSupportedRoundTrip() throws JsonProcessingException {
		assertConversionTest("*");
		assertConversionTest("404684003 |Clinical finding|");
		assertConversionTest("< 404684003 |Clinical finding|");
		assertConversionTest("<< 404684003 |Clinical finding|");
		assertConversionTest("<! 404684003 |Clinical finding|");
		assertConversionTest("<<! 404684003 |Clinical finding|");
		assertConversionTest("> 362969004");
		assertConversionTest(">> 362969004");
		assertConversionTest(">! 362969004");
		assertConversionTest(">>! 362969004");
		assertConversionTest("^ 11816080008");
		assertConversionTest(">> 900000000000441003 AND >> 362969004", ">> 900000000000441003, >> 362969004");
		assertConversionTest(">> 900000000000441003 OR >> 362969004", ">> 900000000000441003 or >> 362969004");
		assertConversionTest(">> 900000000000441003 MINUS >> 362969004", ">> 900000000000441003 minus >> 362969004");
		assertConversionTest("< 404684003 |Clinical finding| : 363698007 |Finding site| = 113331007 |Structure of endocrine system|");
		assertConversionTest("< 404684003 |Clinical finding| . 363698007 |Finding site|");
		assertConversionTest("< 404684003 |Clinical finding| {{ +HISTORY }}", "< 404684003 |Clinical finding| {{ + HISTORY }}");
	}

	@Test
	void testUnsupportedConceptFilterOnParse() {
		assertThrows(FHIRServerResponseException.class, () -> eclQueryBuilder.createQuery("* {{ C definitionStatus = primitive }}"));
	}

	@Test
	void testUnsupportedMemberFieldsOnValidate() {
		ExpressionConstraint expressionConstraint = eclQueryBuilder.createQuery("^ [*] 404684003");
		assertThrows(FHIRServerResponseException.class, () -> eclFeatureValidator.validate(expressionConstraint));
	}

	@Test
	void testUnsupportedAttributeGroupOnParse() {
		assertThrows(FHIRServerResponseException.class, () -> eclQueryBuilder.createQuery(
				"< 404684003 |Clinical finding| : { 363698007 |Finding site| = << 39057004 |Pulmonary valve structure| }"));
	}

	private void assertConversionTest(String inputEcl) throws JsonProcessingException {
		assertConversionTest(inputEcl, inputEcl);
	}

	private void assertConversionTest(String inputEcl, String expectedEcl) throws JsonProcessingException {
		final ExpressionConstraint eclModel = eclQueryBuilder.createQuery(inputEcl);
		eclFeatureValidator.validate(eclModel);
		final String eclModelJsonString = objectMapper.writeValueAsString(eclModel);
		String eclOutputString = eclModelDeserializerService.convertECLModelToString(eclModelJsonString);
		assertEquals(expectedEcl, eclOutputString, "Not equal using model: " + eclModelJsonString);
	}

}
