package org.snomed.snowstormlite.mcp.config;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies MCP tool registration wiring. Without {@link McpConfiguration#mcpToolProvider},
 * Spring AI's MCP server starts with an empty tool list.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class McpToolRegistrationTest {

	private static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
			"lookup_snomed_code",
			"search_snomed_codes",
			"validate_snomed_code",
			"check_snomed_subsumption"
	);

	@Autowired
	private ToolCallbackProvider mcpToolProvider;

	@Autowired
	@Qualifier("syncTools")
	private List<SyncToolSpecification> mcpSyncTools;

	@Test
	void mcpToolProviderExposesSnomedTools() {
		Set<String> toolNames = Arrays.stream(mcpToolProvider.getToolCallbacks())
				.map(ToolCallback::getToolDefinition)
				.map(def -> def.name())
				.collect(Collectors.toSet());

		assertEquals(EXPECTED_TOOL_NAMES, toolNames);
	}

	@Test
	void mcpServerRegistersSnomedToolsForClients() {
		assertFalse(mcpSyncTools.isEmpty(), "MCP server should register SNOMED CT tools for client discovery");

		Set<String> toolNames = mcpSyncTools.stream()
				.map(spec -> spec.tool().name())
				.collect(Collectors.toSet());

		assertEquals(EXPECTED_TOOL_NAMES, toolNames);
	}
}
