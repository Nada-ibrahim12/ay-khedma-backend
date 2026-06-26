package com.aykhedma.mcp.server;

import java.util.Map;

public interface McpToolHandler {

    String getName();

    Map<String, Object> getSchema();

    Object execute(Map<String, Object> arguments);
}