package org.snomed.snowstormlite.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstormlite.rest.pojo.BuildVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Version", description = "Build Version")
public class VersionController {

	private final BuildProperties buildProperties;

	public VersionController(@Autowired(required = false) BuildProperties buildProperties) {
		this.buildProperties = buildProperties;
	}

	@Operation(summary = "Software build version and timestamp.")
	@GetMapping(value = "/version", produces = "application/json")
	public BuildVersion getBuildInformation() {
		if (buildProperties == null) {
			return new BuildVersion("development", null);
		}
		return new BuildVersion(buildProperties.getVersion(), buildProperties.getTime().toString());
	}

}
