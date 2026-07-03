package com.aykhedma.controller;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.response.ChatResponse;
import com.aykhedma.model.user.User;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.service.AiAssistantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI Chat Controller Tests")
class AiChatControllerTest {

    @Mock
    private AiAssistantService aiAssistantService;

    @Mock
    private CustomUserDetails userDetails;

    @InjectMocks
    private AiChatController aiChatController;

    private ObjectMapper objectMapper = new ObjectMapper();

    private final Long USER_ID = 1L;
    private final String SESSION_ID = "session-123";
    private final String TEST_MESSAGE = "Hello, I need a plumber";
    private final String AI_RESPONSE = "I found 3 plumbers near you";

    private User mockUser;
    private ChatResponse mockChatResponse;
    private AiChatRequest mockRequest;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(USER_ID);
        mockUser.setName("Test User");
        mockUser.setEmail("test@example.com");

        mockChatResponse = ChatResponse.builder()
                .sessionId(SESSION_ID)
                .message(AI_RESPONSE)
                .timestamp(LocalTime.now().atDate(LocalDate.now()))
                .build();

        mockRequest = AiChatRequest.builder()
                .sessionId(SESSION_ID)
                .message(TEST_MESSAGE)
                .build();
    }

    private CustomUserDetails createAuthenticatedUserDetails() {
        CustomUserDetails details = mock(CustomUserDetails.class);
        when(details.getUser()).thenReturn(mockUser);
        return details;
    }

    @Nested
    @DisplayName("Chat JSON Endpoint Tests")
    class ChatJsonEndpointTests {

        @Test
        @DisplayName("Should process chat request and return response")
        void chat_ValidRequest_ReturnsChatResponse() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            when(aiAssistantService.chat(any(AiChatRequest.class), any(User.class)))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chat(mockRequest, authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSessionId()).isEqualTo(SESSION_ID);
            assertThat(response.getBody().getMessage()).isEqualTo(AI_RESPONSE);

            verify(aiAssistantService).chat(mockRequest, mockUser);
        }

        @Test
        @DisplayName("Should handle null user details gracefully")
        void chat_NullUserDetails_ProcessesRequest() {
            when(aiAssistantService.chat(any(AiChatRequest.class), isNull()))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chat(mockRequest, null);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).chat(mockRequest, null);
        }

        @Test
        @DisplayName("Should handle request with all fields")
        void chat_FullRequest_ProcessesAllFields() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            LocationDTO location = LocationDTO.builder()
                    .latitude(30.0444)
                    .longitude(31.2357)
                    .build();

            AiChatRequest fullRequest = AiChatRequest.builder()
                    .sessionId(SESSION_ID)
                    .message(TEST_MESSAGE)
                    .providerId(10L)
                    .serviceTypeId(5L)
                    .requestedDate(LocalDate.now().plusDays(1))
                    .requestedTime(LocalTime.of(10, 0))
                    .location(location)
                    .build();

            when(aiAssistantService.chat(any(AiChatRequest.class), any(User.class)))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chat(fullRequest, authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).chat(eq(fullRequest), any(User.class));
        }

        @Test
        @DisplayName("Should handle empty message")
        void chat_EmptyMessage_ReturnsResponse() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            AiChatRequest emptyRequest = AiChatRequest.builder()
                    .sessionId(SESSION_ID)
                    .message("")
                    .build();

            ChatResponse emptyResponse = ChatResponse.builder()
                    .sessionId(SESSION_ID)
                    .message("Please provide a message")
                    .build();

            when(aiAssistantService.chat(any(AiChatRequest.class), any(User.class)))
                    .thenReturn(emptyResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chat(emptyRequest, authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).chat(emptyRequest, mockUser);
        }
    }

    @Nested
    @DisplayName("Chat Multipart Endpoint Tests")
    class ChatMultipartEndpointTests {

        @Test
        @DisplayName("Should process chat with text message only")
        void chatWithVoice_TextOnly_ReturnsResponse() throws Exception {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            when(aiAssistantService.chat(any(AiChatRequest.class), any(User.class)))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chatWithVoice(
                    SESSION_ID,
                    TEST_MESSAGE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).isEqualTo(AI_RESPONSE);

            verify(aiAssistantService).chat(any(AiChatRequest.class), eq(mockUser));
        }

        @Test
        @DisplayName("Should process chat with voice note")
        void chatWithVoice_VoiceNote_ReturnsResponse() throws Exception {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            MockMultipartFile voiceNote = new MockMultipartFile(
                    "voiceNote",
                    "voice.wav",
                    "audio/wav",
                    "test audio content".getBytes());

            when(aiAssistantService.chat(any(AiChatRequest.class), any(User.class)))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chatWithVoice(
                    SESSION_ID,
                    TEST_MESSAGE,
                    voiceNote,
                    null,
                    null,
                    null,
                    null,
                    null,
                    authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).chat(any(AiChatRequest.class), eq(mockUser));
        }

        @Test
        @DisplayName("Should process chat with all parameters")
        void chatWithVoice_AllParameters_ReturnsResponse() throws Exception {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            LocationDTO location = LocationDTO.builder()
                    .latitude(30.0444)
                    .longitude(31.2357)
                    .build();

            MockMultipartFile voiceNote = new MockMultipartFile(
                    "voiceNote",
                    "voice.wav",
                    "audio/wav",
                    "test audio content".getBytes());

            when(aiAssistantService.chat(any(AiChatRequest.class), any(User.class)))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chatWithVoice(
                    SESSION_ID,
                    TEST_MESSAGE,
                    voiceNote,
                    10L,
                    5L,
                    LocalDate.now().plusDays(1),
                    LocalTime.of(10, 0),
                    location,
                    authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).chat(any(AiChatRequest.class), eq(mockUser));
        }

        @Test
        @DisplayName("Should handle null user details in multipart request")
        void chatWithVoice_NullUserDetails_ProcessesRequest() throws Exception {
            when(aiAssistantService.chat(any(AiChatRequest.class), isNull()))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chatWithVoice(
                    SESSION_ID,
                    TEST_MESSAGE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).chat(any(AiChatRequest.class), isNull());
        }

        @Test
        @DisplayName("Should handle null sessionId")
        void chatWithVoice_NullSessionId_ReturnsResponse() throws Exception {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            when(aiAssistantService.chat(any(AiChatRequest.class), any(User.class)))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chatWithVoice(
                    null,
                    TEST_MESSAGE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).chat(any(AiChatRequest.class), eq(mockUser));
        }
    }

    @Nested
    @DisplayName("New Chat Endpoint Tests")
    class NewChatEndpointTests {

        @Test
        @DisplayName("Should start new chat for authenticated user")
        void startNewChat_AuthenticatedUser_ReturnsNewChatResponse() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            ChatResponse newChatResponse = ChatResponse.builder()
                    .sessionId("new-session-456")
                    .message("New chat started. How can I help you today?")
                    .timestamp(LocalTime.now().atDate(LocalDate.now()))
                    .build();

            when(aiAssistantService.startNewChat(any(User.class)))
                    .thenReturn(newChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.startNewChat(authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSessionId()).isEqualTo("new-session-456");
            assertThat(response.getBody().getMessage()).contains("New chat started");

            verify(aiAssistantService).startNewChat(mockUser);
        }

        @Test
        @DisplayName("Should start new chat for unauthenticated user")
        void startNewChat_UnauthenticatedUser_ReturnsNewChatResponse() {
            ChatResponse newChatResponse = ChatResponse.builder()
                    .sessionId("new-session-789")
                    .message("New chat started.")
                    .build();

            when(aiAssistantService.startNewChat(isNull()))
                    .thenReturn(newChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.startNewChat(null);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).startNewChat(null);
        }
    }

    @Nested
    @DisplayName("Get Chat Endpoint Tests")
    class GetChatEndpointTests {

        @Test
        @DisplayName("Should get chat by sessionId")
        void getChat_ValidSessionId_ReturnsChatResponse() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            when(aiAssistantService.getChat(eq(SESSION_ID), any(User.class)))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.getChat(SESSION_ID, authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSessionId()).isEqualTo(SESSION_ID);

            verify(aiAssistantService).getChat(SESSION_ID, mockUser);
        }

        @Test
        @DisplayName("Should handle null user details when getting chat")
        void getChat_NullUserDetails_ReturnsChatResponse() {
            when(aiAssistantService.getChat(eq(SESSION_ID), isNull()))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.getChat(SESSION_ID, null);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).getChat(SESSION_ID, null);
        }
    }

    @Nested
    @DisplayName("Get User Chats Endpoint Tests")
    class GetUserChatsEndpointTests {

        @Test
        @DisplayName("Should get all chats for authenticated user")
        void getUserChats_AuthenticatedUser_ReturnsChatList() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            List<ChatResponse> chatList = Arrays.asList(
                    ChatResponse.builder()
                            .sessionId("session-1")
                            .message("Chat 1")
                            .build(),
                    ChatResponse.builder()
                            .sessionId("session-2")
                            .message("Chat 2")
                            .build());

            when(aiAssistantService.getUserChats(any(User.class)))
                    .thenReturn(chatList);

            ResponseEntity<List<ChatResponse>> response = aiChatController.getUserChats(authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(2);

            verify(aiAssistantService).getUserChats(mockUser);
        }

        @Test
        @DisplayName("Should return empty list when user has no chats")
        void getUserChats_NoChats_ReturnsEmptyList() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            when(aiAssistantService.getUserChats(any(User.class)))
                    .thenReturn(List.of());

            ResponseEntity<List<ChatResponse>> response = aiChatController.getUserChats(authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).isEmpty();

            verify(aiAssistantService).getUserChats(mockUser);
        }

        @Test
        @DisplayName("Should handle null user details")
        void getUserChats_NullUserDetails_ReturnsChatList() {
            when(aiAssistantService.getUserChats(isNull()))
                    .thenReturn(List.of());

            ResponseEntity<List<ChatResponse>> response = aiChatController.getUserChats(null);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).getUserChats(null);
        }
    }

    @Nested
    @DisplayName("Delete Chat Endpoint Tests")
    class DeleteChatEndpointTests {

        @Test
        @DisplayName("Should delete chat successfully")
        void deleteChat_ValidSession_ReturnsSuccessResponse() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            when(aiAssistantService.deleteChatbotChatSession(eq(SESSION_ID), any(User.class)))
                    .thenReturn(true);

            ResponseEntity<?> response = aiChatController.deleteChat(SESSION_ID, authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("message");
            assertThat(body).containsKey("sessionId");
            assertThat(body.get("message")).isEqualTo("Chat session deleted successfully");
            assertThat(body.get("sessionId")).isEqualTo(SESSION_ID);

            verify(aiAssistantService).deleteChatbotChatSession(SESSION_ID, mockUser);
        }

        @Test
        @DisplayName("Should return 404 when chat not found")
        void deleteChat_ChatNotFound_Returns404() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            when(aiAssistantService.deleteChatbotChatSession(eq(SESSION_ID), any(User.class)))
                    .thenReturn(false);

            ResponseEntity<?> response = aiChatController.deleteChat(SESSION_ID, authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("error");
            assertThat(body.get("error")).isEqualTo("Chat session not found or could not be deleted");

            verify(aiAssistantService).deleteChatbotChatSession(SESSION_ID, mockUser);
        }

        @Test
        @DisplayName("Should handle null user details when deleting")
        void deleteChat_NullUserDetails_Returns404() {
            when(aiAssistantService.deleteChatbotChatSession(eq(SESSION_ID), isNull()))
                    .thenReturn(false);

            ResponseEntity<?> response = aiChatController.deleteChat(SESSION_ID, null);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            verify(aiAssistantService).deleteChatbotChatSession(SESSION_ID, null);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle very long message")
        void chat_VeryLongMessage_ProcessesRequest() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            String longMessage = "a".repeat(10000);
            AiChatRequest longRequest = AiChatRequest.builder()
                    .sessionId(SESSION_ID)
                    .message(longMessage)
                    .build();

            when(aiAssistantService.chat(any(AiChatRequest.class), any(User.class)))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chat(longRequest, authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).chat(longRequest, mockUser);
        }

        @Test
        @DisplayName("Should handle special characters in message")
        void chat_SpecialCharacters_ProcessesRequest() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            String specialMessage = "Hello! @#$%^&*()_+ How are you? 🚀";
            AiChatRequest specialRequest = AiChatRequest.builder()
                    .sessionId(SESSION_ID)
                    .message(specialMessage)
                    .build();

            when(aiAssistantService.chat(any(AiChatRequest.class), any(User.class)))
                    .thenReturn(mockChatResponse);

            ResponseEntity<ChatResponse> response = aiChatController.chat(specialRequest, authenticatedUser);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            verify(aiAssistantService).chat(specialRequest, mockUser);
        }

        @Test
        @DisplayName("Should handle service exception gracefully")
        void chat_ServiceThrowsException_PropagatesException() {
            CustomUserDetails authenticatedUser = createAuthenticatedUserDetails();

            when(aiAssistantService.chat(any(AiChatRequest.class), any(User.class)))
                    .thenThrow(new RuntimeException("Service error"));

            try {
                aiChatController.chat(mockRequest, authenticatedUser);
            } catch (RuntimeException e) {
                assertThat(e.getMessage()).contains("Service error");
            }

            verify(aiAssistantService).chat(mockRequest, mockUser);
        }
    }
}