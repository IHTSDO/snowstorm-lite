package org.snomed.snowstormlite.config;

import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.snowstormlite.service.ecl.ExpressionConstraintLanguageService;
import org.snomed.snowstormlite.service.ecl.SECLObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class EclConfiguration {

	@Bean
	public ECLQueryBuilder eclQueryBuilder(@Lazy ExpressionConstraintLanguageService eclService) {
		return new ECLQueryBuilder(new SECLObjectFactory(eclService));
	}

}
