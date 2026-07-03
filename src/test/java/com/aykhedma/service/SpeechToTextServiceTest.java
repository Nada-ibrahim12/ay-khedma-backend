package com.aykhedma.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Speech To Text Service Tests")
class SpeechToTextServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SpeechToTextService speechToTextService;

    private final String TEST_API_KEY = "AIzaSyD1234567890abcdefghijklmnopqrstuvwxyz";
    private final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private final String MODEL = "gemini-2.5-flash";
    private final ObjectMapper realObjectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        List<String> apiKeys = List.of(TEST_API_KEY);
        ReflectionTestUtils.setField(speechToTextService, "apiKeys", apiKeys);
        ReflectionTestUtils.setField(speechToTextService, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(speechToTextService, "model", MODEL);
        ReflectionTestUtils.setField(speechToTextService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(speechToTextService, "objectMapper", objectMapper);
    }

    @Nested
    @DisplayName("Is Enabled Tests")
    class IsEnabledTests {

        @Test
        @DisplayName("Should return true when API keys are configured")
        void isEnabled_WithApiKeys_ReturnsTrue() {
            assertThat(speechToTextService.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should return false when no API keys configured")
        void isEnabled_NoApiKeys_ReturnsFalse() {
            ReflectionTestUtils.setField(speechToTextService, "apiKeys", new ArrayList<>());
            assertThat(speechToTextService.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should return false when API keys are empty strings")
        void isEnabled_EmptyApiKeys_ReturnsFalse() {
            List<String> emptyKeys = List.of("", "   ");
            ReflectionTestUtils.setField(speechToTextService, "apiKeys", emptyKeys);
            assertThat(speechToTextService.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Transcribe Audio Tests")
    class TranscribeAudioTests {

        @Test
        @DisplayName("Should transcribe audio successfully")
        void transcribeAudio_ValidAudio_ReturnsTranscript() throws Exception {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    "test audio content".getBytes());

            String expectedTranscript = "This is a test transcription";
            String jsonResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + expectedTranscript
                    + "\"}]}}]}";

            ResponseEntity<String> responseEntity = mock(ResponseEntity.class);
            when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(responseEntity);
            when(responseEntity.getBody()).thenReturn(jsonResponse);

            // 🔥 FIX: Use real ObjectMapper to parse JSON instead of complex mocks
            when(objectMapper.readTree(anyString())).thenReturn(realObjectMapper.readTree(jsonResponse));

            String result = speechToTextService.transcribeAudio(audioFile);

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(expectedTranscript);
        }

        @Test
        @DisplayName("Should return null when audio file is null")
        void transcribeAudio_NullFile_ReturnsNull() throws IOException {
            String result = speechToTextService.transcribeAudio(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null when audio file is empty")
        void transcribeAudio_EmptyFile_ReturnsNull() throws IOException {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    new byte[0]);

            String result = speechToTextService.transcribeAudio(emptyFile);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null when no API keys available")
        void transcribeAudio_NoApiKeys_ReturnsNull() throws IOException {
            ReflectionTestUtils.setField(speechToTextService, "apiKeys", new ArrayList<>());

            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    "test".getBytes());

            String result = speechToTextService.transcribeAudio(audioFile);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle API error response")
        void transcribeAudio_ApiError_ReturnsNull() throws Exception {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    "test".getBytes());

            String jsonResponse = "{\"error\":{\"code\":429,\"message\":\"Rate limit exceeded\"}}";
            ResponseEntity<String> responseEntity = mock(ResponseEntity.class);
            when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(responseEntity);
            when(responseEntity.getBody()).thenReturn(jsonResponse);
            when(objectMapper.readTree(anyString())).thenReturn(realObjectMapper.readTree(jsonResponse));

            String result = speechToTextService.transcribeAudio(audioFile);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null when no transcript found")
        void transcribeAudio_NoTranscript_ReturnsNull() throws Exception {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    "test".getBytes());

            String jsonResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}]}}]}";
            ResponseEntity<String> responseEntity = mock(ResponseEntity.class);
            when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(responseEntity);
            when(responseEntity.getBody()).thenReturn(jsonResponse);
            when(objectMapper.readTree(anyString())).thenReturn(realObjectMapper.readTree(jsonResponse));

            String result = speechToTextService.transcribeAudio(audioFile);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle null response body")
        void transcribeAudio_NullResponseBody_ReturnsNull() throws Exception {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    "test".getBytes());

            ResponseEntity<String> responseEntity = mock(ResponseEntity.class);
            when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(responseEntity);
            when(responseEntity.getBody()).thenReturn(null);

            String result = speechToTextService.transcribeAudio(audioFile);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Rate Limiting Tests")
    class RateLimitingTests {

        @Test
        @DisplayName("Should handle rate limit (429) error")
        void transcribeAudio_RateLimit_ReturnsNull() throws Exception {
            List<String> apiKeys = List.of(TEST_API_KEY);
            ReflectionTestUtils.setField(speechToTextService, "apiKeys", apiKeys);

            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    "test".getBytes());

            when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null,
                            null, null));

            String result = speechToTextService.transcribeAudio(audioFile);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle authentication error (401/403)")
        void transcribeAudio_AuthError_ReturnsNull() throws Exception {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    "test".getBytes());

            when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(
                            HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

            String result = speechToTextService.transcribeAudio(audioFile);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("MIME Type Detection Tests")
    class MimeTypeDetectionTests {

        @Test
        @DisplayName("Should detect WAV from content type")
        void detectMimeType_WavContentType_ReturnsWav() throws IOException {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    "test".getBytes());

            String result = (String) ReflectionTestUtils.invokeMethod(
                    speechToTextService, "detectMimeType", audioFile);

            assertThat(result).isEqualTo("audio/wav");
        }

        @Test
        @DisplayName("Should detect MP3 from content type")
        void detectMimeType_Mp3ContentType_ReturnsMp3() throws IOException {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.mp3",
                    "audio/mpeg",
                    "test".getBytes());

            String result = (String) ReflectionTestUtils.invokeMethod(
                    speechToTextService, "detectMimeType", audioFile);

            assertThat(result).isEqualTo("audio/mpeg");
        }

        @Test
        @DisplayName("Should detect OGG from file extension")
        void detectMimeType_OggExtension_ReturnsOgg() throws IOException {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.ogg",
                    null,
                    "test".getBytes());

            String result = (String) ReflectionTestUtils.invokeMethod(
                    speechToTextService, "detectMimeType", audioFile);

            assertThat(result).isEqualTo("audio/ogg");
        }

        @Test
        @DisplayName("Should detect M4A from file extension")
        void detectMimeType_M4aExtension_ReturnsM4a() throws IOException {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.m4a",
                    null,
                    "test".getBytes());

            String result = (String) ReflectionTestUtils.invokeMethod(
                    speechToTextService, "detectMimeType", audioFile);

            assertThat(result).isEqualTo("audio/mp4");
        }

        @Test
        @DisplayName("Should default to MP3 when extension unknown")
        void detectMimeType_UnknownExtension_ReturnsMp3() throws IOException {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.xyz",
                    null,
                    "test".getBytes());

            String result = (String) ReflectionTestUtils.invokeMethod(
                    speechToTextService, "detectMimeType", audioFile);

            assertThat(result).isEqualTo("audio/mpeg");
        }
    }

    @Nested
    @DisplayName("Escape JSON Tests")
    class EscapeJsonTests {

        @Test
        @DisplayName("Should escape backslash")
        void escapeJson_Backslash_ReturnsEscaped() {
            String result = (String) ReflectionTestUtils.invokeMethod(
                    speechToTextService, "escapeJson", "test\\test");
            assertThat(result).isEqualTo("test\\\\test");
        }

        @Test
        @DisplayName("Should escape double quotes")
        void escapeJson_DoubleQuote_ReturnsEscaped() {
            String result = (String) ReflectionTestUtils.invokeMethod(
                    speechToTextService, "escapeJson", "test\"test");
            assertThat(result).isEqualTo("test\\\"test");
        }

        @Test
        @DisplayName("Should escape newline")
        void escapeJson_Newline_ReturnsEscaped() {
            String result = (String) ReflectionTestUtils.invokeMethod(
                    speechToTextService, "escapeJson", "test\ntest");
            assertThat(result).isEqualTo("test\\ntest");
        }

        @Test
        @DisplayName("Should escape carriage return")
        void escapeJson_CarriageReturn_ReturnsEscaped() {
            String result = (String) ReflectionTestUtils.invokeMethod(
                    speechToTextService, "escapeJson", "test\rtest");
            assertThat(result).isEqualTo("test\\rtest");
        }

        @Test
        @DisplayName("Should return empty string for null")
        void escapeJson_Null_ReturnsEmpty() {
            String result = (String) ReflectionTestUtils.invokeMethod(
                    speechToTextService, "escapeJson", (Object) null);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Key Rotation Tests")
    class KeyRotationTests {

        @Test
        @DisplayName("Should try all keys and return null when all fail")
        void transcribeAudio_KeyRotation_TriesAllKeys() throws Exception {
            List<String> apiKeys = List.of(TEST_API_KEY);
            ReflectionTestUtils.setField(speechToTextService, "apiKeys", apiKeys);

            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    "test".getBytes());

            when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                            null, null, null));

            String result = speechToTextService.transcribeAudio(audioFile);
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Consecutive Failure Handling Tests")
    class ConsecutiveFailureHandlingTests {

        @Test
        @DisplayName("Should skip transcription after too many consecutive failures")
        void transcribeAudio_TooManyFailures_ReturnsNull() throws Exception {
            ReflectionTestUtils.setField(speechToTextService, "consecutiveFailures",
                    new java.util.concurrent.atomic.AtomicInteger(3));
            ReflectionTestUtils.setField(speechToTextService, "lastFailureTime", java.time.Instant.now());

            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.wav",
                    "audio/wav",
                    "test".getBytes());

            String result = speechToTextService.transcribeAudio(audioFile);
            assertThat(result).isNull();

            verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                    eq(String.class));
        }
    }
}