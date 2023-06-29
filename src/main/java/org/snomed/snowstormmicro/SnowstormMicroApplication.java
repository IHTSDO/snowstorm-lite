package org.snomed.snowstormmicro;

import org.apache.logging.log4j.util.Strings;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormmicro.loading.ImportService;
import org.snomed.snowstormmicro.util.TimerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@SpringBootApplication(
		exclude = {
				DataSourceAutoConfiguration.class,
				QuartzAutoConfiguration.class,
				ThymeleafAutoConfiguration.class
		}
)
public class SnowstormMicroApplication implements CommandLineRunner {

	@Value("${load}")
	private String loadReleaseArchives;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void run(String... args) {
		if (!Strings.isEmpty(loadReleaseArchives)) {
			Set<String> filePaths = Arrays.stream(loadReleaseArchives.split(",")).collect(Collectors.toSet());
			try {
				TimerUtil timer = new TimerUtil("Import");
				new ImportService().importRelease(filePaths);
				timer.finish();
				logger.info("Import complete");
				System.exit(0);
			} catch (ReleaseImportException | IOException e) {
				logger.error("Import failed", e);
				System.exit(1);
			}
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(SnowstormMicroApplication.class, args);
	}

}
