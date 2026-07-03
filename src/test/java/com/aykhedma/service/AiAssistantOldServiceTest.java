package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.mapper.LocationMapper;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.model.booking.BookingStatus;
import com.aykhedma.model.chat.ChatMessage;
import com.aykhedma.model.chat.ChatResponseType;
import com.aykhedma.model.chat.ChatSession;
import com.aykhedma.model.chat.MessageRole;
import com.aykhedma.model.chat.MessageType;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.repository.*;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI Assistant Old Service Tests")
class AiAssistantOldServiceTest {

    @Mock
    private AiAssistantServiceImpl baseService;

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private ProviderMapper providerMapper;

    @Mock
    private ProviderService providerService;

    @Mock
    private BookingService bookingService;

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @Mock
    private ServiceCategoryRepository categoryRepository;

    @Mock
    private ConsumerRepository consumerRepository;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private LocationService locationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private SpeechToTextService speechToTextService;

    @InjectMocks
    private AiAssistantOldService aiAssistantOldService;

    private final Long USER_ID = 1L;
    private final String SESSION_ID = "session-123";
    private final String TEST_MESSAGE = "I need a plumber";

    private User mockUser;
    private ChatSession mockSession;
    private ChatMessage mockMessage;
    private AiChatRequest mockRequest;
    private ServiceType mockServiceType;
    private Provider mockProvider;
    private Location mockLocation;
    private LocationDTO mockLocationDTO;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(USER_ID);
        mockUser.setName("Test User");
        mockUser.setEmail("test@example.com");

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

        mockServiceType = new ServiceType();
        mockServiceType.setId(10L);
        mockServiceType.setName("Plumbing");
        mockServiceType.setNameAr("سباكة");

        mockLocation = new Location();
        mockLocation.setLatitude(30.0444);
        mockLocation.setLongitude(31.2357);

        mockLocationDTO = LocationDTO.builder()
                .latitude(30.0444)
                .longitude(31.2357)
                .build();

        mockProvider = new Provider();
        mockProvider.setId(1L);
        mockProvider.setName("Test Provider");
        mockProvider.setVerificationStatus(VerificationStatus.VERIFIED);
        mockProvider.setLocation(mockLocation);

