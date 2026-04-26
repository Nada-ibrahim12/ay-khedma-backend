package com.aykhedma.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
@RequiredArgsConstructor
public class GeminiClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    @Value("${ai.gemini.model:gemini-flash-latest}")
    private String model;

    public boolean isEnabled() {
        return StringUtils.hasText(apiKey);
    }

    public String generateJson(String prompt) {
        return generateJson(java.util.List.of(new ConversationTurn("user", prompt)));
    }

    /**
     * Sends a multi-turn conversation to Gemini so the model understands context.
     *
     * @param history ordered list of previous turns (role + text). Must alternate user/model.
     * @return raw JSON text response from Gemini, or null on failure.
     */
    public String generateJson(java.util.List<ConversationTurn> history) {
        if (!isEnabled()) {
            return null;
        }

        try {
            WebClient client = webClientBuilder.baseUrl(baseUrl).build();

            java.util.List<GeminiContent> contents = history.stream()
                    .map(turn -> new GeminiContent(turn.role(),
                            java.util.List.of(new GeminiPart(turn.text()))))
                    .toList();

            String response = client.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/{model}:generateContent")
                            .build(model))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-goog-api-key", apiKey)
                    .bodyValue(new GeminiRequest(contents, new GeminiGenerationConfig(0.2, "application/json")))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (!StringUtils.hasText(response)) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode textNode = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");

            if (textNode.isMissingNode() || textNode.isNull()) {
                return null;
            }

            return textNode.asText();
        } catch (Exception ex) {
            log.warn("Gemini request failed: {}", ex.getMessage());
            return null;
        }
    }

    public record ConversationTurn(String role, String text) {
    }

    private record GeminiRequest(java.util.List<GeminiContent> contents,
            GeminiGenerationConfig generationConfig) {
    }

    private record GeminiContent(String role, java.util.List<GeminiPart> parts) {
    }

    private record GeminiPart(String text) {
    }

    private record GeminiGenerationConfig(Double temperature, String responseMimeType) {
    }
}