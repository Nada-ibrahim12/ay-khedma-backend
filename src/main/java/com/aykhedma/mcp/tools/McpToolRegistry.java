package com.aykhedma.mcp.tools;

import com.aykhedma.mcp.server.McpServer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class McpToolRegistry {

    private final List<McpTool> tools = new ArrayList<>();

    @PostConstruct
    public void init() {
        log.info("McpToolRegistry initialized. Tools registered so far: {}", tools.size());
    }

    public void registerTool(McpTool tool) {
        tools.add(tool);
        log.info("Registered tool in registry: {} (total: {})", tool.getName(), tools.size());
    }

    public void registerAll(McpServer server) {
        log.info("Registering {} tools with MCP server...", tools.size());
        for (McpTool tool : tools) {
            server.registerTool(tool);
            log.info("Registered: {}", tool.getName());
        }
        log.info("Registered {} tools with MCP server", tools.size());
    }

    public int getToolCount() {
        return tools.size();
    }

    public List<McpTool> getTools() {
        return new ArrayList<>(tools);
    }
}