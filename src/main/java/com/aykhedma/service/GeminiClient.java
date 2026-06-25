package com.aykhedma.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetrySpec;

import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
public class GeminiClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    private volatile ChatClient chatClient;

    private final AtomicInteger keyCursor = new AtomicInteger(0);
    private final Map<String, Instant> keyCooldownUntil = new ConcurrentHashMap<>();

    // track consecutive DNS failures
    private final AtomicInteger consecutiveDnsFailures = new AtomicInteger(0);
    private volatile Instant lastDnsFailureTime = null;
    private static final int MAX_DNS_FAILURES = 3;
    private static final Duration DNS_COOLDOWN = Duration.ofSeconds(30);

    @Value("#{'${ai.gemini.api-keys:}'.split(',')}")
    private List<String> apiKeys;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    @Value("${ai.gemini.model:gemini-2.5-flash}")
    private String model;

    @Value("${ai.gemini.use-spring-ai:false}")
    private boolean useSpringAi;

    // cache for successful responses (reduce duplicate calls)
    private final Map<String, CachedResponse> responseCache = new ConcurrentHashMap<>();
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private List<String> getApiKeys() {
        List<String> keys = new ArrayList<>();
        if (apiKeys == null) {
            return keys;
        }
        for (String apiKey : apiKeys) {
            if (StringUtils.hasText(apiKey)) {
                String trimmed = apiKey.trim();
                // Only include valid Gemini keys
                if (trimmed.startsWith("AIzaSy") && trimmed.length() > 35) {
                    keys.add(trimmed);
                } else {
                    log.warn("Skipping invalid API key format: {}",
                            trimmed.length() > 15 ? trimmed.substring(0, 15) + "..." : trimmed);
                }
            }
        }
        return keys;
    }

    public boolean isEnabled() {
        return useSpringAi || !getApiKeys().isEmpty();
    }

    public String generateJson(String prompt) {
        return generateJson(List.of(new ConversationTurn("user", prompt)), null);
    }

    public String generateJson(List<ConversationTurn> history, String systemPrompt) {
        if (!isEnabled()) {
            log.debug("Gemini is disabled");
            return null;
        }

        //  if we are in DNS failure cooldown
        if (consecutiveDnsFailures.get() >= MAX_DNS_FAILURES) {
            if (lastDnsFailureTime != null &&
                    Duration.between(lastDnsFailureTime, Instant.now()).compareTo(DNS_COOLDOWN) < 0) {
                log.warn("DNS resolution failing, skipping Gemini request (cooldown: {}s remaining)",
                        DNS_COOLDOWN.getSeconds() - Duration.between(lastDnsFailureTime, Instant.now()).getSeconds());
                return null;
            } else {
                // Reset after cooldown
                consecutiveDnsFailures.set(0);
                lastDnsFailureTime = null;
            }
        }

        // check cache for identical requests
        String cacheKey = buildCacheKey(history, systemPrompt);
        if (cacheKey != null) {
            CachedResponse cached = responseCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                log.debug("Returning cached Gemini response");
                return cached.response;
            }
        }

        String springAiResponse = generateWithSpringAi(history, systemPrompt);
        if (StringUtils.hasText(springAiResponse)) {
            return springAiResponse;
        }

        String response = generateWithWebClient(history, systemPrompt);

        // Cache successful response
        if (StringUtils.hasText(response) && cacheKey != null) {
            responseCache.put(cacheKey, new CachedResponse(response));
        }

        return response;
    }

    private String buildCacheKey(List<ConversationTurn> history, String systemPrompt) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        // only cache simple single-turn requests
        if (history.size() == 1 && !StringUtils.hasText(systemPrompt)) {
            String text = history.get(0).text();
            if (StringUtils.hasText(text) && text.length() < 100) {
                return "single:" + text.trim().toLowerCase();
            }
        }
        return null;
    }

    private String generateWithWebClient(List<ConversationTurn> history, String systemPrompt) {
        List<String> apiKeys = getApiKeys();
        int size = apiKeys.size();
        if (size == 0) {
            log.debug("No valid API keys configured");
            return null;
        }

        // build the request once
        List<GeminiContent> contents = new ArrayList<>();

        if (StringUtils.hasText(systemPrompt)) {
            contents.add(new GeminiContent("user", List.of(new GeminiPart(systemPrompt))));
            contents.add(new GeminiContent("model",
                    List.of(new GeminiPart("Understood. I will follow these instructions."))));
        }

        for (ConversationTurn turn : history) {
            String geminiRole = "user".equals(turn.role()) ? "user" : "model";
            contents.add(new GeminiContent(geminiRole, List.of(new GeminiPart(turn.text()))));
        }

        GeminiRequest request = new GeminiRequest(
                contents,
                new GeminiGenerationConfig(0.2, "application/json"));

        // start from next key in rotation
        int start = Math.floorMod(keyCursor.getAndIncrement(), size);
        int triedKeys = 0;
        int skippedForCooldown = 0;

        for (int offset = 0; offset < size; offset++) {
            int idx = (start + offset) % size;
            String currentKey = apiKeys.get(idx);

            Instant cooldownUntil = keyCooldownUntil.get(currentKey);
            if (cooldownUntil != null && cooldownUntil.isAfter(Instant.now())) {
                skippedForCooldown++;
                continue;
            }

            triedKeys++;

            try {
                String response = webClientBuilder
                        .baseUrl(baseUrl)
                        .build()
                        .post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/models/{model}:generateContent")
                                .build(model))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-goog-api-key", currentKey)
                        .bodyValue(request)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(
                                        new GeminiHttpException(clientResponse.statusCode().value(), body))))
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(30))
                        // Retry on 429 with backoff
                        .retryWhen(RetrySpec.fixedDelay(2, Duration.ofSeconds(2))
                                .filter(throwable -> throwable instanceof GeminiHttpException &&
                                        ((GeminiHttpException) throwable).statusCode == 429)
                                .onRetryExhaustedThrow((spec, signal) -> {
                                    log.debug("Retry exhausted for key {}, continuing to next key", idx);
                                    return signal.failure();
                                }))
                        .block();

                if (!StringUtils.hasText(response)) {
                    log.debug("Empty response from Gemini with key index {}", idx);
                    continue;
                }

                JsonNode root = objectMapper.readTree(response);
                JsonNode error = root.path("error");

                if (!error.isMissingNode()) {
                    String errorMessage = error.path("message").asText("");
                    int errorCode = error.path("code").asInt(0);

                    log.warn("Gemini API error with key {}: {} (code: {})", idx, errorMessage, errorCode);

                    if (errorCode == 429) {
                        keyCooldownUntil.put(currentKey, Instant.now().plus(Duration.ofSeconds(60)));
                        continue;
                    } else if (errorCode == 403 || errorCode == 401) {
                        keyCooldownUntil.put(currentKey, Instant.now().plus(Duration.ofMinutes(10)));
                        log.error("Key {} invalid, cooling down for 10 min", idx);
                        continue;
                    } else if (errorCode == 500 || errorCode == 503) {
                        keyCooldownUntil.put(currentKey, Instant.now().plus(Duration.ofSeconds(5)));
                        continue;
                    }
                    continue;
                }

                JsonNode textNode = root.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text");

                if (textNode.isMissingNode() || textNode.isNull()) {
                    log.debug("No text in Gemini response with key index {}", idx);
                    continue;
                }

                // Success - clear cooldown
                keyCooldownUntil.remove(currentKey);
                // Reset DNS failures on success
                consecutiveDnsFailures.set(0);
                lastDnsFailureTime = null;

                log.debug("Successfully used API key index {}", idx);
                return textNode.asText();

            } catch (WebClientRequestException ex) {
                // DNS or network error
                if (ex.getCause() instanceof UnknownHostException ||
                        ex.getMessage().contains("Failed to resolve")) {
                    log.warn("DNS resolution failed for key {}: {}", idx, ex.getMessage());
                    consecutiveDnsFailures.incrementAndGet();
                    lastDnsFailureTime = Instant.now();

                    if (consecutiveDnsFailures.get() >= MAX_DNS_FAILURES) {
                        log.error("Multiple DNS failures detected, stopping Gemini requests temporarily");
                        return null;
                    }
                } else {
                    log.warn("Network error with key {}: {}", idx, ex.getMessage());
                }
                continue;

            } catch (GeminiHttpException httpEx) {
                int status = httpEx.statusCode;
                if (status == 429) {
                    keyCooldownUntil.put(currentKey, Instant.now().plus(Duration.ofSeconds(60)));
                } else if (status >= 500) {
                    keyCooldownUntil.put(currentKey, Instant.now().plus(Duration.ofSeconds(5)));
                }
                continue;

            } catch (Exception ex) {
                log.warn("Error with key {}: {}", idx, ex.getMessage());
                continue;
            }
        }

        if (triedKeys == 0) {
            log.warn("All {} API key(s) are in cooldown ({} skipped)", size, skippedForCooldown);
        } else {
            log.warn("All {} eligible API key(s) failed", triedKeys);
        }
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

    public void clearCache() {
        responseCache.clear();
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

    private static class GeminiHttpException extends RuntimeException {
        final int statusCode;
        final String body;

        GeminiHttpException(int statusCode, String body) {
            super("Gemini HTTP " + statusCode + ": " + body);
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private static class CachedResponse {
        final String response;
        final Instant expiresAt;

        CachedResponse(String response) {
            this.response = response;
            this.expiresAt = Instant.now().plus(CACHE_TTL);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}