package com.aykhedma.controller;

import com.aykhedma.mcp.server.McpServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpController {

    private final McpServer mcpServer;

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleRequest(@RequestBody Map<String, Object> request) {
        log.info("MCP request received: {}", request.get("method"));
        try {
            Map<String, Object> response = mcpServer.handleRequest(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("MCP error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "AyKhedma MCP Server",
                "version", "1.0.0",
                "tools", String.valueOf(mcpServer.getToolCount())));
    }

    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "method", "tools/list",
                "id", "1");
        Map<String, Object> response = mcpServer.handleRequest(request);
        return ResponseEntity.ok(response);
    }
}