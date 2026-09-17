package org.snomed.snowstormlite.service.ecl.deserializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.FieldFilter;
import org.snomed.langauges.ecl.domain.filter.HistorySupplement;
import org.snomed.langauges.ecl.domain.refinement.EclAttribute;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeSet;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.langauges.ecl.domain.refinement.SubRefinement;
import org.snomed.snowstormlite.service.ecl.EclFeatureValidator;
import org.snomed.snowstormlite.service.ecl.constraint.SEclAttribute;
import org.snomed.snowstormlite.service.ecl.constraint.SEclAttributeSet;
import org.snomed.snowstormlite.service.ecl.constraint.SEclRefinement;
import org.snomed.snowstormlite.service.ecl.constraint.SSubAttributeSet;
import org.snomed.snowstormlite.service.ecl.constraint.SSubRefinement;
import org.snomed.snowstormlite.service.ecl.filter.SFieldFilter;
import org.snomed.snowstormlite.service.ecl.filter.SHistorySupplement;
import org.springframework.stereotype.Service;

@Service
public class ECLModelDeserializerService {

	private final ObjectMapper mapper;
	private final EclFeatureValidator eclFeatureValidator;

	public ECLModelDeserializerService(EclFeatureValidator eclFeatureValidator) {
		this.eclFeatureValidator = eclFeatureValidator;
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		SimpleModule module = new SimpleModule();
		final ECLModelDeserializer deserializer = new ECLModelDeserializer(mapper, null);
		module.addDeserializer(ExpressionConstraint.class, deserializer);
		module.addDeserializer(SubExpressionConstraint.class, new SubExpressionDeserializer(deserializer));

		module.addDeserializer(EclAttribute.class, new GenericJsonDeserializer<>(SEclAttribute.class));
		module.addDeserializer(EclAttributeSet.class, new GenericJsonDeserializer<>(SEclAttributeSet.class));
		module.addDeserializer(EclRefinement.class, new GenericJsonDeserializer<>(SEclRefinement.class));
		module.addDeserializer(SubAttributeSet.class, new GenericJsonDeserializer<>(SSubAttributeSet.class));
		module.addDeserializer(SubRefinement.class, new GenericJsonDeserializer<>(SSubRefinement.class));
		module.addDeserializer(HistorySupplement.class, new GenericJsonDeserializer<>(SHistorySupplement.class));
		module.addDeserializer(FieldFilter.class, new GenericJsonDeserializer<>(SFieldFilter.class));

		mapper.registerModule(module);
	}

	public String convertECLModelToString(String eclModelJsonString) throws JsonProcessingException {
		final ExpressionConstraint expressionConstraint = mapper.readValue(eclModelJsonString, ExpressionConstraint.class);
		eclFeatureValidator.validate(expressionConstraint);

		StringBuffer buffer = new StringBuffer();
		ECLModelDeserializer.expressionConstraintToString(expressionConstraint, buffer);
		return buffer.toString();
	}

}
