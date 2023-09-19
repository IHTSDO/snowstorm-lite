package org.snomed.snowstormlite;

import org.hibernate.service.spi.ServiceException;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.service.AppSetupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;

import java.io.IOException;

@SpringBootApplication(
		exclude = {
				DataSourceAutoConfiguration.class,
				QuartzAutoConfiguration.class,
				ThymeleafAutoConfiguration.class
		}
)
public class SnowstormLiteApplication implements CommandLineRunner {

	@Autowired
	private AppSetupService appSetupService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void run(String... args) {
		try {
			appSetupService.run();
		} catch (ServiceException | IOException | ReleaseImportException e) {
			logger.error(e.getMessage(), e);
			System.exit(1);
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(SnowstormLiteApplication.class, args);
	}

}
