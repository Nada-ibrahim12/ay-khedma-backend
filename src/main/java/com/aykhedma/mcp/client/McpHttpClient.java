// package com.aykhedma.mcp.client;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Service;
// import org.springframework.web.reactive.function.client.WebClient;
// import reactor.core.publisher.Mono;

// import java.util.Map;
// import java.util.UUID;

// @Service
// @Slf4j
// @RequiredArgsConstructor
// public class McpHttpClient {

//     private final WebClient webClient;
//     private final ObjectMapper objectMapper;

//     @Value("${mcp.server.url:http://localhost:8081/mcp}")
//     private String mcpServerUrl;

//     /**
//      * Initialize connection (handshake)
//      */
//     public Map<String, Object> initialize() {
//         Map<String, Object> request = Map.of(
//                 "jsonrpc", "2.0",
//                 "method", "initialize",
//                 "params", Map.of(
//                         "protocolVersion", "2024-11-05",
//                         "clientInfo", Map.of(
//                                 "name", "AyKhedma-MCP-Client",
//                                 "version", "1.0.0")),
//                 "id", UUID.randomUUID().toString());
//         return post(request);
//     }

//     /**
//      * List all tools from MCP server
//      */
//     public Map<String, Object> listTools() {
//         Map<String, Object> request = Map.of(
//                 "jsonrpc", "2.0",
//                 "method", "tools/list",
//                 "id", UUID.randomUUID().toString());
//         return post(request);
//     }

//     /**
//      * Call a tool
//      */
//     public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
//         Map<String, Object> params = Map.of(
//                 "name", toolName,
//                 "arguments", arguments);
//         Map<String, Object> request = Map.of(
//                 "jsonrpc", "2.0",
//                 "method", "tools/call",
//                 "params", params,
//                 "id", UUID.randomUUID().toString());
//         return post(request);
//     }

//     private Map<String, Object> post(Map<String, Object> request) {
//         try {
//             return webClient.post()
//                     .uri(mcpServerUrl)
//                     .bodyValue(request)
//                     .retrieve()
//                     .bodyToMono(Map.class)
//                     .block();
//         } catch (Exception e) {
//             log.error("MCP HTTP request failed: {}", e.getMessage(), e);
//             return null;
//         }
//     }
// }