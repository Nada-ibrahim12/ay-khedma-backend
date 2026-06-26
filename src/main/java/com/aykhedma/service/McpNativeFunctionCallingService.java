// package com.aykhedma.service;

// import com.aykhedma.mcp.client.McpHttpClient;
// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.ai.chat.client.ChatClient;
// import org.springframework.ai.model.function.FunctionCallback;
// import org.springframework.stereotype.Service;

// import java.util.ArrayList;
// import java.util.List;
// import java.util.Map;

// @Service
// @Slf4j
// @RequiredArgsConstructor
// public class McpNativeFunctionCallingService {

//     private final McpHttpClient mcpHttpClient;
//     private final ChatClient chatClient;
//     private final ObjectMapper objectMapper;

//     public String chatWithMcp(String userMessage, String sessionId) {
//         try {
//             // 1. Get tools from MCP server (HTTP)
//             Map<String, Object> toolsResponse = mcpHttpClient.listTools();
//             if (toolsResponse == null || !toolsResponse.containsKey("result")) {
//                 log.warn("Failed to get tools from MCP server");
//                 return "عذراً، حدث خطأ في الاتصال بالخادم.";
//             }

//             // 2. Parse tool schemas and create FunctionCallbacks
//             JsonNode resultNode = objectMapper.valueToTree(toolsResponse.get("result"));
//             JsonNode toolsNode = resultNode.path("tools");
//             List<FunctionCallback> functionCallbacks = new ArrayList<>();

//             for (JsonNode tool : toolsNode) {
//                 String name = tool.path("name").asText();
//                 String description = tool.path("description").asText();
//                 JsonNode inputSchema = tool.path("inputSchema");

//                 // Create a FunctionCallback that calls the MCP tool via HTTP
//                 FunctionCallback callback = FunctionCallback.builder()
//                         .name(name)
//                         .description(description)
//                         .inputSchema(inputSchema) // Pass as JsonNode or Map
//                         .function(input -> {
//                             // Convert input to Map<String, Object>
//                             Map<String, Object> args = objectMapper.convertValue(input, Map.class);
//                             // Call the MCP tool
//                             Map<String, Object> result = mcpHttpClient.callTool(name, args);
//                             // Extract the actual result (the "text" content)
//                             return extractResult(result);
//                         })
//                         .build();

//                 functionCallbacks.add(callback);
//                 log.info("Registered function: {}", name);
//             }

//             // 3. Build a new ChatClient with these functions
//             ChatClient mcpChatClient = chatClient.mutate()
//                     .defaultFunctions(functionCallbacks)
//                     .build();

//             // 4. Send the user message – Gemini will automatically call the function
//             String response = mcpChatClient.prompt()
//                     .user(userMessage)
//                     .call()
//                     .content();

//             return response;

//         } catch (Exception e) {
//             log.error("MCP native function calling failed: {}", e.getMessage(), e);
//             return "عذراً، حدث خطأ. حاول مرة أخرى.";
//         }
//     }

//     private Object extractResult(Map<String, Object> mcpResponse) {
//         // MCP response structure: { "result": { "content": [ { "type": "text", "text":
//         // "..." } ] } }
//         if (mcpResponse == null)
//             return "Error: No response from MCP server.";
//         Map<String, Object> result = (Map<String, Object>) mcpResponse.get("result");
//         if (result == null)
//             return "Error: No result field in MCP response.";
//         List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
//         if (content == null || content.isEmpty())
//             return "Error: No content in MCP response.";
//         Map<String, Object> firstContent = content.get(0);
//         return firstContent.get("text");
//     }
// }