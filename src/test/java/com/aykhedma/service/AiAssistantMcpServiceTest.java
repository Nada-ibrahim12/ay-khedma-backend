package com.aykhedma.service;

import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.mcp.server.McpServer;
import com.aykhedma.model.chat.ChatMessage;
import com.aykhedma.model.chat.ChatResponseType;
import com.aykhedma.model.chat.ChatSession;
import com.aykhedma.model.chat.MessageRole;
import com.aykhedma.model.chat.MessageType;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.ChatMessageRepository;
import com.aykhedma.repository.ChatSessionRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI Assistant MCP Service Tests")
class AiAssistantMcpServiceTest {

    @Mock
    private AiAssistantServiceImpl baseService;

    @Mock
    private AiAssistantOldService oldService;

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private SpeechToTextService speechToTextService;

    @Mock
    private McpServer mcpServer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private AiAssistantMcpService aiAssistantMcpService;

    private final Long USER_ID = 1L;
    private final String SESSION_ID = "session-123";
    private final String TEST_MESSAGE = "I need a plumber";

    private User mockUser;
    private Consumer mockConsumer;
    private ChatSession mockSession;
    private ChatMessage mockMessage;
    private AiChatRequest mockRequest;
    private Location mockLocation;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(USER_ID);
        mockUser.setName("Test User");
        mockUser.setEmail("test@example.com");
        mockUser.setRole(UserType.CONSUMER);

        mockConsumer = new Consumer();
        mockConsumer.setId(USER_ID);
        mockConsumer.setName("Test Consumer");
        mockConsumer.setEmail("test@example.com");
        mockConsumer.setRole(UserType.CONSUMER);

        mockLocation = new Location();
        mockLocation.setLatitude(30.0444);
        mockLocation.setLongitude(31.2357);
        mockConsumer.setLocation(mockLocation);

        mockSession = ChatSession.builder()
                .sessionId(SESSION_ID)
                .userId(USER_ID)
                .isActive(true)
                .detectedLanguage("ar")
                .startTime(LocalDateTime.now())
                .build();

        mockMessage = ChatMessage.builder()
                .id("1")
                .chatSession(mockSession)
                .senderId(USER_ID)
                .senderRole(MessageRole.USER)
                .content(TEST_MESSAGE)
                .type(MessageType.TEXT)
                .isRead(true)
                .timestamp(LocalDateTime.now())
                .build();

        mockRequest = AiChatRequest.builder()
                .sessionId(SESSION_ID)
                .message(TEST_MESSAGE)
                .build();

