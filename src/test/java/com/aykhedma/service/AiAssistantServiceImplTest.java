package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ForbiddenException;
import com.aykhedma.mapper.LocationMapper;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.mcp.server.McpServer;
import com.aykhedma.model.chat.*;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI Assistant Service Tests")
class AiAssistantServiceImplTest {

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
    private ConsumerRepository consumerRepository;

    @Mock
    private LocationRepository locationRepository;

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
    private ServiceCategoryRepository categoryRepository;

    @Mock
    private SpeechToTextService speechToTextService;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private McpServer mcpServer;

    @Mock
    private AiAssistantMcpService mcpService;

    @Mock
    private AiAssistantOldService oldService;

    @InjectMocks
    private AiAssistantServiceImpl aiAssistantService;

    private final Long USER_ID = 1L;
    private final String SESSION_ID = "session-123";
    private final String TEST_MESSAGE = "I need a plumber";

    private User mockUser;
    private Consumer mockConsumer;
    private ChatSession mockSession;
    private ChatMessage mockMessage;
    private AiChatRequest mockRequest;
    private ServiceType mockServiceType;
    private ServiceCategory mockCategory;
    private Provider mockProvider;

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
        mockServiceType.setRiskLevel(com.aykhedma.model.service.RiskLevel.LOW);

        mockCategory = new ServiceCategory();
        mockCategory.setId(1L);
        mockCategory.setName("Home Services");
        mockCategory.setNameAr("خدمات منزلية");

        mockProvider = new Provider();
        mockProvider.setId(1L);
        mockProvider.setName("Test Provider");
        mockProvider.setVerificationStatus(VerificationStatus.VERIFIED);

        Location location = new Location();
        location.setLatitude(30.0444);
        location.setLongitude(31.2357);
        mockProvider.setLocation(location);

