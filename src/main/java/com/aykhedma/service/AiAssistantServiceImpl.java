package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ForbiddenException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.model.chat.*;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.mapper.LocationMapper;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.mcp.server.McpServer;
import com.aykhedma.repository.*;
import com.aykhedma.service.AiAssistantServiceImpl.Intent;
import com.aykhedma.service.AiAssistantServiceImpl.UnifiedAssistantResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AiAssistantServiceImpl implements AiAssistantService {

    // ===== CONSTANTS =====
    public static final int MAX_HISTORY_TURNS = 10;
    public static final int DEFAULT_SEARCH_RADIUS_KM = 10;
    public static final int MAX_SERVICES_FOR_INLINE_CATALOG = 80;
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    public static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    public static final Duration CATALOG_CACHE_TTL = Duration.ofMinutes(5);
    public static final Duration AI_LOOKUP_CACHE_TTL = Duration.ofMinutes(10);

    // ===== REPOSITORIES & SERVICES =====
    private final GeminiClient geminiClient;
    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;
    private final ProviderService providerService;
    private final BookingService bookingService;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ConsumerRepository consumerRepository;
    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;
    private final LocationService locationService;
    private final ObjectMapper objectMapper;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final SpeechToTextService speechToTextService;
    private final TimeSlotRepository timeSlotRepository;

    // ===== MCP CONFIGURATION =====
    @Value("${mcp.use-mcp:false}")
    private boolean useMcp;

    @Value("${mcp.enabled:true}")
    private boolean mcpEnabled;

    private final Optional<McpServer> mcpServer;

    public AiAssistantMcpService mcpService;
    public AiAssistantOldService oldService;

    @Autowired
    public void setMcpService(@Lazy AiAssistantMcpService mcpService) {
        this.mcpService = mcpService;
    }

    @Autowired
    public void setOldService(@Lazy AiAssistantOldService oldService) {
        this.oldService = oldService;
    }

    @PostConstruct
    public void init() {
        if (this.oldService == null) {
            log.warn("oldService not initialized yet");
        }
    }

    // ===== CACHES =====
    private volatile CachedValue<List<ServiceType>> serviceTypesCache;
    private volatile CachedValue<List<ServiceCategory>> categoriesCache;
    private volatile CachedValue<String> serviceCatalogJsonCache;
    private final Map<String, CachedValue<Long>> serviceTypeByMeaningCache = new ConcurrentHashMap<>();

    // ===== INTERFACE METHODS =====

    @Override
    public ChatResponse chat(AiChatRequest request, User currentUser) {
        if (mcpEnabled && useMcp && mcpServer.isPresent()) {
            log.info("Using MCP implementation for chat");
            return mcpService.chatWithMcp(request, currentUser);
        }
        log.info("Using existing implementation for chat");
        return oldService.chatWithExisting(request, currentUser);
    }

    @Override
    public List<ChatResponse> getUserChats(User currentUser) {
        if (currentUser == null) {
            throw new ForbiddenException("User must be authenticated to view chat history");
        }

        List<ChatSession> sessions = chatSessionRepository.findByUserIdOrderByStartTimeDesc(currentUser.getId());

        return sessions.stream()
                .map(session -> {
                    List<ChatMessage> messages = chatMessageRepository
                            .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId());

                    String firstMessageContent = messages.isEmpty() ? "New Chat"
                            : getDisplayTitle(messages.get(0));

                    LocalDateTime lastMessageTime = messages.isEmpty() ? session.getStartTime()
                            : messages.get(messages.size() - 1).getTimestamp();

                    return ChatResponse.builder()
                            .sessionId(session.getSessionId())
                            .timestamp(lastMessageTime)
                            .message(firstMessageContent)
                            .detectedLanguage(session.getDetectedLanguage())
                            .build();
                })
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
    }

    private String getDisplayTitle(ChatMessage message) {
        if (message.getType() == MessageType.VOICE) {
            if (Boolean.TRUE.equals(message.getTranscriptionSuccess())
                    && StringUtils.hasText(message.getTranscribedText())) {
                return message.getTranscribedText();
            } else {
                return "🎤 Voice note";
            }
        }

        return message.getContent();
    }

    @Override
    public ChatResponse getChat(String sessionId, User currentUser) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BadRequestException("Chat session not found"));

        if (!Objects.equals(session.getUserId(), currentUser != null ? currentUser.getId() : null)) {
            throw new ForbiddenException("You are not allowed to access this chat session");
        }

        List<ChatMessage> messageEntities = chatMessageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(sessionId);

        List<ChatMessageResponse> messages = messageEntities
                .stream()
                .map(message -> ChatMessageResponse.builder()
                        .id(message.getId())
                        .roomId(sessionId)
                        .senderId(message.getSenderId())
                        .senderName(message.getSenderRole() == MessageRole.ASSISTANT ? "Assistant" : "User")
                        .senderRole(message.getSenderRole())
                        .content(message.getContent())
                        .type(message.getType())
                        .mediaUrls(message.getMediaUrls())
                        .timestamp(message.getTimestamp())
                        .isRead(Boolean.TRUE.equals(message.getIsRead()))
                        .build())
                .collect(Collectors.toList());

        ChatMessage lastAssistantMessage = messageEntities.stream()
                .filter(ChatMessage::isAssistantMessage)
                .reduce((first, second) -> second)
                .orElse(null);

        ChatResponseType lastResponseType = lastAssistantMessage != null
                && lastAssistantMessage.getResponseType() != null
                        ? lastAssistantMessage.getResponseType()
                        : ChatResponseType.TEXT;

        List<ProviderSummaryResponse> providers = lastAssistantMessage != null
                ? parseProvidersPayload(lastAssistantMessage.getProvidersPayload())
                : List.of();

        List<ScheduleResponse.TimeSlotResponse> availableSlots = lastAssistantMessage != null
                ? parseAvailableSlotsPayload(lastAssistantMessage.getAvailableSlotsPayload())
                : List.of();

        String lastMessage = lastAssistantMessage != null && StringUtils.hasText(lastAssistantMessage.getContent())
                ? lastAssistantMessage.getContent()
                : (session.getLastMessage() != null ? session.getLastMessage().getContent() : "");

        return ChatResponse.builder()
                .sessionId(session.getSessionId())
                .messages(messages)
                .timestamp(LocalDateTime.now())
                .message(lastMessage)
                .detectedLanguage(
                        StringUtils.hasText(session.getDetectedLanguage()) ? session.getDetectedLanguage() : "en")
                .responseType(lastResponseType)
                .providers(providers)
                .availableTimeSlots(availableSlots)
                .build();
    }

    @Override
    public ChatResponse startNewChat(User currentUser) {
        Long userId = currentUser != null ? currentUser.getId() : null;

        if (userId != null) {
            chatSessionRepository.findActiveSessionByUser(userId).ifPresent(session -> {
                session.endSession();
                chatSessionRepository.save(session);
            });
        }

        ChatSession session = ChatSession.builder()
                .userId(userId)
                .isActive(true)
                .detectedLanguage("ar")
                .build();

        ChatSession savedSession = chatSessionRepository.save(session);

        return ChatResponse.builder()
                .sessionId(savedSession.getSessionId())
                .timestamp(LocalDateTime.now())
                .message("New chat started. How can I help you today?")
                .responseType(ChatResponseType.TEXT)
                .detectedLanguage("ar")
                .build();
    }

    @Scheduled(fixedDelay = 3600000)
    public void deleteEmptySessions() {
        log.info("Starting cleanup of empty chat sessions");

        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1);
        List<ChatSession> oldSessions = chatSessionRepository
                .findByStartTimeBefore(cutoffTime);

        int deletedCount = 0;

        for (ChatSession session : oldSessions) {
            List<ChatMessage> messages = chatMessageRepository
                    .findByChatSessionSessionId(session.getSessionId());

            if (messages.isEmpty()) {
                chatSessionRepository.delete(session);
                deletedCount++;
                log.debug("Deleted empty session: {}", session.getSessionId());
            }
        }

        log.info("Cleanup completed. Deleted {} empty sessions.", deletedCount);
    }

    @Override
    @Transactional
    public boolean deleteChatbotChatSession(String sessionId, User currentUser) {
        log.info("User {} deleting chat with session: {}", currentUser.getId(), sessionId);

        if (sessionId == null || sessionId.trim().isEmpty()) {
            log.warn("Attempted to delete chat with null or empty session ID");
            return false;
        }

        try {
            Optional<ChatSession> sessionOptional = chatSessionRepository.findById(sessionId);

            if (sessionOptional.isEmpty()) {
                log.warn("Chat session not found with ID: {}", sessionId);
                return false;
            }

            ChatSession session = sessionOptional.get();

            if (!session.getUserId().equals(currentUser.getId())) {
                log.warn("User {} attempted to delete session {} owned by user {}",
                        currentUser.getId(), sessionId, session.getUserId());
                return false;
            }

            chatSessionRepository.delete(session);
            log.info("Successfully deleted chat session: {}", sessionId);
            return true;

        } catch (Exception e) {
            log.error("Error deleting chat session {}: {}", sessionId, e.getMessage(), e);
            return false;
        }
    }
    // ===== SESSION MANAGEMENT =====

    public ChatSession resolveSession(AiChatRequest request, Long userId) {
        if (StringUtils.hasText(request.getSessionId())) {
            ChatSession session = chatSessionRepository.findBySessionIdAndIsActiveTrue(request.getSessionId())
                    .orElseThrow(() -> new BadRequestException("Invalid or expired session ID"));

            if (!Objects.equals(session.getUserId(), userId)) {
                throw new ForbiddenException("You are not allowed to access this chat session");
            }

            return session;
        }

        if (userId != null) {
            Optional<ChatSession> activeSession = chatSessionRepository.findActiveSessionByUser(userId);
            if (activeSession.isPresent()) {
                return activeSession.get();
            }
        }

        ChatSession session = ChatSession.builder()
                .userId(userId)
                .isActive(true)
                .detectedLanguage("ar")
                .build();
        return chatSessionRepository.save(session);
    }

    // ===== MESSAGE PERSISTENCE =====

    public void saveUserMessage(ChatSession session, Long userId, String content) {
        ChatMessage userMessage = ChatMessage.builder()
                .chatSession(session)
                .senderId(userId != null ? userId : 0L)
                .senderRole(MessageRole.USER)
                .content(content)
                .type(MessageType.TEXT)
                .isRead(true)
                .build();
        chatMessageRepository.save(userMessage);
    }

    public void saveAssistantMessage(ChatSession session, ChatResponse response) {
        String content = response != null ? response.getMessage() : null;

        ChatMessage assistantMessage = ChatMessage.builder()
                .chatSession(session)
                .senderId(0L)
                .senderRole(MessageRole.ASSISTANT)
                .content(content)
                .type(MessageType.TEXT)
                .responseType(response != null ? response.getResponseType() : ChatResponseType.TEXT)
                .providersPayload(serializeProviders(response != null ? response.getProviders() : null))
                .availableSlotsPayload(
                        serializeAvailableSlots(response != null ? response.getAvailableTimeSlots() : null))
                .isRead(true)
                .build();
        chatMessageRepository.save(assistantMessage);

        if (response != null && response.getResponseType() == ChatResponseType.PROVIDER_LIST
                && response.getProviders() != null && !response.getProviders().isEmpty()) {
            ProviderSummaryResponse primary = response.getProviders().get(0);
            session.setLastSuggestedProviderId(primary.getId());
            session.setLastSuggestedProviderName(primary.getName());
            chatSessionRepository.save(session);
        }
    }

    public void applySessionProviderContext(UnifiedAssistantResponse unified, ChatSession session) {
        if (unified == null || session == null) {
            return;
        }

        if ((unified.action == Action.CHECK_AVAILABILITY || unified.action == Action.CREATE_BOOKING)
                && unified.providerId == null
                && !StringUtils.hasText(unified.providerName)
                && session.getLastSuggestedProviderId() != null) {

            unified.providerId = session.getLastSuggestedProviderId();
            unified.providerName = session.getLastSuggestedProviderName();
            log.info("Applied session provider context for {}: {} (ID: {})",
                    unified.action, unified.providerName, unified.providerId);
        }
    }

    // ===== CACHING =====

    public List<ServiceType> getCachedServiceTypes() {
        CachedValue<List<ServiceType>> cached = serviceTypesCache;
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }
        List<ServiceType> fresh = serviceTypeRepository.findAll();
        serviceTypesCache = new CachedValue<>(fresh, CATALOG_CACHE_TTL);
        return fresh;
    }

    public List<ServiceCategory> getCachedCategories() {
        CachedValue<List<ServiceCategory>> cached = categoriesCache;
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }
        List<ServiceCategory> fresh = categoryRepository.findAll();
        categoriesCache = new CachedValue<>(fresh, CATALOG_CACHE_TTL);
        return fresh;
    }

    public String getServiceCatalogJsonOrNull() {
        CachedValue<String> cached = serviceCatalogJsonCache;
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        List<ServiceType> allServices = getCachedServiceTypes();
        String json;
        if (allServices.isEmpty() || allServices.size() > MAX_SERVICES_FOR_INLINE_CATALOG) {
            json = null;
        } else {
            json = "[" + allServices.stream()
                    .map(st -> String.format("{\"id\":%d,\"name\":\"%s\",\"nameAr\":\"%s\"}",
                            st.getId(), escapeJson(st.getName()), escapeJson(st.getNameAr())))
                    .collect(Collectors.joining(",")) + "]";
        }

        serviceCatalogJsonCache = new CachedValue<>(json, CATALOG_CACHE_TTL);
        return json;
    }

    // ===== SERVICE RESOLUTION =====

    public ServiceType resolveServiceTypeByMeaning(String userMessage) {
        List<ServiceType> allServices = getCachedServiceTypes();

        if (allServices.isEmpty()) {
            log.warn("No service types in database");
            return null;
        }

        String cacheKey = normalize(userMessage);
        CachedValue<Long> cached = StringUtils.hasText(cacheKey) ? serviceTypeByMeaningCache.get(cacheKey) : null;
        if (cached != null && !cached.isExpired()) {
            Long cachedId = cached.value;
            if (cachedId == null) {
                return null;
            }
            return allServices.stream().filter(st -> st.getId().equals(cachedId)).findFirst().orElse(null);
        }

        ServiceType resolved;
        if (allServices.size() <= 50 && geminiClient.isEnabled()) {
            resolved = resolveServiceTypeWithAiByMeaning(userMessage, allServices);
        } else {
            resolved = resolveServiceTypeWithCategoriesThenAi(userMessage);
        }

        if (StringUtils.hasText(cacheKey)) {
            serviceTypeByMeaningCache.put(cacheKey,
                    new CachedValue<>(resolved != null ? resolved.getId() : null, AI_LOOKUP_CACHE_TTL));
        }

        return resolved;
    }

    public ServiceType resolveServiceTypeByName(String name) {
        if (!StringUtils.hasText(name))
            return null;
        return getCachedServiceTypes().stream()
                .filter(st -> name.equalsIgnoreCase(st.getName()) ||
                        name.equalsIgnoreCase(st.getNameAr()) ||
                        st.getName().toLowerCase().contains(name.toLowerCase()) ||
                        st.getNameAr().toLowerCase().contains(name.toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    public Long resolveProviderIdByName(String providerName) {
        if (!StringUtils.hasText(providerName))
            return null;

        String targetName = normalize(providerName);
        List<Provider> providers = providerRepository.findAll();

        Optional<Provider> exact = providers.stream()
                .filter(p -> normalize(p.getName()).equals(targetName))
                .findFirst();
        if (exact.isPresent())
            return exact.get().getId();

        return providers.stream()
                .filter(p -> normalize(p.getName()).contains(targetName) || targetName.contains(normalize(p.getName())))
                .findFirst()
                .map(Provider::getId)
                .orElse(null);
    }

    // ===== PROVIDER SEARCH =====

    public List<ProviderSummaryResponse> findAndSortProviders(Long serviceTypeId,
            LocationDTO userLocation, Integer radiusKm, User currentUser) {

        List<Provider> providers = providerRepository.findByServiceTypeIdAndVerificationStatus(
                serviceTypeId, VerificationStatus.VERIFIED);

        if (providers.isEmpty()) {
            log.info("No verified providers for service type: {}", serviceTypeId);
            return new ArrayList<>();
        }

        List<ProviderSummaryResponse> responses = providers.stream()
                .map(provider -> toProviderSummaryWithDistance(provider, userLocation, currentUser))
                .collect(Collectors.toList());

        responses.sort((p1, p2) -> {
            double score1 = calculateScore(p1);
            double score2 = calculateScore(p2);
            return Double.compare(score2, score1);
        });

        if (radiusKm != null && radiusKm > 0) {
            responses = responses.stream()
                    .filter(provider -> provider.getDistance() == null || provider.getDistance() <= radiusKm)
                    .collect(Collectors.toList());
        }

        return responses;
    }

    public LocationDTO resolveSearchLocation(User currentUser, AiChatRequest request) {
        if (isConsumer(currentUser)) {
            Optional<Consumer> consumer = consumerRepository.findById(currentUser.getId());
            if (consumer.isPresent() && consumer.get().getLocation() != null) {
                return locationMapper.toDto(consumer.get().getLocation());
            }
        }
        return request.getLocation();
    }

    // ===== RESPONSE BUILDING =====

    public String buildSmartReply(List<ProviderSummaryResponse> providers, ServiceType serviceType,
            String userMessage) {

        String serviceName = serviceType.getNameAr() != null ? serviceType.getNameAr() : serviceType.getName();

        if (providers.isEmpty()) {
            return "عذراً، لم أجد أي " + serviceName
                    + " متاحين في منطقتك حالياً. حاول مرة أخرى لاحقاً.";
        }

        if (providers.size() == 1) {
            return buildSingleProviderReply(providers.get(0));
        }

        return buildMultipleProvidersReply(providers, serviceName);
    }

    private String buildSingleProviderReply(ProviderSummaryResponse p) {
        StringBuilder reply = new StringBuilder();
        reply.append("🔍 وجدت مزود خدمة واحد مناسب:\n\n");

        reply.append("👤").append(p.getName());
        reply.append("\n");

        if (p.getServiceTypeAr() != null) {
            reply.append("🔧 الخدمة: ").append(p.getServiceTypeAr());
            reply.append("\n");
        }

        if (p.getAverageRating() != null && p.getAverageRating() > 0) {
            reply.append("⭐ التقييم: ").append(String.format("%.1f", p.getAverageRating())).append("/5\n");
        }

        if (p.getDistance() != null) {
            reply.append("📍 المسافة: ").append(String.format("%.1f", p.getDistance())).append(" كم\n");
        }
        if (p.getArea() != null) {
            reply.append("🏙️ المنطقة: ").append(p.getArea());
            reply.append("\n");
        }

        if (p.getPrice() != null) {
            reply.append("💰 السعر: ").append(p.getPrice()).append(" جنيه");
            if (p.getPriceTypeAr() != null) {
                reply.append(" (").append(p.getPriceTypeAr()).append(")");
            }
            reply.append("\n");
        }

        if (p.getCancellationRate() != null) {
            reply.append("📊 معدل الإلغاء: ").append(String.format("%.1f", p.getCancellationRate())).append("%\n");
        }

        if (p.getEstimatedArrivalTime() != null) {
            reply.append("⏱️ وقت الوصول المتوقع: ").append(p.getEstimatedArrivalTime()).append(" دقيقة\n");
        }

        reply.append("\n💡 هل تريد حجز موعد معه؟ أو معرفة مواعيده المتاحة؟");
        return reply.toString();
    }

    private String buildMultipleProvidersReply(List<ProviderSummaryResponse> providers, String serviceName) {
    StringBuilder reply = new StringBuilder();
    reply.append("🔍 وجدت ").append(providers.size()).append(" ").append(serviceName).append(" مناسبين:\n\n");

    int maxDisplay = Math.min(5, providers.size());
    for (int i = 0; i < maxDisplay; i++) {
        ProviderSummaryResponse p = providers.get(i);
        
        reply.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        reply.append("【").append(i + 1).append("】 ").append(p.getName()).append("\n");
        reply.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        if (p.getAverageRating() != null && p.getAverageRating() > 0) {
            reply.append("⭐ التقييم: ").append(String.format("%.1f", p.getAverageRating())).append("\n");
        }
        
        if (p.getDistance() != null) {
            reply.append("📍 المسافة: ").append(String.format("%.1f", p.getDistance())).append(" كم\n");
        }
        
        if (p.getPrice() != null) {
            reply.append("💰 السعر: ").append(p.getPrice());
            if (p.getPriceTypeAr() != null) {
                reply.append("/").append(p.getPriceTypeAr());
            }
            reply.append("\n");
        }
        
        if (p.getArea() != null) {
            reply.append("🏙️ المنطقة: ").append(p.getArea()).append("\n");
        }
        
        reply.append("\n");
    }

    if (providers.size() > 5) {
        reply.append("...و ").append(providers.size() - 5).append(" آخرين\n\n");
    }

    reply.append("💡 اختر رقم المزود المناسب أو اسألني عن تفاصيل أكثر.");
    return reply.toString();
}

    // public String buildSolutionSuggestionReply(String normalizedMessage) {
    // if (containsAny(normalizedMessage, "كهرب", "electric", "power", "نور",
    // "لمبة", "فيشة", "breaker", "قاطع")) {
    // return "جرب أولاً: 1) تأكد إن القاطع الرئيسي شغال، 2) راجع الفيشة والريموت،
    // 3) افصل الجهاز 5 دقائق ورجعه تاني، 4) لو في شرر أو سخونة، افصل الكهرباء
    // فوراً. لو المشكلة مستمرة أقدر أدورلك على كهربائي.";
    // }

    // if (containsAny(normalizedMessage, "تكييف", "ac", "air", "cool", "برد")) {
    // return "جرب أولاً: 1) تأكد من وضع التبريد والحرارة، 2) نظف الفلتر، 3) راجع
    // البطاريات والريموت، 4) افصل التكييف 5 دقائق ثم شغله. لو ما اتحلّش أقدر أدورلك
    // على فني تكييف.";
    // }

    // if (containsAny(normalizedMessage, "مياه", "water", "تسريب", "بيسرب",
    // "حنفية", "ماسورة", "صرف")) {
    // return "جرب أولاً: 1) اقفل مصدر المياه، 2) راقب مكان التسريب، 3) تأكد إن
    // الوصلات مش مفكوكة، 4) لو في كسر واضح أو التسريب كبير أقدر أدورلك على سباك.";
    // }

    // if (containsAny(normalizedMessage, "باب", "قفل", "lock", "مفتاح", "handle"))
    // {
    // return "جرب أولاً: 1) تأكد إن الباب مش عالق، 2) استخدم زيت خفيف للمفصلة لو
    // بتحتك، 3) راجع المفتاح/القفل، 4) لو القفل مكسور أقدر أدورلك على فني.";
    // }

    // return "ممكن نبدأ بخطوات بسيطة: 1) تأكد من مصدر المشكلة، 2) افصل/شغّل الجهاز
    // لو ده آمن، 3) راقب إذا كان في جزء مفكوك أو توقف مفاجئ. لو المشكلة مستمرة أقدر
    // أدورلك على مختص مناسب.";
    // }

    public String formatAvailabilityMessage(Long providerId, LocalDate date,
            List<ScheduleResponse.TimeSlotResponse> slots) {
        if (slots == null || slots.isEmpty()) {
            return "لم يتم العثور على مواعيد متاحة للمزود " + providerId + " في تاريخ " + DATE_FORMAT.format(date);
        }

        String slotSummary = slots.stream()
                .limit(5)
                .map(slot -> TIME_FORMAT.format(slot.getStartTime()) + " - " + TIME_FORMAT.format(slot.getEndTime()))
                .collect(Collectors.joining(", "));

        return "المواعيد المتاحة للمزود " + providerId + " في تاريخ " + DATE_FORMAT.format(date) + ": " + slotSummary;
    }

    public String formatUpcomingAvailabilityMessage(String providerName,
            List<ScheduleResponse.TimeSlotResponse> slots) {
        if (slots == null || slots.isEmpty()) {
            return "لا توجد مواعيد متاحة لـ " + (providerName != null ? providerName : "هذا المزود")
                    + " في الأيام القادمة.";
        }

        Map<LocalDate, List<ScheduleResponse.TimeSlotResponse>> slotsByDate = slots.stream()
                .collect(Collectors.groupingBy(slot -> parseDateSafe(slot.getDate())));

        slotsByDate.remove(null);

        StringBuilder message = new StringBuilder();
        message.append("المواعيد المتاحة لـ ").append(providerName != null ? providerName : "المزود")
                .append(" خلال الأيام القادمة:\n\n");

        int dateCount = 0;
        for (Map.Entry<LocalDate, List<ScheduleResponse.TimeSlotResponse>> entry : slotsByDate.entrySet()) {
            if (dateCount++ >= 7)
                break;
            message.append("📅 ").append(DATE_FORMAT.format(entry.getKey())).append(":\n");
            String times = entry.getValue().stream()
                    .map(slot -> TIME_FORMAT.format(slot.getStartTime()) + " - "
                            + TIME_FORMAT.format(slot.getEndTime()))
                    .collect(Collectors.joining(", "));
            message.append("   🕐 ").append(times).append("\n");
        }

        return message.toString();
    }

    // ===== UTILITY METHODS =====

    public String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start)
            return null;
        return text.substring(start, end + 1);
    }

    public LocalDate parseDateSafe(String dateText) {
        if (!StringUtils.hasText(dateText))
            return null;
        try {
            return LocalDate.parse(dateText.trim(), DATE_FORMAT);
        } catch (Exception ex) {
            return null;
        }
    }

    public LocalTime parseTimeSafe(String timeText) {
        if (!StringUtils.hasText(timeText))
            return null;
        try {
            return LocalTime.parse(timeText.trim(), TIME_FORMAT);
        } catch (Exception ex) {
            return null;
        }
    }

    public String detectLanguage(String text) {
        if (text == null)
            return "en";
        return text.matches(".*[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF].*") ? "ar" : "en";
    }

    public boolean isConsumer(User currentUser) {
        return currentUser != null && currentUser.getRole() == UserType.CONSUMER;
    }

    public String normalize(String value) {
        if (!StringUtils.hasText(value))
            return "";
        return NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    public boolean containsAny(String normalizedText, String... keywords) {
        for (String keyword : keywords) {
            if (normalizedText.contains(normalize(keyword)))
                return true;
        }
        return false;
    }

    public String escapeJson(String value) {
        if (value == null)
            return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    public List<ChatMessage> getRecentHistory(List<ChatMessage> fullHistory, int maxTurns) {
        if (fullHistory == null || fullHistory.isEmpty()) {
            return new ArrayList<>();
        }
        if (fullHistory.size() <= maxTurns) {
            return fullHistory;
        }
        return fullHistory.subList(fullHistory.size() - maxTurns, fullHistory.size());
    }

    public List<GeminiClient.ConversationTurn> toConversationTurns(List<ChatMessage> history) {
        return history.stream()
                .map(msg -> new GeminiClient.ConversationTurn(
                        msg.getSenderRole() == MessageRole.USER ? "user" : "assistant",
                        msg.getContent()))
                .collect(Collectors.toList());
    }

    public String buildConversationContext(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "No previous conversation.";
        }

        StringBuilder context = new StringBuilder();
        context.append("## PREVIOUS CONVERSATION CONTEXT:\n");

        int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
        for (int i = start; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            String role = msg.getSenderRole() == MessageRole.USER ? "User" : "Assistant";
            context.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        return context.toString();
    }

    // ===== SERIALIZATION =====

    private String serializeProviders(List<ProviderSummaryResponse> providers) {
        if (providers == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(providers);
        } catch (Exception ex) {
            log.debug("Failed to serialize providers payload: {}", ex.getMessage());
            return null;
        }
    }

    private String serializeAvailableSlots(List<ScheduleResponse.TimeSlotResponse> slots) {
        if (slots == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(slots);
        } catch (Exception ex) {
            log.debug("Failed to serialize available slots payload: {}", ex.getMessage());
            return null;
        }
    }

    private List<ProviderSummaryResponse> parseProvidersPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(payload,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ProviderSummaryResponse.class));
        } catch (Exception ex) {
            log.debug("Failed to parse providers payload: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<ScheduleResponse.TimeSlotResponse> parseAvailableSlotsPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(payload,
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                            ScheduleResponse.TimeSlotResponse.class));
        } catch (Exception ex) {
            log.debug("Failed to parse available slots payload: {}", ex.getMessage());
            return List.of();
        }
    }

    // ===== PRIVATE HELPERS =====

    private ProviderSummaryResponse toProviderSummaryWithDistance(Provider provider, LocationDTO userLocation,
            User currentUser) {
        ProviderSummaryResponse response = providerMapper.toProviderSummaryResponse(provider);

        if (currentUser != null && currentUser.getRole() == UserType.CONSUMER) {
            try {
                DistanceResponse distanceResponse = locationService
                        .calculateDistanceBetweenConsumerAndProvider(currentUser.getId(), provider.getId());
                if (distanceResponse != null) {
                    response.setDistance(Math.round(distanceResponse.getDistanceKm() * 10.0) / 10.0);
                }
            } catch (Exception e) {
                log.debug("Could not calculate consumer-provider distance for provider {}: {}", provider.getId(),
                        e.getMessage());
            }
        } else if (userLocation != null && provider.getLocation() != null) {
            try {
                Location userLoc = locationMapper.toEntity(userLocation);
                if (userLoc.getLatitude() != null && userLoc.getLongitude() != null) {
                    double distance = provider.getLocation().calculateDistance(userLoc);
                    response.setDistance(Math.round(distance * 10.0) / 10.0);
                }
            } catch (Exception e) {
                log.debug("Could not calculate distance for provider {}: {}", provider.getId(), e.getMessage());
            }
        }

        return response;
    }

    private double calculateScore(ProviderSummaryResponse provider) {
        double score = 0.0;

        if (provider.getDistance() != null && provider.getDistance() > 0) {
            double distanceScore = Math.max(0, 50 - (provider.getDistance() * 5));
            score += distanceScore;
        } else {
            score += 25;
        }

        if (provider.getAverageRating() != null) {
            double ratingScore = provider.getAverageRating() * 10;
            score += ratingScore;
        } else {
            score += 25;
        }

        return score;
    }

    private ServiceType resolveServiceTypeWithAiByMeaning(String userMessage, List<ServiceType> allServices) {
        String servicesJson = allServices.stream()
                .map(st -> String.format(
                        "{\"id\":%d,\"name\":\"%s\",\"nameAr\":\"%s\",\"description\":\"%s\"}",
                        st.getId(),
                        escapeJson(st.getName()),
                        escapeJson(st.getNameAr()),
                        escapeJson(st.getDescription() != null ? st.getDescription() : "")))
                .collect(Collectors.joining(","));

        String prompt = "أنت مساعد ذكي. المستخدم بيوصف خدمة he/she محتاجها.\n"
                + "مهمتك: اختار أنسب خدمة من القائمة حسب المعنى، مش لازم تطابق الكلمة بالضبط.\n\n"
                + "طلب المستخدم: " + userMessage + "\n\n"
                + "الخدمات المتاحة: [" + servicesJson + "]\n\n"
                + "قواعد الاختيار:\n"
                + "- \"أنا تعبان / دكتور / عندي وجع / صحتي\" -> اختار أي خدمة طبية (Doctor, Dentist, etc)\n"
                + "- \"الحوض مسرب / سباك / مواسير\" -> اختار خدمة سباكة (Pipe Repair, Drain Cleaning, etc)\n"
                + "- \"كهربائي / فيشة / إضاءة / قطع الكهرباء\" -> اختار خدمة كهرباء\n"
                + "- \"محامي / قضية / عقد\" -> اختار خدمة قانونية\n"
                + "- \"تنظيف / كناسة / بيت\" -> اختار خدمة تنظيف\n"
                + "- \"مهندس / تصميم / بناء\" -> اختار خدمة هندسية\n\n"
                + "Return ONLY JSON: {\"serviceTypeId\": number}\n"
                + "إذا مش متأكد، ارجع {\"serviceTypeId\": null}";

        String response = geminiClient.generateJson(prompt);

        if (StringUtils.hasText(response)) {
            try {
                String json = extractJson(response);
                Long id = objectMapper.readTree(json).path("serviceTypeId").asLong();

                return allServices.stream()
                        .filter(st -> st.getId().equals(id))
                        .findFirst()
                        .orElse(null);
            } catch (Exception ex) {
                log.debug("AI meaning resolution failed: {}", ex.getMessage());
            }
        }

        return null;
    }

    private ServiceType resolveServiceTypeWithCategoriesThenAi(String userMessage) {
        List<ServiceCategory> allCategories = getCachedCategories();
        ServiceCategory detectedCategory = detectCategoryWithAi(userMessage, allCategories);

        if (detectedCategory == null) {
            return null;
        }

        List<ServiceType> servicesInCategory = serviceTypeRepository.findByCategoryId(detectedCategory.getId());

        if (servicesInCategory.isEmpty()) {
            return null;
        }

        return resolveServiceTypeWithAiByMeaning(userMessage, servicesInCategory);
    }

    private ServiceCategory detectCategoryWithAi(String userMessage, List<ServiceCategory> categories) {
        String categoriesJson = categories.stream()
                .map(cat -> String.format("{\"id\":%d,\"name\":\"%s\",\"nameAr\":\"%s\"}",
                        cat.getId(), escapeJson(cat.getName()), escapeJson(cat.getNameAr())))
                .collect(Collectors.joining(","));

        String prompt = "أنت مساعد في تطبيق يضم جميع المهن والخدمات (أطباء، مهندسين، سباكين، محامين، إلخ).\n"
                + "المستخدم يقول: " + userMessage + "\n\n"
                + "التصنيفات المتاحة: [" + categoriesJson + "]\n\n"
                + "المطلوب: اختار التصنيف الوحيد الأنسب لطلب المستخدم من حيث المعنى، مش لازم كلمة مطابقة.\n\n"
                + "أمثلة:\n"
                + "- \"أنا تعبان / دكتور / عندي وجع / صحتي\" -> اختار التصنيف الطبي (لو موجود)\n"
                + "- \"الحوض مسرب / سباك / مواسير / حمام بايظ\" -> Plumbing\n"
                + "- \"عايز محامي / قضية / contract\" -> Legal\n"
                + "- \"كهربائي / فيشة / إضاءة\" -> Electrical\n"
                + "- \"مهندس / تصميم / بناء\" -> Engineering\n"
                + "- \"أنظف / تنظيف / كناسة\" -> Cleaning\n\n"
                + "Return ONLY JSON: {\"categoryId\": number}\n"
                + "إذا مش متأكد، ترجع {\"categoryId\": null}";

        String response = geminiClient.generateJson(prompt);

        if (StringUtils.hasText(response)) {
            try {
                String json = extractJson(response);
                JsonNode node = objectMapper.readTree(json);

                if (node.has("categoryId") && !node.path("categoryId").isNull()) {
                    Long categoryId = node.path("categoryId").asLong();
                    return categories.stream()
                            .filter(cat -> cat.getId().equals(categoryId))
                            .findFirst()
                            .orElse(null);
                }
            } catch (Exception ex) {
                log.debug("AI category detection failed: {}", ex.getMessage());
            }
        }

        return null;
    }

    // ===== INNER CLASSES =====

    @Data
    public static class UnifiedAssistantResponse {
        private Action action;
        private Intent intent;
        private String reply;
        private Long providerId;
        private String providerName;
        private String serviceTypeName;
        private Long serviceTypeId;
        private LocalDate requestedDate;
        private LocalTime requestedTime;
        private String problemDescription;
        private Integer searchRadiusKm;
        private boolean needsClarification;
        private List<String> missingFields;

        public boolean isValid() {
            return action != null;
        }
    }

    public enum Intent {
        GENERAL, SEARCH_PROVIDERS, SUGGEST_SOLUTIONS, GET_AVAILABILITY, CREATE_BOOKING, GET_PROVIDER_DETAILS,
        CLARIFICATION
    }

    public enum Action {
        GENERAL, SEARCH_PROVIDERS, SUGGEST_SOLUTIONS, CHECK_AVAILABILITY, CREATE_BOOKING, GET_PROVIDER_DETAILS,
        ASK_CLARIFICATION
    }

    private static class CachedValue<T> {
        final T value;
        final Instant expiresAt;

        CachedValue(T value, Duration ttl) {
            this.value = value;
            this.expiresAt = Instant.now().plus(ttl);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}