        ReflectionTestUtils.setField(aiAssistantOldService, "baseService", baseService);
    }

    @Nested
    @DisplayName("Chat With Existing Tests")
    class ChatWithExistingTests {

        @Test
        @DisplayName("Should process text message successfully")
        void chatWithExisting_TextMessage_ReturnsChatResponse() {
            when(baseService.resolveSession(any(), any())).thenReturn(mockSession);
            when(baseService.detectLanguage(anyString())).thenReturn("en");
            when(chatMessageRepository.findByChatSessionSessionIdOrderByTimestampAsc(anyString()))
                    .thenReturn(List.of(mockMessage));
            when(geminiClient.isEnabled()).thenReturn(true);
            when(geminiClient.generateJson(anyList(), anyString()))
                    .thenReturn("{\"action\":\"GENERAL\",\"reply\":\"Hello\"}");
            when(baseService.extractJson(anyString()))
                    .thenReturn("{\"action\":\"GENERAL\",\"reply\":\"Hello\"}");
            when(baseService.getRecentHistory(anyList(), anyInt())).thenReturn(List.of(mockMessage));
            when(baseService.toConversationTurns(anyList())).thenReturn(new ArrayList<>());
            when(baseService.getServiceCatalogJsonOrNull()).thenReturn(null);
            when(baseService.buildConversationContext(anyList())).thenReturn("Context");
            when(baseService.parseDateSafe(anyString())).thenReturn(null);
            when(baseService.parseTimeSafe(anyString())).thenReturn(null);

            ChatResponse result = aiAssistantOldService.chatWithExisting(mockRequest, mockUser);

            assertThat(result).isNotNull();
            assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
        }

        @Test
        @DisplayName("Should process voice note successfully")
        void chatWithExisting_VoiceNote_ReturnsChatResponse() throws IOException {
            MockMultipartFile voiceNote = new MockMultipartFile(
                    "voiceNote",
                    "voice.wav",
                    "audio/wav",
                    "test audio content".getBytes());

            AiChatRequest voiceRequest = AiChatRequest.builder()
                    .sessionId(SESSION_ID)
                    .voiceNote(voiceNote)
                    .build();

            when(speechToTextService.transcribeAudio(any())).thenReturn("Transcribed text");
            when(baseService.resolveSession(any(), any())).thenReturn(mockSession);
            when(baseService.detectLanguage(anyString())).thenReturn("en");
            when(chatMessageRepository.findByChatSessionSessionIdOrderByTimestampAsc(anyString()))
                    .thenReturn(List.of(mockMessage));
            when(geminiClient.isEnabled()).thenReturn(true);
            when(geminiClient.generateJson(anyList(), anyString()))
                    .thenReturn("{\"action\":\"GENERAL\",\"reply\":\"Hello\"}");
            when(baseService.extractJson(anyString()))
                    .thenReturn("{\"action\":\"GENERAL\",\"reply\":\"Hello\"}");
            when(baseService.getRecentHistory(anyList(), anyInt())).thenReturn(List.of(mockMessage));
            when(baseService.toConversationTurns(anyList())).thenReturn(new ArrayList<>());
            when(baseService.getServiceCatalogJsonOrNull()).thenReturn(null);
            when(baseService.buildConversationContext(anyList())).thenReturn("Context");
            when(baseService.parseDateSafe(anyString())).thenReturn(null);
            when(baseService.parseTimeSafe(anyString())).thenReturn(null);

            ChatResponse result = aiAssistantOldService.chatWithExisting(voiceRequest, mockUser);

            assertThat(result).isNotNull();
            assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
            verify(speechToTextService).transcribeAudio(any());
        }

        @Test
        @DisplayName("Should handle voice transcription failure")
        void chatWithExisting_VoiceTranscriptionFailed_ReturnsClarification() throws IOException {
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

            ChatResponse result = aiAssistantOldService.chatWithExisting(voiceRequest, mockUser);

            assertThat(result).isNotNull();
            assertThat(result.getResponseType()).isEqualTo(ChatResponseType.CLARIFICATION);
            assertThat(result.getMessage()).contains("عذراً");
        }

        @Test
        @DisplayName("Should throw BadRequestException when message is empty")
        void chatWithExisting_EmptyMessage_ThrowsException() {
            AiChatRequest emptyRequest = AiChatRequest.builder()
                    .sessionId(SESSION_ID)
                    .message("")
                    .build();

            assertThatThrownBy(() -> aiAssistantOldService.chatWithExisting(emptyRequest, mockUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Message or voice note is required");
        }

        @Test
        @DisplayName("Should use smart fallback when Gemini is disabled")
        void chatWithExisting_GeminiDisabled_UsesSmartFallback() {
            when(baseService.resolveSession(any(), any())).thenReturn(mockSession);
            when(baseService.detectLanguage(anyString())).thenReturn("en");
            when(chatMessageRepository.findByChatSessionSessionIdOrderByTimestampAsc(anyString()))
                    .thenReturn(List.of(mockMessage));
            when(geminiClient.isEnabled()).thenReturn(false);
            when(baseService.getRecentHistory(anyList(), anyInt())).thenReturn(List.of(mockMessage));
            when(baseService.normalize(anyString())).thenReturn("need plumber");
            when(baseService.containsAny(anyString(), any())).thenReturn(false);

            ChatResponse result = aiAssistantOldService.chatWithExisting(mockRequest, mockUser);

            assertThat(result).isNotNull();
        }

        // @Test
        // @DisplayName("Should handle provider search action from Gemini")
        // void chatWithExisting_ProviderSearch_ReturnsProviders() {
        //     when(baseService.resolveSession(any(), any())).thenReturn(mockSession);
        //     when(baseService.detectLanguage(anyString())).thenReturn("en");
        //     when(chatMessageRepository.findByChatSessionSessionIdOrderByTimestampAsc(anyString()))
        //             .thenReturn(List.of(mockMessage));
        //     when(geminiClient.isEnabled()).thenReturn(true);
        //     when(geminiClient.generateJson(anyList(), anyString()))
        //             .thenReturn("{\"action\":\"SEARCH_PROVIDERS\",\"reply\":\"Found providers\",\"serviceTypeId\":10}");
        //     when(baseService.extractJson(anyString()))
        //             .thenReturn("{\"action\":\"SEARCH_PROVIDERS\",\"reply\":\"Found providers\",\"serviceTypeId\":10}");
        //     when(baseService.getRecentHistory(anyList(), anyInt())).thenReturn(List.of(mockMessage));
        //     when(baseService.toConversationTurns(anyList())).thenReturn(new ArrayList<>());
        //     when(baseService.getServiceCatalogJsonOrNull()).thenReturn(null);
        //     when(baseService.buildConversationContext(anyList())).thenReturn("Context");
        //     when(baseService.getCachedServiceTypes()).thenReturn(List.of(mockServiceType));
        //     when(baseService.resolveSearchLocation(any(), any())).thenReturn(mockLocationDTO);
        //     when(baseService.findAndSortProviders(anyLong(), any(), anyInt(), any())).thenReturn(new ArrayList<>());
        //     when(baseService.buildSmartReply(anyList(), any(), anyString())).thenReturn("Found providers");
        //     when(baseService.parseDateSafe(anyString())).thenReturn(null);
        //     when(baseService.parseTimeSafe(anyString())).thenReturn(null);

        //     ChatResponse result = aiAssistantOldService.chatWithExisting(mockRequest, mockUser);

        //     assertThat(result).isNotNull();
        //     assertThat(result.getResponseType()).isEqualTo(ChatResponseType.PROVIDER_LIST);
        // }

        // @Test
        // @DisplayName("Should handle booking action from Gemini")
        // void chatWithExisting_BookingAction_ReturnsBookingCreated() {
        //     when(baseService.resolveSession(any(), any())).thenReturn(mockSession);
        //     when(baseService.detectLanguage(anyString())).thenReturn("en");
        //     when(chatMessageRepository.findByChatSessionSessionIdOrderByTimestampAsc(anyString()))
        //             .thenReturn(List.of(mockMessage));
        //     when(geminiClient.isEnabled()).thenReturn(true);
        //     when(geminiClient.generateJson(anyList(), anyString()))
        //             .thenReturn(
        //                     "{\"action\":\"CREATE_BOOKING\",\"reply\":\"Booking created\",\"providerId\":1,\"requestedDate\":\"2024-01-15\",\"requestedTime\":\"10:00\",\"problemDescription\":\"Test\"}");
        //     when(baseService.extractJson(anyString()))
        //             .thenReturn(
        //                     "{\"action\":\"CREATE_BOOKING\",\"reply\":\"Booking created\",\"providerId\":1,\"requestedDate\":\"2024-01-15\",\"requestedTime\":\"10:00\",\"problemDescription\":\"Test\"}");
        //     when(baseService.getRecentHistory(anyList(), anyInt())).thenReturn(List.of(mockMessage));
        //     when(baseService.toConversationTurns(anyList())).thenReturn(new ArrayList<>());
        //     when(baseService.getServiceCatalogJsonOrNull()).thenReturn(null);
        //     when(baseService.buildConversationContext(anyList())).thenReturn("Context");
        //     when(baseService.isConsumer(any())).thenReturn(true);
        //     when(baseService.parseDateSafe(anyString())).thenReturn(LocalDate.of(2024, 1, 15));
        //     when(baseService.parseTimeSafe(anyString())).thenReturn(LocalTime.of(10, 0));
        //     when(providerRepository.findById(anyLong())).thenReturn(Optional.of(mockProvider));
        //     when(timeSlotRepository.isTimeWithinAvailableSlot(anyLong(), any(), any())).thenReturn(true);
        //     when(bookingService.requestBooking(anyLong(), any(BookingRequest.class)))
        //             .thenReturn(BookingResponse.builder().id(100L).status(BookingStatus.PENDING).build());

        //     ChatResponse result = aiAssistantOldService.chatWithExisting(mockRequest, mockUser);

        //     assertThat(result).isNotNull();
        //     assertThat(result.getResponseType()).isEqualTo(ChatResponseType.BOOKING_CREATED);
        //     assertThat(result.getBooking()).isNotNull();
        //     assertThat(result.getBooking().getId()).isEqualTo(100L);
        // }
    }
}