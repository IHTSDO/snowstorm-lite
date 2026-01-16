package org.snomed.snowstormlite.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for Model Context Protocol (MCP) server functionality.
 * <p>
 * This configuration enables MCP server capabilities for Snowstorm Lite,
 * allowing AI assistants to interact with SNOMED CT terminology data through
 * standardized MCP tools.
 * <p>
 * The MCP server is auto-configured by Spring AI MCP starter and provides:
 * <ul>
 *   <li>SSE (Server-Sent Events) transport at /mcp/sse</li>
 *   <li>Tool discovery and invocation</li>
 *   <li>JSON-RPC 2.0 message handling</li>
 * </ul>
 */
@Configuration
public class McpConfiguration {

	private static final Logger log = LoggerFactory.getLogger(McpConfiguration.class);

	@PostConstruct
	public void init() {
		log.info("MCP Configuration initialized - SNOMED CT MCP tools available via SSE transport");
	}
}
