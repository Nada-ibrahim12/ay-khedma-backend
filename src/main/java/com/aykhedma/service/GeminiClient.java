package com.aykhedma.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class GeminiClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    private volatile ChatClient chatClient;

    @Value("#{'${ai.gemini.api-keys:}'.split(',')}")
    private List<String> apiKeys;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    @Value("${ai.gemini.model:gemini-2.0-flash-exp}")
    private String model;

    @Value("${ai.gemini.use-spring-ai:false}")
    private boolean useSpringAi;

    private List<String> getApiKeys() {
        List<String> keys = new ArrayList<>();
        if (apiKeys == null) {
            return keys;
        }
        for (String apiKey : apiKeys) {
            if (StringUtils.hasText(apiKey)) {
                keys.add(apiKey.trim());
            }
        }
        return keys;
    }

    public boolean isEnabled() {
        return useSpringAi || !getApiKeys().isEmpty();
    }

    /**
     * Single-turn generation - kept for backward compatibility
     */
    public String generateJson(String prompt) {
        return generateJson(List.of(new ConversationTurn("user", prompt)), null);
    }

    /**
     * Multi-turn generation with optional system prompt
     *
     * @param history      ordered list of previous turns (role + text). Must
     *                     alternate user/model.
     * @param systemPrompt optional system instruction (can be null)
     * @return raw JSON text response from Gemini, or null on failure
     */
    public String generateJson(List<ConversationTurn> history, String systemPrompt) {
        if (!isEnabled()) {
            log.debug("Gemini is disabled");
            return null;
        }

        String springAiResponse = generateWithSpringAi(history, systemPrompt);
        if (StringUtils.hasText(springAiResponse)) {
            return springAiResponse;
        }

        return generateWithWebClient(history, systemPrompt);
    }

    private String generateWithWebClient(List<ConversationTurn> history, String systemPrompt) {
        List<String> apiKeys = getApiKeys();
        if (apiKeys.isEmpty()) {
            log.debug("No API keys configured");
            return null;
        }

        // Build the request once
        List<GeminiContent> contents = new ArrayList<>();

        // Add system instruction if provided
        if (StringUtils.hasText(systemPrompt)) {
            contents.add(new GeminiContent("user", List.of(new GeminiPart(systemPrompt))));
            contents.add(new GeminiContent("model",
                    List.of(new GeminiPart("Understood. I will follow these instructions."))));
        }

        // Add conversation history with correct roles
        for (ConversationTurn turn : history) {
            String geminiRole = "user".equals(turn.role()) ? "user" : "model";
            contents.add(new GeminiContent(geminiRole, List.of(new GeminiPart(turn.text()))));
        }

        GeminiRequest request = new GeminiRequest(
                contents,
                new GeminiGenerationConfig(0.2, "application/json"));

        for (int keyIndex = 0; keyIndex < apiKeys.size(); keyIndex++) {
            String currentKey = apiKeys.get(keyIndex);

            try {
                WebClient client = webClientBuilder.baseUrl(baseUrl).build();

                String response = client.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/models/{model}:generateContent")
                                .build(model))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-goog-api-key", currentKey)
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (!StringUtils.hasText(response)) {
                    log.debug("Empty response from Gemini with key {}", keyIndex + 1);
                    continue;
                }

                JsonNode root = objectMapper.readTree(response);

                JsonNode error = root.path("error");
                if (!error.isMissingNode()) {
                    String errorMessage = error.path("message").asText("");
                    log.warn("Gemini API error with key {}: {}", keyIndex + 1, errorMessage);
                    // try next key
                    continue;
                }

                JsonNode textNode = root.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text");

                if (textNode.isMissingNode() || textNode.isNull()) {
                    log.debug("No text in Gemini response with key {}", keyIndex + 1);
                    continue;
                }

                log.debug("Successfully used API key {}", keyIndex + 1);
                return textNode.asText();

            } catch (Exception ex) {
                log.warn("Gemini WebClient request failed with key {}: {}", keyIndex + 1, ex.getMessage());
                // try next key
                continue;
            }
        }

        log.warn("All {} API keys failed", apiKeys.size());
        return null;
    }

    private String generateWithSpringAi(List<ConversationTurn> history, String systemPrompt) {
        if (!useSpringAi) {
            return null;
        }

        ChatClient localClient = getChatClient();
        if (localClient == null) {
            log.debug("Spring AI ChatClient not available");
            return null;
        }

        String conversationPrompt = buildConversationPrompt(history, systemPrompt);
        if (!StringUtils.hasText(conversationPrompt)) {
            return null;
        }

        try {
            return localClient.prompt()
                    .user(conversationPrompt)
                    .call()
                    .content();
        } catch (Exception ex) {
            log.warn("Spring AI request failed: {}", ex.getMessage());
            return null;
        }
    }

    private String buildConversationPrompt(List<ConversationTurn> history, String systemPrompt) {
        StringBuilder builder = new StringBuilder();

        if (StringUtils.hasText(systemPrompt)) {
            builder.append("System: ").append(systemPrompt).append("\n\n");
        }

        for (ConversationTurn turn : history) {
            if (turn == null || !StringUtils.hasText(turn.text())) {
                continue;
            }
            String role = StringUtils.hasText(turn.role()) ? turn.role() : "user";
            builder.append(role).append(": ").append(turn.text().trim()).append("\n");
        }

        return builder.toString().trim();
    }

    private ChatClient getChatClient() {
        ChatClient current = this.chatClient;
        if (current != null) {
            return current;
        }

        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            return null;
        }

        synchronized (this) {
            if (this.chatClient == null) {
                this.chatClient = builder.build();
            }
            return this.chatClient;
        }
    }

    public record ConversationTurn(String role, String text) {
    }

    private record GeminiRequest(List<GeminiContent> contents, GeminiGenerationConfig generationConfig) {
    }

    private record GeminiContent(String role, List<GeminiPart> parts) {
    }

    private record GeminiPart(String text) {
    }

    private record GeminiGenerationConfig(Double temperature, String responseMimeType) {
    }
}