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
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpeechToTextService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    @Value("${ai.gemini.model:gemini-2.5-flash}")
    private String model;

    public String transcribeAudio(MultipartFile audioFile) throws IOException {
        if (audioFile == null || audioFile.isEmpty()) {
            log.warn("Audio file is null or empty");
            return null;
        }

        String filename = audioFile.getOriginalFilename();
        String contentType = audioFile.getContentType();

        log.info("Processing voice note: filename={}, size={} bytes, type={}",
                filename, audioFile.getSize(), contentType);

        return transcribeWithGemini(audioFile);
    }

    private String transcribeWithGemini(MultipartFile audioFile) throws IOException {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Gemini API key not configured for voice transcription");
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);

        String url = baseUrl + "/models/" + model + ":generateContent";
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String transcript = root.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text")
                        .asText(null);

                if (StringUtils.hasText(transcript)) {
                    String cleanedTranscript = transcript.trim();
                    log.info("Transcription successful: {}", cleanedTranscript);
                    System.out.println("\uD83D\uDD0A ARABIC TEXT: " + cleanedTranscript);
                    System.out.println("=== TRANSCRIPTION START ===");
                    System.out.println(cleanedTranscript);
                    System.out.println("=== TRANSCRIPTION END ===");
                    return cleanedTranscript;
                } else {
                    log.warn("No transcript found in Gemini response");
                }
            } else {
                log.warn("Empty response from Gemini");
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Gemini transcription model '{}' is not available at '{}': {}", model, baseUrl, e.getMessage());
        } catch (Exception e) {
            log.error("Gemini transcription failed: {}", e.getMessage(), e);
        }

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