        ReflectionTestUtils.setField(aiAssistantService, "useMcp", false);
        ReflectionTestUtils.setField(aiAssistantService, "mcpEnabled", true);
        ReflectionTestUtils.setField(aiAssistantService, "mcpServer", Optional.of(mcpServer));
    }

    @Nested
    @DisplayName("Get User Chats Tests")
    class GetUserChatsTests {

        @Test
        @DisplayName("Should return user chats when authenticated")
        void getUserChats_AuthenticatedUser_ReturnsChats() {
            when(chatSessionRepository.findByUserIdOrderByStartTimeDesc(USER_ID))
                    .thenReturn(List.of(mockSession));
            when(chatMessageRepository.findByChatSessionSessionIdOrderByTimestampAsc(SESSION_ID))
                    .thenReturn(List.of(mockMessage));

            List<ChatResponse> result = aiAssistantService.getUserChats(mockUser);

            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSessionId()).isEqualTo(SESSION_ID);
            assertThat(result.get(0).getMessage()).isEqualTo(TEST_MESSAGE);
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is null")
        void getUserChats_NullUser_ThrowsForbiddenException() {
            assertThatThrownBy(() -> aiAssistantService.getUserChats(null))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("User must be authenticated");
        }

        @Test
        @DisplayName("Should return empty list when user has no chats")
        void getUserChats_NoChats_ReturnsEmptyList() {
            when(chatSessionRepository.findByUserIdOrderByStartTimeDesc(USER_ID))
                    .thenReturn(new ArrayList<>());

            List<ChatResponse> result = aiAssistantService.getUserChats(mockUser);

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get Chat Tests")
    class GetChatTests {

        @Test
        @DisplayName("Should return chat when session exists and user is authorized")
        void getChat_ValidSession_ReturnsChat() {
            when(chatSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(mockSession));
            when(chatMessageRepository.findByChatSessionSessionIdOrderByTimestampAsc(SESSION_ID))
                    .thenReturn(List.of(mockMessage));

            ChatResponse result = aiAssistantService.getChat(SESSION_ID, mockUser);

            assertThat(result).isNotNull();
            assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(result.getMessages()).isNotEmpty();
        }

        @Test
        @DisplayName("Should throw BadRequestException when session not found")
        void getChat_SessionNotFound_ThrowsException() {
            when(chatSessionRepository.findById("invalid-session")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiAssistantService.getChat("invalid-session", mockUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Chat session not found");
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is not authorized")
        void getChat_UnauthorizedUser_ThrowsException() {
            User otherUser = new User();
            otherUser.setId(2L);

            when(chatSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(mockSession));

            assertThatThrownBy(() -> aiAssistantService.getChat(SESSION_ID, otherUser))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not allowed to access");
        }
    }

    @Nested
    @DisplayName("Start New Chat Tests")
    class StartNewChatTests {

        @Test
        @DisplayName("Should start new chat for authenticated user")
        void startNewChat_AuthenticatedUser_ReturnsNewChat() {
            when(chatSessionRepository.findActiveSessionByUser(USER_ID))
                    .thenReturn(Optional.of(mockSession));
            when(chatSessionRepository.save(any(ChatSession.class)))
                    .thenReturn(mockSession);

            ChatResponse result = aiAssistantService.startNewChat(mockUser);

            assertThat(result).isNotNull();
            assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(result.getMessage()).contains("New chat started");
            verify(chatSessionRepository, times(2)).save(any(ChatSession.class));
        }

        @Test
        @DisplayName("Should start new chat for unauthenticated user")
        void startNewChat_UnauthenticatedUser_ReturnsNewChat() {
            when(chatSessionRepository.save(any(ChatSession.class)))
                    .thenReturn(mockSession);

            ChatResponse result = aiAssistantService.startNewChat(null);

            assertThat(result).isNotNull();
            assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
            verify(chatSessionRepository, times(1)).save(any(ChatSession.class));
        }
    }

    @Nested
    @DisplayName("Delete Chat Tests")
    class DeleteChatTests {

        @Test
        @DisplayName("Should delete chat successfully")
        void deleteChat_ValidSession_ReturnsTrue() {
            when(chatSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(mockSession));

            boolean result = aiAssistantService.deleteChatbotChatSession(SESSION_ID, mockUser);

            assertThat(result).isTrue();
            verify(chatSessionRepository).delete(mockSession);
        }

        @Test
        @DisplayName("Should return false when session not found")
        void deleteChat_SessionNotFound_ReturnsFalse() {
            when(chatSessionRepository.findById("invalid-session")).thenReturn(Optional.empty());

            boolean result = aiAssistantService.deleteChatbotChatSession("invalid-session", mockUser);

            assertThat(result).isFalse();
            verify(chatSessionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should return false when user is not authorized")
        void deleteChat_UnauthorizedUser_ReturnsFalse() {
            User otherUser = new User();
            otherUser.setId(2L);

            when(chatSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(mockSession));

            boolean result = aiAssistantService.deleteChatbotChatSession(SESSION_ID, otherUser);

            assertThat(result).isFalse();
            verify(chatSessionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should return false when sessionId is null or empty")
        void deleteChat_NullSessionId_ReturnsFalse() {
            boolean result = aiAssistantService.deleteChatbotChatSession(null, mockUser);
            assertThat(result).isFalse();

            result = aiAssistantService.deleteChatbotChatSession("", mockUser);
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Resolve Session Tests")
    class ResolveSessionTests {

//        @Test
//        @DisplayName("Should resolve existing session when sessionId provided")
//        void resolveSession_ExistingSessionId_ReturnsSession() {
//            when(chatSessionRepository.findBySessionIdAndIsActiveTrue(SESSION_ID))
//                    .thenReturn(Optional.of(mockSession));
//
//            ChatSession result = aiAssistantService.resolveSession(mockRequest, USER_ID);
//
//            assertThat(result).isNotNull();
//            assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
//        }

//        @Test
//        @DisplayName("Should throw BadRequestException when session is invalid")
//        void resolveSession_InvalidSession_ThrowsException() {
//            when(chatSessionRepository.findBySessionIdAndIsActiveTrue("invalid"))
//                    .thenReturn(Optional.empty());
//
//            AiChatRequest request = AiChatRequest.builder()
//                    .sessionId("invalid")
//                    .build();
//
//            assertThatThrownBy(() -> aiAssistantService.resolveSession(request, USER_ID))
//                    .isInstanceOf(BadRequestException.class)
//                    .hasMessageContaining("Invalid or expired session ID");
//        }

        @Test
        @DisplayName("Should create new session when no sessionId provided")
        void resolveSession_NoSessionId_CreatesNewSession() {
            AiChatRequest request = AiChatRequest.builder().build();

            when(chatSessionRepository.findActiveSessionByUser(USER_ID))
                    .thenReturn(Optional.empty());
            when(chatSessionRepository.save(any(ChatSession.class)))
                    .thenReturn(mockSession);

            ChatSession result = aiAssistantService.resolveSession(request, USER_ID);

            assertThat(result).isNotNull();
            verify(chatSessionRepository).save(any(ChatSession.class));
        }
    }

    @Nested
    @DisplayName("Caching Tests")
    class CachingTests {

        @Test
        @DisplayName("Should cache service types")
        void getCachedServiceTypes_ReturnsCachedTypes() {
            when(serviceTypeRepository.findAll()).thenReturn(List.of(mockServiceType));

            List<ServiceType> result1 = aiAssistantService.getCachedServiceTypes();
            List<ServiceType> result2 = aiAssistantService.getCachedServiceTypes();

            assertThat(result1).isNotNull();
            assertThat(result2).isNotNull();
            verify(serviceTypeRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Should cache categories")
        void getCachedCategories_ReturnsCachedCategories() {
            when(categoryRepository.findAll()).thenReturn(List.of(mockCategory));

            List<ServiceCategory> result1 = aiAssistantService.getCachedCategories();
            List<ServiceCategory> result2 = aiAssistantService.getCachedCategories();

            assertThat(result1).isNotNull();
            assertThat(result2).isNotNull();
            verify(categoryRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Should return null when too many services for catalog")
        void getServiceCatalogJsonOrNull_TooManyServices_ReturnsNull() {
            List<ServiceType> manyServices = new ArrayList<>();
            for (int i = 0; i < 81; i++) {
                ServiceType st = new ServiceType();
                st.setId((long) i);
                st.setName("Service " + i);
                manyServices.add(st);
            }

            when(serviceTypeRepository.findAll()).thenReturn(manyServices);

            String result = aiAssistantService.getServiceCatalogJsonOrNull();

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Service Resolution Tests")
    class ServiceResolutionTests {

        @Test
        @DisplayName("Should resolve service type by name")
        void resolveServiceTypeByName_ExistingName_ReturnsService() {
            when(serviceTypeRepository.findAll()).thenReturn(List.of(mockServiceType));

            ServiceType result = aiAssistantService.resolveServiceTypeByName("Plumbing");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("Should resolve service type by Arabic name")
        void resolveServiceTypeByName_ArabicName_ReturnsService() {
            when(serviceTypeRepository.findAll()).thenReturn(List.of(mockServiceType));

            ServiceType result = aiAssistantService.resolveServiceTypeByName("سباكة");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("Should return null when service type not found")
        void resolveServiceTypeByName_NotFound_ReturnsNull() {
            when(serviceTypeRepository.findAll()).thenReturn(List.of(mockServiceType));

            ServiceType result = aiAssistantService.resolveServiceTypeByName("NonExistent");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should resolve provider by name")
        void resolveProviderIdByName_ExistingProvider_ReturnsId() {
            when(providerRepository.findAll()).thenReturn(List.of(mockProvider));

            Long result = aiAssistantService.resolveProviderIdByName("Test Provider");

            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should return null when provider not found")
        void resolveProviderIdByName_NotFound_ReturnsNull() {
            when(providerRepository.findAll()).thenReturn(List.of());

            Long result = aiAssistantService.resolveProviderIdByName("NonExistent");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Provider Search Tests")
    class ProviderSearchTests {

        @Test
        @DisplayName("Should find and sort providers")
        void findAndSortProviders_ReturnsSortedProviders() {
            when(providerRepository.findByServiceTypeIdAndVerificationStatus(anyLong(), any()))
                    .thenReturn(List.of(mockProvider));
            when(providerMapper.toProviderSummaryResponse(any(Provider.class)))
                    .thenReturn(ProviderSummaryResponse.builder()
                            .id(1L)
                            .name("Test Provider")
                            .build());

            List<ProviderSummaryResponse> result = aiAssistantService.findAndSortProviders(
                    10L, null, 10, mockUser);

            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Should return empty list when no providers found")
        void findAndSortProviders_NoProviders_ReturnsEmpty() {
            when(providerRepository.findByServiceTypeIdAndVerificationStatus(anyLong(), any()))
                    .thenReturn(new ArrayList<>());

            List<ProviderSummaryResponse> result = aiAssistantService.findAndSortProviders(
                    10L, null, 10, mockUser);

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Response Building Tests")
    class ResponseBuildingTests {

        @Test
        @DisplayName("Should build smart reply for multiple providers")
        void buildSmartReply_MultipleProviders_ReturnsFormattedReply() {
            List<ProviderSummaryResponse> providers = Arrays.asList(
                    ProviderSummaryResponse.builder().id(1L).name("Provider 1").build(),
                    ProviderSummaryResponse.builder().id(2L).name("Provider 2").build());

            String result = aiAssistantService.buildSmartReply(providers, mockServiceType, TEST_MESSAGE);

            assertThat(result).isNotNull();
            assertThat(result).contains("سباكة");
            assertThat(result).contains("Provider 1");
            assertThat(result).contains("Provider 2");
        }

        @Test
        @DisplayName("Should build smart reply for single provider")
        void buildSmartReply_SingleProvider_ReturnsFormattedReply() {
            List<ProviderSummaryResponse> providers = List.of(
                    ProviderSummaryResponse.builder()
                            .id(1L)
                            .name("Provider 1")
                            .averageRating(4.5)
                            .build());

            String result = aiAssistantService.buildSmartReply(providers, mockServiceType, TEST_MESSAGE);

            assertThat(result).isNotNull();
            assertThat(result).contains("مزود خدمة واحد");
            assertThat(result).contains("Provider 1");
        }

        @Test
        @DisplayName("Should build smart reply when no providers found")
        void buildSmartReply_NoProviders_ReturnsNotFoundMessage() {
            String result = aiAssistantService.buildSmartReply(new ArrayList<>(), mockServiceType, TEST_MESSAGE);

            assertThat(result).isNotNull();
            assertThat(result).contains("عذراً");
            assertThat(result).contains("سباكة");
        }
    }

    @Nested
    @DisplayName("Utility Methods Tests")
    class UtilityMethodsTests {

        @Test
        @DisplayName("Should detect Arabic language")
        void detectLanguage_ArabicText_ReturnsAr() {
            String result = aiAssistantService.detectLanguage("مرحبا كيف الحال");
            assertThat(result).isEqualTo("ar");
        }

        @Test
        @DisplayName("Should detect English language")
        void detectLanguage_EnglishText_ReturnsEn() {
            String result = aiAssistantService.detectLanguage("Hello how are you");
            assertThat(result).isEqualTo("en");
        }

        @Test
        @DisplayName("Should return en for null text")
        void detectLanguage_NullText_ReturnsEn() {
            String result = aiAssistantService.detectLanguage(null);
            assertThat(result).isEqualTo("en");
        }

        @Test
        @DisplayName("Should normalize text")
        void normalize_RemovesSpecialCharacters() {
            String result = aiAssistantService.normalize("Hello! @#$% World 123");
            assertThat(result).isEqualTo("hello world 123");
        }

        @Test
        @DisplayName("Should escape JSON strings")
        void escapeJson_EscapesSpecialCharacters() {
            String result = aiAssistantService.escapeJson("Hello \"World\" \n New Line");
            assertThat(result).isEqualTo("Hello \\\"World\\\"   New Line");
        }

        @Test
        @DisplayName("Should parse date safely")
        void parseDateSafe_ValidDate_ReturnsLocalDate() {
            LocalDate result = aiAssistantService.parseDateSafe("2024-01-15");
            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("Should return null for invalid date")
        void parseDateSafe_InvalidDate_ReturnsNull() {
            LocalDate result = aiAssistantService.parseDateSafe("invalid-date");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should parse time safely")
        void parseTimeSafe_ValidTime_ReturnsLocalTime() {
            LocalTime result = aiAssistantService.parseTimeSafe("14:30");
            assertThat(result).isEqualTo(LocalTime.of(14, 30));
        }

        @Test
        @DisplayName("Should return null for invalid time")
        void parseTimeSafe_InvalidTime_ReturnsNull() {
            LocalTime result = aiAssistantService.parseTimeSafe("invalid-time");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should check if user is consumer")
        void isConsumer_ConsumerUser_ReturnsTrue() {
            boolean result = aiAssistantService.isConsumer(mockUser);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false for non-consumer user")
        void isConsumer_NonConsumer_ReturnsFalse() {
            User providerUser = new User();
            providerUser.setRole(UserType.PROVIDER);
            boolean result = aiAssistantService.isConsumer(providerUser);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should extract JSON from text")
        void extractJson_ValidJson_ReturnsJson() {
            String text = "Some text {\"key\": \"value\"} more text";
            String result = aiAssistantService.extractJson(text);
            assertThat(result).isEqualTo("{\"key\": \"value\"}");
        }

        @Test
        @DisplayName("Should return null when no JSON found")
        void extractJson_NoJson_ReturnsNull() {
            String text = "No JSON here";
            String result = aiAssistantService.extractJson(text);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should get recent history")
        void getRecentHistory_ReturnsRecentMessages() {
            List<ChatMessage> history = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                ChatMessage msg = ChatMessage.builder()
                        .id("msg-" + i)
                        .content("Message " + i)
                        .senderRole(MessageRole.USER)
                        .build();
                history.add(msg);
            }

            List<ChatMessage> result = aiAssistantService.getRecentHistory(history, 10);

            assertThat(result).hasSize(10);
            assertThat(result.get(0).getContent()).isEqualTo("Message 5");
        }

        @Test
        @DisplayName("Should return empty list for null history")
        void getRecentHistory_NullHistory_ReturnsEmpty() {
            List<ChatMessage> result = aiAssistantService.getRecentHistory(null, 10);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should check if text contains any keyword")
        void containsAny_ContainsKeyword_ReturnsTrue() {
            boolean result = aiAssistantService.containsAny("I need a plumber", "plumber", "electrician");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when no keyword found")
        void containsAny_NoKeyword_ReturnsFalse() {
            boolean result = aiAssistantService.containsAny("I need a plumber", "doctor", "engineer");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should format availability message")
        void formatAvailabilityMessage_WithSlots_ReturnsFormattedMessage() {
            List<ScheduleResponse.TimeSlotResponse> slots = Arrays.asList(
                    ScheduleResponse.TimeSlotResponse.builder()
                            .startTime(LocalTime.of(9, 0))
                            .endTime(LocalTime.of(10, 0))
                            .build(),
                    ScheduleResponse.TimeSlotResponse.builder()
                            .startTime(LocalTime.of(10, 0))
                            .endTime(LocalTime.of(11, 0))
                            .build());

            String result = aiAssistantService.formatAvailabilityMessage(1L, LocalDate.now(), slots);

            assertThat(result).isNotNull();
            assertThat(result).contains("09:00");
            assertThat(result).contains("10:00");
        }

        @Test
        @DisplayName("Should format availability message when no slots")
        void formatAvailabilityMessage_NoSlots_ReturnsNotFoundMessage() {
            String result = aiAssistantService.formatAvailabilityMessage(1L, LocalDate.now(), null);

            assertThat(result).isNotNull();
            assertThat(result).contains("لم يتم العثور");
        }
    }

    @Nested
@DisplayName("Chat Method Tests")
class ChatMethodTests {

    @Test
    @DisplayName("Should use existing implementation when MCP is disabled")
    void chat_McpDisabled_UsesExistingImplementation() {
        ReflectionTestUtils.setField(aiAssistantService, "useMcp", false);
        ReflectionTestUtils.setField(aiAssistantService, "mcpEnabled", true);
        ReflectionTestUtils.setField(aiAssistantService, "oldService", oldService);
        ReflectionTestUtils.setField(aiAssistantService, "mcpServer", Optional.empty());

        when(oldService.chatWithExisting(any(), any())).thenReturn(
                ChatResponse.builder()
                        .sessionId(SESSION_ID)
                        .message("Test response")
                        .build()
        );

        ChatResponse result = aiAssistantService.chat(mockRequest, mockUser);

        assertThat(result).isNotNull();
        verify(oldService).chatWithExisting(mockRequest, mockUser);
        verify(mcpService, never()).chatWithMcp(any(), any());
    }

    @Test
    @DisplayName("Should use MCP implementation when enabled")
    void chat_McpEnabled_UsesMcpImplementation() {
        ReflectionTestUtils.setField(aiAssistantService, "useMcp", true);
        ReflectionTestUtils.setField(aiAssistantService, "mcpEnabled", true);
        ReflectionTestUtils.setField(aiAssistantService, "mcpService", mcpService);
        ReflectionTestUtils.setField(aiAssistantService, "mcpServer", Optional.of(mcpServer));

        when(mcpService.chatWithMcp(any(), any())).thenReturn(
                ChatResponse.builder()
                        .sessionId(SESSION_ID)
                        .message("MCP response")
                        .build()
        );

        ChatResponse result = aiAssistantService.chat(mockRequest, mockUser);

        assertThat(result).isNotNull();
        assertThat(result.getMessage()).isEqualTo("MCP response");
        verify(mcpService).chatWithMcp(mockRequest, mockUser);
        verify(oldService, never()).chatWithExisting(any(), any());
    }
}
}