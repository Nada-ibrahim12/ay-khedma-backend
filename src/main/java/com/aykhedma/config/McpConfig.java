package com.aykhedma.config;

import com.aykhedma.mcp.server.McpServer;
import com.aykhedma.mcp.tools.McpToolRegistry;
import com.aykhedma.mcp.tools.tools.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@Slf4j
public class McpConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        log.info("ObjectMapper configured with JavaTimeModule");
        return mapper;
    }

    @Bean
    public McpServer mcpServer(
            McpToolRegistry toolRegistry,
            SearchProvidersTool searchProvidersTool,
            CheckAvailabilityTool checkAvailabilityTool,
            CreateBookingTool createBookingTool,
            GetProviderDetailsTool getProviderDetailsTool,
            ObjectMapper objectMapper) {

        log.info("Initializing MCP Server...");

        // Manually register all tools
        log.info("Registering SearchProvidersTool...");
        toolRegistry.registerTool(searchProvidersTool);

        log.info("Registering CheckAvailabilityTool...");
        toolRegistry.registerTool(checkAvailabilityTool);

        log.info("Registering CreateBookingTool...");
        toolRegistry.registerTool(createBookingTool);

        log.info("Registering GetProviderDetailsTool...");
        toolRegistry.registerTool(getProviderDetailsTool);

        log.info("All tools registered in registry. Total: {}", toolRegistry.getToolCount());

        McpServer server = new McpServer(objectMapper);

        // Register all tools with the server
        toolRegistry.registerAll(server);

        log.info("MCP Server initialized with {} tools", server.getToolCount());
        return server;
    }
}