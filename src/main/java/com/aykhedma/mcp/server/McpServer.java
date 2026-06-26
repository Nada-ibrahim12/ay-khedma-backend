package com.aykhedma.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class McpServer {

    private final ObjectMapper objectMapper;
    private final Map<String, McpToolHandler> toolHandlers = new LinkedHashMap<>();

    public McpServer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.info("MCP Server initialized");
    }

    public void registerTool(McpToolHandler handler) {
        toolHandlers.put(handler.getName(), handler);
        log.info("Registered MCP tool: {}", handler.getName());
    }

    public Set<String> getToolNames() {
        return toolHandlers.keySet();
    }

    public int getToolCount() {
        return toolHandlers.size();
    }


    public Map<String, Object> handleRequest(Map<String, Object> request) {
        String method = (String) request.get("method");
        String id = request.get("id") != null ? request.get("id").toString() : "1";

        log.debug("MCP Request: method={}, id={}", method, id);

        try {
            return switch (method) {
                case "initialize" -> handleInitialize(id);
                case "tools/list" -> handleListTools(id);
                case "tools/call" -> {
                    Map<String, Object> params = (Map<String, Object>) request.get("params");
                    yield handleCallTool(params, id); 
                }
                default -> errorResponse("Method not found: " + method, id);
            };
        } catch (Exception e) {
            log.error("MCP Error: {}", e.getMessage(), e);
            return errorResponse(e.getMessage(), id);
        }
    }

    private Map<String, Object> handleInitialize(String id) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "AyKhedma-MCP-Server");
        serverInfo.put("version", "1.0.0");
        result.put("serverInfo", serverInfo);
        result.put("protocolVersion", "2024-11-05");

        response.put("result", result);
        return response;
    }

    private Map<String, Object> handleListTools(String id) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpToolHandler handler : toolHandlers.values()) {
            tools.add(handler.getSchema());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);

        response.put("result", result);
        return response;
    }

    private Map<String, Object> handleCallTool(Map<String, Object> params, String id) {
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        log.info("🔧 Calling MCP tool: {} with args: {}", toolName, arguments);

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        try {
            McpToolHandler handler = toolHandlers.get(toolName);
            if (handler == null) {
                throw new IllegalArgumentException("Unknown tool: " + toolName);
            }

            Object result = handler.execute(arguments);

            Map<String, Object> resultObj = new HashMap<>();
            List<Map<String, Object>> contentList = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            content.put("type", "text");

            String jsonResult = objectMapper.writeValueAsString(result);
            content.put("text", jsonResult);
            contentList.add(content);

            resultObj.put("content", contentList);
            resultObj.put("isError", false);

            response.put("result", resultObj);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", e.getMessage(), e);

            Map<String, Object> resultObj = new HashMap<>();
            List<Map<String, Object>> contentList = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            content.put("type", "text");
            content.put("text", "Error: " + e.getMessage());
            contentList.add(content);

            resultObj.put("content", contentList);
            resultObj.put("isError", true);

            response.put("result", resultObj);
        }

        return response;
    }

    private Map<String, Object> errorResponse(String message, String id) {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        Map<String, Object> error = new HashMap<>();
        error.put("code", -32000);
        error.put("message", message);
        response.put("error", error);

        return response;
    }
}