        ReflectionTestUtils.setField(aiAssistantMcpService, "baseService", baseService);
    }

    @Nested
    @DisplayName("Chat With MCP Tests")
    class ChatWithMcpTests {

        @Test
        @DisplayName("Should process text message successfully")
        void chatWithMcp_TextMessage_ReturnsChatResponse() throws Exception {
            String toolResponse = "{\"tool\":\"search_providers\",\"arguments\":{\"serviceTypes\":[\"Plumbing\"]},\"needsClarification\":false,\"reply\":\"Searching for providers\"}";

            when(baseService.resolveSession(any(), any())).thenReturn(mockSession);
            when(baseService.detectLanguage(anyString())).thenReturn("en");
            when(baseService.getRecentHistory(anyList(), anyInt())).thenReturn(new ArrayList<>());
            when(baseService.getCachedServiceTypes()).thenReturn(new ArrayList<>());
            when(baseService.getCachedCategories()).thenReturn(new ArrayList<>());
            when(baseService.buildConversationContext(anyList())).thenReturn("Context");
            when(baseService.toConversationTurns(anyList())).thenReturn(new ArrayList<>());
            when(geminiClient.isEnabled()).thenReturn(true);
            when(geminiClient.generateJson(anyList(), anyString())).thenReturn(toolResponse);
            when(baseService.extractJson(anyString())).thenReturn(toolResponse);
            when(objectMapper.readTree(anyString())).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));
            when(mcpServer.getToolSchemas()).thenReturn(new ArrayList<>());

            Map<String, Object> mcpResponse = new HashMap<>();
            Map<String, Object> result = new HashMap<>();
            result.put("isError", false);
            Map<String, Object> content = new HashMap<>();
            content.put("text", "{\"providers\":[]}");
            result.put("content", List.of(content));
            mcpResponse.put("result", result);
            when(mcpServer.handleRequest(anyMap())).thenReturn(mcpResponse);

            when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(mockMessage);
            doNothing().when(baseService).saveAssistantMessage(any(), any());

            when(oldService.chatWithExisting(any(), any())).thenReturn(
                    ChatResponse.builder()
                            .sessionId(SESSION_ID)
                            .message("Fallback response")
                            .build());

            ChatResponse response = aiAssistantMcpService.chatWithMcp(mockRequest, mockUser);

            assertThat(response).isNotNull();
            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
        }

        @Test
        @DisplayName("Should process voice note successfully")
        void chatWithMcp_VoiceNote_ReturnsChatResponse() throws Exception {
            MockMultipartFile voiceNote = new MockMultipartFile(
                    "voiceNote",
                    "voice.wav",
                    "audio/wav",
                    "test audio content".getBytes());

            AiChatRequest voiceRequest = AiChatRequest.builder()
                    .sessionId(SESSION_ID)
                    .voiceNote(voiceNote)
                    .build();

            String toolResponse = "{\"tool\":\"search_providers\",\"arguments\":{\"serviceTypes\":[\"Plumbing\"]},\"needsClarification\":false,\"reply\":\"Searching for providers\"}";

            when(speechToTextService.transcribeAudio(any())).thenReturn("Transcribed text");
            when(baseService.resolveSession(any(), any())).thenReturn(mockSession);
            when(baseService.detectLanguage(anyString())).thenReturn("en");
            when(baseService.getRecentHistory(anyList(), anyInt())).thenReturn(new ArrayList<>());
            when(baseService.getCachedServiceTypes()).thenReturn(new ArrayList<>());
            when(baseService.getCachedCategories()).thenReturn(new ArrayList<>());
            when(baseService.buildConversationContext(anyList())).thenReturn("Context");
            when(baseService.toConversationTurns(anyList())).thenReturn(new ArrayList<>());
            when(geminiClient.isEnabled()).thenReturn(true);
            when(geminiClient.generateJson(anyList(), anyString())).thenReturn(toolResponse);
            when(baseService.extractJson(anyString())).thenReturn(toolResponse);
            when(objectMapper.readTree(anyString())).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));
            when(mcpServer.getToolSchemas()).thenReturn(new ArrayList<>());

            Map<String, Object> mcpResponse = new HashMap<>();
            Map<String, Object> result = new HashMap<>();
            result.put("isError", false);
            Map<String, Object> content = new HashMap<>();
            content.put("text", "{\"providers\":[]}");
            result.put("content", List.of(content));
            mcpResponse.put("result", result);
            when(mcpServer.handleRequest(anyMap())).thenReturn(mcpResponse);

            when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(mockMessage);
            doNothing().when(baseService).saveAssistantMessage(any(), any());

            when(oldService.chatWithExisting(any(), any())).thenReturn(
                    ChatResponse.builder()
                            .sessionId(SESSION_ID)
                            .message("Fallback response")
                            .build());

            ChatResponse response = aiAssistantMcpService.chatWithMcp(voiceRequest, mockUser);

            assertThat(response).isNotNull();
            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
            verify(speechToTextService).transcribeAudio(any());
        }

        @Test
        @DisplayName("Should handle voice transcription failure")
        void chatWithMcp_VoiceTranscriptionFailed_ReturnsClarification() throws IOException {
            MockMultipartFile voiceNote = new MockMultipartFile(
                    "voiceNote",
                    "voice.wav",
                    "audio/wav",
                    "test audio content".getBytes());

            AiChatRequest voiceRequest = AiChatRequest.builder()
                    .sessionId(SESSION_ID)
                    .voiceNote(voiceNote)
                    .build();

            when(speechToTextService.transcribeAudio(any())).thenReturn("");
            when(baseService.resolveSession(any(), any())).thenReturn(mockSession);
            when(baseService.detectLanguage(anyString())).thenReturn("ar");
            when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(mockMessage);
            doNothing().when(baseService).saveAssistantMessage(any(), any());

            ChatResponse response = aiAssistantMcpService.chatWithMcp(voiceRequest, mockUser);

            assertThat(response).isNotNull();
            assertThat(response.getResponseType()).isEqualTo(ChatResponseType.CLARIFICATION);
            assertThat(response.getMessage()).contains("عذراً");
        }

        @Test
        @DisplayName("Should handle voice transcription error")
        void chatWithMcp_VoiceTranscriptionError_ReturnsError() throws IOException {
            MockMultipartFile voiceNote = new MockMultipartFile(
                    "voiceNote",
                    "voice.wav",
                    "audio/wav",
                    "test audio content".getBytes());

            AiChatRequest voiceRequest = AiChatRequest.builder()
                    .sessionId(SESSION_ID)
                    .voiceNote(voiceNote)
                    .build();

            when(speechToTextService.transcribeAudio(any())).thenThrow(new IOException("Transcription failed"));
            when(baseService.resolveSession(any(), any())).thenReturn(mockSession);

            ChatResponse response = aiAssistantMcpService.chatWithMcp(voiceRequest, mockUser);

            assertThat(response).isNotNull();
            assertThat(response.getResponseType()).isEqualTo(ChatResponseType.ERROR);
            assertThat(response.getMessage()).contains("خطأ");
        }

        @Test
        @DisplayName("Should throw BadRequestException when message is empty")
        void chatWithMcp_EmptyMessage_ThrowsException() {
            AiChatRequest emptyRequest = AiChatRequest.builder()
                    .sessionId(SESSION_ID)
                    .message("")
                    .build();

            assertThatThrownBy(() -> aiAssistantMcpService.chatWithMcp(emptyRequest, mockUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Message or voice note is required");
        }

        @Test
        @DisplayName("Should fall back to old service when Gemini is disabled")
        void chatWithMcp_GeminiDisabled_FallsBackToOldService() {
            when(baseService.resolveSession(any(), any())).thenReturn(mockSession);
            when(baseService.detectLanguage(anyString())).thenReturn("en");
            when(geminiClient.isEnabled()).thenReturn(false);
            when(oldService.chatWithExisting(any(), any())).thenReturn(
                    ChatResponse.builder()
                            .sessionId(SESSION_ID)
                            .message("Fallback response")
                            .build());

            ChatResponse response = aiAssistantMcpService.chatWithMcp(mockRequest, mockUser);

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Fallback response");
            verify(oldService).chatWithExisting(mockRequest, mockUser);
        }

        @Test
        @DisplayName("Should handle tool response with no tool name")
        void chatWithMcp_NoToolName_FallsBackToOldService() throws Exception {
            String toolResponse = "{\"arguments\":{\"serviceTypes\":[\"Plumbing\"]},\"needsClarification\":false,\"reply\":\"Searching\"}";

            when(baseService.resolveSession(any(), any())).thenReturn(mockSession);
            when(baseService.detectLanguage(anyString())).thenReturn("en");
            when(baseService.getRecentHistory(anyList(), anyInt())).thenReturn(new ArrayList<>());
            when(baseService.getCachedServiceTypes()).thenReturn(new ArrayList<>());
            when(baseService.getCachedCategories()).thenReturn(new ArrayList<>());
            when(baseService.buildConversationContext(anyList())).thenReturn("Context");
            when(baseService.toConversationTurns(anyList())).thenReturn(new ArrayList<>());
            when(geminiClient.isEnabled()).thenReturn(true);
            when(geminiClient.generateJson(anyList(), anyString())).thenReturn(toolResponse);
            when(baseService.extractJson(anyString())).thenReturn(toolResponse);
            when(objectMapper.readTree(anyString())).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));
            when(oldService.chatWithExisting(any(), any())).thenReturn(
                    ChatResponse.builder()
                            .sessionId(SESSION_ID)
                            .message("Fallback")
                            .build());

            ChatResponse response = aiAssistantMcpService.chatWithMcp(mockRequest, mockUser);

            assertThat(response).isNotNull();
            verify(oldService).chatWithExisting(any(), any());
        }

        @Test
        @DisplayName("Should handle MCP chat exception and fallback")
        void chatWithMcp_Exception_FallsBackToOldService() {
            when(baseService.resolveSession(any(), any())).thenThrow(new RuntimeException("Test error"));
            when(oldService.chatWithExisting(any(), any())).thenReturn(
                    ChatResponse.builder()
                            .sessionId(SESSION_ID)
                            .message("Fallback")
                            .build());

            ChatResponse response = aiAssistantMcpService.chatWithMcp(mockRequest, mockUser);

            assertThat(response).isNotNull();
            verify(oldService).chatWithExisting(any(), any());
        }
    }

    @Nested
    @DisplayName("Build MCP Request Tests")
    class BuildMcpRequestTests {

        @Test
        @DisplayName("Should build valid MCP request")
        void buildMcpRequest_ValidInput_ReturnsRequest() {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("serviceType", "Plumbing");

            Object result = ReflectionTestUtils.invokeMethod(aiAssistantMcpService,
                    "buildMcpRequest", "search_providers", arguments);

            assertThat(result).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertThat(resultMap.get("jsonrpc")).isEqualTo("2.0");
            assertThat(resultMap.get("method")).isEqualTo("tools/call");
            assertThat(resultMap.get("id")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Parse MCP Content Tests")
    class ParseMcpContentTests {

        @Test
        @DisplayName("Should return null when result is missing")
        void parseMcpContent_NoResult_ReturnsNull() throws Exception {
            Map<String, Object> mcpResponse = new HashMap<>();

            Object result = ReflectionTestUtils.invokeMethod(aiAssistantMcpService,
                    "parseMcpContent", mcpResponse);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null when content is missing")
        void parseMcpContent_NoContent_ReturnsNull() throws Exception {
            Map<String, Object> mcpResponse = new HashMap<>();
            Map<String, Object> result = new HashMap<>();
            result.put("isError", false);
            mcpResponse.put("result", result);

            Object parsed = ReflectionTestUtils.invokeMethod(aiAssistantMcpService,
                    "parseMcpContent", mcpResponse);

            assertThat(parsed).isNull();
        }

        @Test
        @DisplayName("Should return null when text is empty")
        void parseMcpContent_EmptyText_ReturnsNull() throws Exception {
            Map<String, Object> mcpResponse = new HashMap<>();
            Map<String, Object> result = new HashMap<>();
            result.put("isError", false);
            Map<String, Object> content = new HashMap<>();
            content.put("text", "");
            result.put("content", List.of(content));
            mcpResponse.put("result", result);

            Object parsed = ReflectionTestUtils.invokeMethod(aiAssistantMcpService,
                    "parseMcpContent", mcpResponse);

            assertThat(parsed).isNull();
        }
    }

    @Nested
    @DisplayName("Parse MCP Response Tests")
    class ParseMcpResponseTests {

        @Test
        @DisplayName("Should return empty list when result is missing")
        void parseMcpResponse_NoResult_ReturnsEmpty() throws Exception {
            Map<String, Object> mcpResponse = new HashMap<>();

            @SuppressWarnings("unchecked")
            List<ProviderSummaryResponse> result = (List<ProviderSummaryResponse>) ReflectionTestUtils.invokeMethod(
                    aiAssistantMcpService,
                    "parseMcpResponse", mcpResponse);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when error is true")
        void parseMcpResponse_Error_ReturnsEmpty() throws Exception {
            Map<String, Object> mcpResponse = new HashMap<>();
            Map<String, Object> result = new HashMap<>();
            result.put("isError", true);
            Map<String, Object> content = new HashMap<>();
            content.put("text", "Error message");
            result.put("content", List.of(content));
            mcpResponse.put("result", result);

            @SuppressWarnings("unchecked")
            List<ProviderSummaryResponse> parsed = (List<ProviderSummaryResponse>) ReflectionTestUtils.invokeMethod(
                    aiAssistantMcpService,
                    "parseMcpResponse", mcpResponse);

            assertThat(parsed).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when content is missing")
        void parseMcpResponse_NoContent_ReturnsEmpty() throws Exception {
            Map<String, Object> mcpResponse = new HashMap<>();
            Map<String, Object> result = new HashMap<>();
            result.put("isError", false);
            mcpResponse.put("result", result);

            @SuppressWarnings("unchecked")
            List<ProviderSummaryResponse> parsed = (List<ProviderSummaryResponse>) ReflectionTestUtils.invokeMethod(
                    aiAssistantMcpService,
                    "parseMcpResponse", mcpResponse);

            assertThat(parsed).isEmpty();
        }
    }
}