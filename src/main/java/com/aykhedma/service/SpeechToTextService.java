package com.aykhedma.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpeechToTextService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    @Value("#{'${ai.gemini.api-keys:}'.split(',')}")
    private List<String> apiKeys;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    @Value("${ai.gemini.model:gemini-2.5-flash}")
    private String model;

    private final AtomicInteger keyCursor = new AtomicInteger(0);

    private final Map<String, Instant> keyCooldownUntil = new ConcurrentHashMap<>();
    private static final Duration RATE_LIMIT_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration AUTH_ERROR_COOLDOWN = Duration.ofMinutes(10);

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile Instant lastFailureTime = null;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final Duration FAILURE_COOLDOWN = Duration.ofSeconds(30);

    private List<String> getApiKeys() {
        List<String> keys = new ArrayList<>();
        if (apiKeys == null) {
            return keys;
        }
        for (String apiKey : apiKeys) {
            if (StringUtils.hasText(apiKey)) {
                String trimmed = apiKey.trim();
                if (trimmed.length() > 35) {
                    keys.add(trimmed);
                } else {
                    log.warn("Skipping invalid API key format for transcription: {}",
                            trimmed.length() > 15 ? trimmed.substring(0, 15) + "..." : trimmed);
                }
            }
        }
        return keys;
    }

    public boolean isEnabled() {
        return !getApiKeys().isEmpty();
    }

    public String transcribeAudio(MultipartFile audioFile) throws IOException {
        if (audioFile == null || audioFile.isEmpty()) {
            log.warn("Audio file is null or empty");
            return null;
        }

        if (consecutiveFailures.get() >= MAX_CONSECUTIVE_FAILURES) {
            if (lastFailureTime != null &&
                    Duration.between(lastFailureTime, Instant.now()).compareTo(FAILURE_COOLDOWN) < 0) {
                log.warn("Too many consecutive failures, skipping transcription (cooldown: {}s remaining)",
                        FAILURE_COOLDOWN.getSeconds() - Duration.between(lastFailureTime, Instant.now()).getSeconds());
                return null;
            } else {
                consecutiveFailures.set(0);
                lastFailureTime = null;
            }
        }

        String filename = audioFile.getOriginalFilename();
        String contentType = audioFile.getContentType();

        log.info("Processing voice note: filename={}, size={} bytes, type={}",
                filename, audioFile.getSize(), contentType);

        return transcribeWithGemini(audioFile);
    }

    private String transcribeWithGemini(MultipartFile audioFile) throws IOException {
        List<String> apiKeys = getApiKeys();
        if (apiKeys.isEmpty()) {
            log.warn("No valid Gemini API keys configured for voice transcription");
            return null;
        }

        byte[] audioBytes = audioFile.getBytes();
        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);
        String mimeType = detectMimeType(audioFile);
        log.info("Detected MIME type: {} for file: {}", mimeType, audioFile.getOriginalFilename());

        String prompt = """
                قم بتفريغ هذا التسجيل الصوتي إلى نص مكتوب بدقة عالية.

                قواعد مهمة:
                - أخرج ONLY النص المكتوب، لا شيء آخر نهائياً
                - لا تضف أي جمل تمهيدية مثل "هذا هو النص:" أو "ال transcript:"
                - إذا كان المتحدث يتحدث العربية، أخرج النص بالعربية
                - إذا كان يتحدث الإنجليزية، أخرج النص بالإنجليزية
                - حافظ على علامات الترقيم المناسبة
                - إذا كان الصوت غير واضح، اكتب ما تفهمه
                - إذا كان فيه كلمات إنجليزية وسط العربي، اكتبها كما هي
                - الدقة أهم من السرعة
                """;

        String requestBody = String.format("""
                {
                    "contents": [{
                        "parts": [
                            {"text": "%s"},
                            {"inline_data": {"mime_type": "%s", "data": "%s"}}
                        ]
                    }],
                    "generationConfig": {
                        "temperature": 0.0,
                        "responseMimeType": "text/plain"
                    }
                }
                """, escapeJson(prompt), mimeType, base64Audio);

        int size = apiKeys.size();
        int start = Math.floorMod(keyCursor.getAndIncrement(), size);
        int triedKeys = 0;
        int skippedForCooldown = 0;

        for (int offset = 0; offset < size; offset++) {
            int idx = (start + offset) % size;
            String currentKey = apiKeys.get(idx);

            Instant cooldownUntil = keyCooldownUntil.get(currentKey);
            if (cooldownUntil != null && cooldownUntil.isAfter(Instant.now())) {
                skippedForCooldown++;
                log.debug("Skipping transcription key {} - in cooldown until {}", idx, cooldownUntil);
                continue;
            }

            triedKeys++;

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Goog-Api-Key", currentKey);

                String url = baseUrl + "/models/" + model + ":generateContent";
                HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

                log.debug("Attempting transcription with key index {}", idx);
                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        String.class);

                if (response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());

                    JsonNode error = root.path("error");
                    if (!error.isMissingNode()) {
                        String errorMessage = error.path("message").asText("");
                        int errorCode = error.path("code").asInt(0);

                        log.warn("Gemini transcription API error with key {}: {} (code: {})",
                                idx, errorMessage, errorCode);

                        if (errorCode == 429) {
                            keyCooldownUntil.put(currentKey, Instant.now().plus(RATE_LIMIT_COOLDOWN));
                            log.warn("Transcription key {} rate-limited, cooling down for {}s",
                                    idx, RATE_LIMIT_COOLDOWN.getSeconds());
                            continue;
                        } else if (errorCode == 403 || errorCode == 401) {
                            keyCooldownUntil.put(currentKey, Instant.now().plus(AUTH_ERROR_COOLDOWN));
                            log.error("Transcription key {} invalid, cooling down for {}m",
                                    idx, AUTH_ERROR_COOLDOWN.toMinutes());
                            continue;
                        } else if (errorCode == 500 || errorCode == 503) {
                            keyCooldownUntil.put(currentKey, Instant.now().plus(Duration.ofSeconds(5)));
                            continue;
                        }
                        continue;
                    }

                    String transcript = root.path("candidates")
                            .path(0)
                            .path("content")
                            .path("parts")
                            .path(0)
                            .path("text")
                            .asText(null);

                    if (StringUtils.hasText(transcript)) {
                        keyCooldownUntil.remove(currentKey);
                        consecutiveFailures.set(0);
                        lastFailureTime = null;

                        String cleanedTranscript = transcript.trim();
                        log.info("Transcription successful with key {}: {}", idx, cleanedTranscript);
                        return cleanedTranscript;
                    } else {
                        log.warn("No transcript found in Gemini response with key {}", idx);
                    }
                } else {
                    log.warn("Empty response from Gemini with key {}", idx);
                }

            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("Transcription rate limited for key {}: {}", idx, e.getMessage());
                keyCooldownUntil.put(currentKey, Instant.now().plus(RATE_LIMIT_COOLDOWN));
                continue;

            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                log.error("Transcription authentication error for key {}: {}", idx, e.getMessage());
                keyCooldownUntil.put(currentKey, Instant.now().plus(AUTH_ERROR_COOLDOWN));
                continue;

            } catch (HttpClientErrorException.NotFound e) {
                log.warn("Gemini transcription model '{}' not available at '{}' for key {}: {}",
                        model, baseUrl, idx, e.getMessage());
                consecutiveFailures.incrementAndGet();
                lastFailureTime = Instant.now();
                continue;

            } catch (Exception e) {
                log.error("Transcription failed with key {}: {}", idx, e.getMessage());
                consecutiveFailures.incrementAndGet();
                lastFailureTime = Instant.now();
                continue;
            }
        }

        if (triedKeys == 0) {
            log.warn("All {} transcription API key(s) are in cooldown ({} skipped)",
                    size, skippedForCooldown);
        } else {
            log.warn("All {} eligible transcription API key(s) failed", triedKeys);
        }

        consecutiveFailures.incrementAndGet();
        lastFailureTime = Instant.now();

        return null;
    }

    private String detectMimeType(MultipartFile audioFile) {
        String filename = audioFile.getOriginalFilename();
        String contentType = audioFile.getContentType();

        if (contentType != null && !contentType.isEmpty()) {
            return contentType;
        }

        if (filename != null) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            switch (ext) {
                case "ogg":
                    return "audio/ogg";
                case "mp3":
                    return "audio/mpeg";
                case "wav":
                    return "audio/wav";
                case "m4a":
                    return "audio/mp4";
                case "aac":
                    return "audio/aac";
                case "flac":
                    return "audio/flac";
                case "webm":
                    return "audio/webm";
                default:
                    return "audio/mpeg";
            }
        }

        return "audio/mpeg";
    }

    private String escapeJson(String value) {
        if (value == null)
            return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}