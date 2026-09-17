package org.snomed.snowstormlite.service.ecl.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.snowstormlite.service.ecl.constraint.SCompoundExpressionConstraint;
import org.snomed.snowstormlite.service.ecl.constraint.SDottedExpressionConstraint;
import org.snomed.snowstormlite.service.ecl.constraint.SEclRefinement;
import org.snomed.snowstormlite.service.ecl.constraint.SRefinedExpressionConstraint;
import org.snomed.snowstormlite.service.ecl.constraint.SSubExpressionConstraint;

import java.io.IOException;

public class ECLModelDeserializer extends StdDeserializer<ExpressionConstraint> {

	private final ObjectMapper mapper;

	public ECLModelDeserializer(ObjectMapper mapper, Class<?> vc) {
		super(vc);
		this.mapper = mapper;
	}

	@Override
	public ExpressionConstraint deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
		JsonNode node = jsonParser.getCodec().readTree(jsonParser);
		if (node.get("dottedAttributes") != null) {
			return mapper.readValue(node.toString(), SDottedExpressionConstraint.class);
		}
		if (node.get("eclRefinement") != null) {
			return mapper.readValue(node.toString(), SRefinedExpressionConstraint.class);
		}
		if (node.get("conjunctionExpressionConstraints") != null ||
				node.get("disjunctionExpressionConstraints") != null ||
				node.get("exclusionExpressionConstraints") != null) {
			return mapper.readValue(node.toString(), SCompoundExpressionConstraint.class);
		}
		return mapper.readValue(node.toString(), SSubExpressionConstraint.class);
	}

	public static void expressionConstraintToString(Object expressionConstraint, StringBuffer buffer) {
		if (expressionConstraint instanceof SDottedExpressionConstraint dottedExpressionConstraint) {
			dottedExpressionConstraint.toString(buffer);
		} else if (expressionConstraint instanceof SRefinedExpressionConstraint refinedExpressionConstraint) {
			refinedExpressionConstraint.toString(buffer);
		} else if (expressionConstraint instanceof SCompoundExpressionConstraint compoundExpressionConstraint) {
			compoundExpressionConstraint.toString(buffer);
		} else if (expressionConstraint instanceof SSubExpressionConstraint subExpressionConstraint) {
			subExpressionConstraint.toString(buffer);
		}
	}

	public static void refinementToString(SEclRefinement refinement, StringBuffer buffer) {
		refinement.toString(buffer);
	}

}
