package org.snomed.snowstormlite;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@PropertySource("application.properties")
@PropertySource("application-test.properties")
@Configuration
@SpringBootApplication(
		exclude = {
				DataSourceAutoConfiguration.class,
				QuartzAutoConfiguration.class,
				ThymeleafAutoConfiguration.class
		}
)
public class TestConfig {
}
