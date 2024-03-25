package org.snomed.snowstormlite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "search.language")
    public LanguageCharacterFoldingConfiguration languageCharacterFoldingConfiguration() {
        return new LanguageCharacterFoldingConfiguration();
    }

    @Bean
    @ConfigurationProperties(prefix = "search.dialect")
    public LanguageDialectAliasConfiguration languageDialectAliasConfiguration() {
        return new LanguageDialectAliasConfiguration();
    }

}
