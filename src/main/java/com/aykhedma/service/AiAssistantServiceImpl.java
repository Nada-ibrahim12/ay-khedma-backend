package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.request.BookingRequest;
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
import com.aykhedma.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
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

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final int MAX_HISTORY_TURNS = 6;
    private static final int MAX_SERVICE_CANDIDATES_FOR_AI = 8;
    private static final int DEFAULT_SEARCH_RADIUS_KM = 10;
    private static final int DEFAULT_PROVIDER_LIMIT = 5;
    private static final int MAX_SERVICES_FOR_INLINE_CATALOG = 80;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");

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

    // ---- Lightweight in-memory caches to cut down on DB hits and repeated
    // ---- Gemini calls for identical/near-identical lookups. These are simple
    // ---- TTL caches; swap for Caffeine/Redis if you need multi-instance sharing.
    private static final Duration CATALOG_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration AI_LOOKUP_CACHE_TTL = Duration.ofMinutes(10);

    private volatile CachedValue<List<ServiceType>> serviceTypesCache;
    private volatile CachedValue<List<ServiceCategory>> categoriesCache;
    private volatile CachedValue<String> serviceCatalogJsonCache;
    private final Map<String, CachedValue<Long>> serviceTypeByMeaningCache = new ConcurrentHashMap<>();

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

                    String lastMessageContent = messages.isEmpty() ? ""
                            : messages.get(messages.size() - 1).getContent();
                    LocalDateTime lastMessageTime = messages.isEmpty() ? session.getStartTime()
                            : messages.get(messages.size() - 1).getTimestamp();

                    return ChatResponse.builder()
                            .sessionId(session.getSessionId())
                            .timestamp(lastMessageTime)
                            .message(lastMessageContent)
                            .detectedLanguage(session.getDetectedLanguage())
                            .build();
                })
                .collect(Collectors.toList());
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

    public ChatResponse chat(AiChatRequest request, User currentUser) {

        String userMessage = request.getMessage();
        boolean isVoiceNote = request.getVoiceNote() != null && !request.getVoiceNote().isEmpty();

        if (isVoiceNote) {
            log.info("Received voice note in chat request, starting transcription");

            try {
                String transcribedText = speechToTextService.transcribeAudio(request.getVoiceNote());
                if (StringUtils.hasText(transcribedText)) {
                    userMessage = transcribedText;
                    log.info("Voice transcribed to: {}", userMessage);
                } else {
                    Long userId = currentUser != null ? currentUser.getId() : null;
                    ChatSession session = resolveSession(request, userId);
                    saveUserMessage(session, userId != null ? userId : 0L, "[Voice note - transcription failed]");
                    saveAssistantMessage(session, ChatResponse.builder()
                            .sessionId(session.getSessionId())
                            .timestamp(LocalDateTime.now())
                            .message("عذراً، لم أتمكن من تحويل الرسالة الصوتية. ممكن تعيد تسجيلها أو تكتبها نصياً؟")
                            .responseType(ChatResponseType.CLARIFICATION)
                            .detectedLanguage("ar")
                            .build());
                    return ChatResponse.builder()
                            .sessionId(session.getSessionId())
                            .timestamp(LocalDateTime.now())
                            .message("عذراً، لم أتمكن من تحويل الرسالة الصوتية. ممكن تعيد تسجيلها أو تكتبها نصياً؟")
                            .responseType(ChatResponseType.CLARIFICATION)
                            .detectedLanguage("ar")
                            .build();
                }
            } catch (IOException e) {
                log.error("Error occurred while transcribing voice note", e);
                userMessage = request.getMessage();
            }
        }

        if (!StringUtils.hasText(userMessage)) {
            throw new BadRequestException("Message or voice note is required");
        }

        Long userId = currentUser != null ? currentUser.getId() : null;
        ChatSession session = resolveSession(request, userId);

        String detectedLanguage = detectLanguage(userMessage);
        if (!detectedLanguage.equals(session.getDetectedLanguage())) {
            session.setDetectedLanguage(detectedLanguage);
            chatSessionRepository.save(session);
        }

        // Load only recent history
        List<ChatMessage> fullHistory = chatMessageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId());
        List<ChatMessage> recentHistory = getRecentHistory(fullHistory, MAX_HISTORY_TURNS);

        String messageToStore = isVoiceNote ? "🎤 [Voice] " + userMessage : userMessage;
        saveUserMessage(session, userId != null ? userId : 0L, messageToStore);

        AiChatRequest effectiveRequest = AiChatRequest.builder()
                .sessionId(request.getSessionId())
                .message(userMessage)
                .providerId(request.getProviderId())
                .serviceTypeId(request.getServiceTypeId())
                .requestedDate(request.getRequestedDate())
                .requestedTime(request.getRequestedTime())
                .location(request.getLocation())
                .build();

        UnifiedAssistantResponse unifiedResponse = getUnifiedResponse(effectiveRequest, currentUser, recentHistory);
        applySessionProviderContext(unifiedResponse, session);

        ChatResponse chatResponse = executeAction(effectiveRequest, currentUser, unifiedResponse, session);

        saveAssistantMessage(session, chatResponse);

        return chatResponse;
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

    private ChatSession resolveSession(AiChatRequest request, Long userId) {
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

    private List<ChatMessage> getRecentHistory(List<ChatMessage> fullHistory, int maxTurns) {
        if (fullHistory == null || fullHistory.isEmpty()) {
            return new ArrayList<>();
        }
        if (fullHistory.size() <= maxTurns) {
            return fullHistory;
        }
        return fullHistory.subList(fullHistory.size() - maxTurns, fullHistory.size());
    }

    private List<GeminiClient.ConversationTurn> toConversationTurns(List<ChatMessage> history) {
        return history.stream()
                .map(msg -> new GeminiClient.ConversationTurn(
                        msg.getSenderRole() == MessageRole.USER ? "user" : "assistant",
                        msg.getContent()))
                .collect(Collectors.toList());
    }

    private UnifiedAssistantResponse getUnifiedResponse(AiChatRequest request, User currentUser,
            List<ChatMessage> history) {
        if (!geminiClient.isEnabled()) {
            return smartFallback(request.getMessage(), history);
        }

        String serviceCatalogJson = getServiceCatalogJsonOrNull();
        String conversationContext = buildConversationContext(history);

        String systemPrompt = buildUnifiedSystemPrompt(currentUser, serviceCatalogJson);

        String userMessage = conversationContext + "\n\nUser: " + request.getMessage();

        List<GeminiClient.ConversationTurn> turns = toConversationTurns(history);
        turns.add(new GeminiClient.ConversationTurn("user", request.getMessage()));

        String modelResponse = geminiClient.generateJson(turns, systemPrompt);
        UnifiedAssistantResponse parsed = parseUnifiedResponse(modelResponse);

        if (parsed != null && parsed.isValid()) {
            if (parsed.serviceTypeId != null) {
                log.info("Gemini resolved serviceTypeId: {}", parsed.serviceTypeId);
            }
            return parsed;
        }

        log.warn("Failed to parse unified response from Gemini, using smart fallback");
        return smartFallback(request.getMessage(), history);
    }

    private String buildUnifiedSystemPrompt(User currentUser, String serviceCatalogJson) {
        String userRole = (currentUser != null && currentUser.getRole() != null)
                ? currentUser.getRole().name()
                : "anonymous";

        String lastProviderContext = "";
        String catalogSection = StringUtils.hasText(serviceCatalogJson)
                ? "\n## AVAILABLE SERVICE TYPES (choose serviceTypeId from this list by MEANING, not exact word match):\n"
                        + serviceCatalogJson
                        + "\n- If the user's request matches one of these by meaning, set \"serviceTypeId\" to its id.\n"
                        + "- If unsure or no good match, set \"serviceTypeId\": null - it will be resolved separately.\n"
                : "\n## NOTE: Service catalog is too large to include here. Always set \"serviceTypeId\": null; "
                        + "it will be resolved in a follow-up step using \"serviceTypeName\".\n";

        return """
                You are Ay Khedma AI Assistant - a comprehensive service marketplace connecting consumers with ALL types of service providers across any profession or industry.

                ## ABOUT THE APP:
                Ay Khedma is a universal platform that includes EVERY profession and service type imaginable:
                - MEDICAL: Doctors, Dentists, Specialists, Clinics, Hospitals
                - ENGINEERING: Civil, Electrical, Mechanical, Architectural, Structural
                - LEGAL: Lawyers, Legal Consultants, Document Drafting, Court Representation
                - HOME SERVICES: Plumbers, Electricians, Painters, Cleaners, Movers, HVAC
                - TECH: Programmers, IT Support, Web Developers, Cybersecurity Experts
                - EDUCATION: Teachers, Tutors, Trainers, Instructors
                - DESIGN: Graphic Designers, Interior Designers, UI/UX, Architects
                - CONSTRUCTION: Contractors, Builders, Renovation Experts
                - AUTOMOTIVE: Mechanics, Car Repair, Detailing
                - BUSINESS: Consultants, Accountants, Marketing Experts
                - CREATIVE: Photographers, Videographers, Musicians, Artists
                - WELLNESS: Coaches, Trainers, Nutritionists, Therapists
                - AND ANY OTHER PROFESSION the user might need!

                ## YOUR TASK:
                Analyze the user's message and return a SINGLE JSON response that captures their intent.

                ## AVAILABLE ACTIONS (MUST USE ONE OF THESE):
                1. SEARCH_PROVIDERS - When user wants to FIND or SEARCH for providers (e.g., "دكتور", "سباك", "عايز حد يصلح التكييف", "جيبلي دكاترة", "فين اقرب سباك")
                2. SUGGEST_SOLUTIONS - When user describes a problem, fault, damage, or malfunction and needs quick troubleshooting steps first
                3. CHECK_AVAILABILITY - When user wants to SEE AVAILABLE TIME SLOTS for a SPECIFIC provider (e.g., "وريني المواعيد عند دكتور أحمد", "طارق أحمد متاح امتى", "عندي عنده مواعيد", "شوفيلي جدول دكتور محمد")
                4. CREATE_BOOKING - When user wants to BOOK or RESERVE an appointment with specific provider, date, and time
                5. ASK_CLARIFICATION - When missing critical information needed to proceed
                6. GENERAL - For casual conversation, greetings, or questions not related to finding/booking services

                ## CRITICAL RULES FOR SUGGEST_SOLUTIONS:
                - Use SUGGEST_SOLUTIONS for simple, DIY issues (loose screw, stuck door, dripping tap)
                - Offer 2-4 practical steps the user can try themselves
                - Always include safety warnings if needed (electricity, gas, water)
                - Ask at the end if they want provider search
                - If issue seems dangerous/complex (broken pipe, electrical spark, gas leak), prioritize safety and suggest professional help immediately
                ## CRITICAL RULES FOR CHECK_AVAILABILITY:
                - Use CHECK_AVAILABILITY when user wants to VIEW available time slots for a SPECIFIC provider
                - Keywords: "وريني المواعيد", "متاح امتى", "available slots", "show me schedule", "شوفيلي جدول", "عندي عنده مواعيد"

                - REQUIRED fields:
                  * providerName OR providerId (extract from user message) - REQUIRED!

                - OPTIONAL fields (user may or may not provide):
                  * requestedDate - If user provides a date, use it. If NOT provided, set to null (DO NOT ask for it!)

                - BEHAVIOR:
                  * If user provides a specific date: check availability for that date only
                  * If user does NOT provide a date: we will show upcoming availability (next 7 days)
                  * NEVER set needsClarification=true just because requestedDate is missing
                  * NEVER add requestedDate to missingFields
                  * When user says "معاه", "هو", or any pronoun referring to a provider, USE THE PROVIDER FROM CONTEXT
                  * Do NOT ask for clarification about provider when it's implied
                  * Always maintain conversation context

                ## CRITICAL RULES FOR CREATE_BOOKING:
                - Use CREATE_BOOKING when user wants to MAKE a booking or appointment
                - Keywords: "احجز", "حجز", "book", "appointment", "عايز أحجز"
                - REQUIRED: providerName/providerId, requestedDate, requestedTime, problemDescription
                - If missing ANY of these, set needsClarification=true and list missingFields
                - For unauthenticated users, set action=ASK_CLARIFICATION with message to login
                -If the user says "معاه", "هو", "same provider", "هذا" without naming:
                → Keep providerId and providerName from the previous turn
                → Do NOT set needsClarification for provider

                ## CRITICAL RULES FOR SEARCH_PROVIDERS:
                - Use SEARCH_PROVIDERS when user wants to FIND providers
                - For urgent needs, set problemDescription="emergency" and searchRadiusKm=5
                - Always set "serviceTypeName" to your best guess of the service category in Arabic or English
                - Additionally set "serviceTypeId" using the catalog below when you can confidently match one

                ## CRITICAL: PROVIDER CONTEXT IN CONVERSATION
                - The conversation history is provided before the current user message
                - ALWAYS check the last assistant message for provider names and IDs
                - If the last assistant message mentioned a provider, use that as context
                - The providerId in the previous turn should be preserved
                """
                + catalogSection
                + """

                        ## OUTPUT FORMAT:
                        Return ONLY valid JSON. No extra text, no explanation, no markdown.
                                        The reply field must be the final user-facing answer, not a raw intent label.

                        {
                          "action": "SEARCH_PROVIDERS | SUGGEST_SOLUTIONS | CHECK_AVAILABILITY | CREATE_BOOKING | ASK_CLARIFICATION | GENERAL",
                          "intent": "SEARCH_PROVIDERS | SUGGEST_SOLUTIONS | GET_AVAILABILITY | CREATE_BOOKING | CLARIFICATION | GENERAL",
                          "reply": "natural language response to user (Arabic if user message is Arabic, otherwise English)",
                          "providerId": number or null,
                          "providerName": string or null,
                          "serviceTypeName": string or null,
                          "serviceTypeId": number or null,
                          "requestedDate": "yyyy-MM-dd" or null,
                          "requestedTime": "HH:mm" or null,
                          "problemDescription": string or null,
                          "searchRadiusKm": number or null,
                          "needsClarification": boolean,
                          "missingFields": ["field1", "field2"] or null
                        }

                        ## EXAMPLES:

                        ## EXAMPLES FOR SUGGEST_SOLUTIONS:

                        ### Example: Electrical Issue
                        User: "اللمبة مش شغالة"
                        → {"action":"SUGGEST_SOLUTIONS","intent":"SUGGEST_SOLUTIONS","reply":"جرب: 1) غير اللمبة، 2) تأكد من الفيشة، 3) راجع القاطع. لو لسة مش شغالة، أقدر أدورلك على كهربائي.","needsClarification":false}

                        ### Example: Plumbing Issue
                        User: "الحنفية بتقطر"
                        → {"action":"SUGGEST_SOLUTIONS","intent":"SUGGEST_SOLUTIONS","reply":"جرب شد الصامولة أو غير الجلدة. لو القطر مستمر، أقدر أدورلك على سباك.","needsClarification":false}

                        ### Example: When user directly asks for provider
                        User: "جيبلي سباك"
                        → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، بدورلك على سباكين في منطقتك...","serviceTypeName":"سباك","serviceTypeId":12}

                        ### Example: Complex issue that needs professional
                        User: "الدولاب كله متكسر"
                        → {"action":"SUGGEST_SOLUTIONS","intent":"SUGGEST_SOLUTIONS","reply":"دي مشكلة أكبر من إصلاح بسيط. الأفضل تستعين بنجار محترف. عايز أدورلك على نجارين في منطقتك؟"}

                        ### Example 1: CHECK_AVAILABILITY (with date provided)
                        User: "وريني المواعيد عند طارق أحمد يوم الأحد"
                        → {"action":"CHECK_AVAILABILITY","intent":"GET_AVAILABILITY","reply":"تمام، ببحثلك عن المواعيد المتاحة عند طارق أحمد يوم الأحد","providerName":"طارق أحمد","requestedDate":"2026-05-07","needsClarification":false}

                        ### Example 2: CHECK_AVAILABILITY (without date - just show upcoming)
                        User: "وريني المواعيد المتاحة عند طارق أحمد"
                        → {"action":"CHECK_AVAILABILITY","intent":"GET_AVAILABILITY","reply":"تمام، ببحثلك عن أقرب المواعيد المتاحة عند طارق أحمد","providerName":"طارق أحمد","requestedDate":null,"needsClarification":false}

                        ### Example 3: CHECK_AVAILABILITY (Arabic slang)
                        User: "شوفيلي جدول دكتور محمد"
                        → {"action":"CHECK_AVAILABILITY","intent":"GET_AVAILABILITY","reply":"ببحثلك عن جدول مواعيد دكتور محمد للأيام القادمة","providerName":"دكتور محمد","requestedDate":null,"needsClarification":false}

                        ### Example 4: CHECK_AVAILABILITY (plural)
                        User: "عند دكتور أسماء مواعيد امتى"
                        → {"action":"CHECK_AVAILABILITY","intent":"GET_AVAILABILITY","reply":"ببحثلك عن المواعيد المتاحة عند دكتورة أسماء","providerName":"دكتور أسماء","requestedDate":null,"needsClarification":false}

                        ### Example 5: CHECK_AVAILABILITY (with provider ID)
                        User: "وريني المواعيد عند provider 123"
                        → {"action":"CHECK_AVAILABILITY","intent":"GET_AVAILABILITY","reply":"ببحثلك عن المواعيد المتاحة للمزود رقم 123","providerId":123,"requestedDate":null,"needsClarification":false}

                        ### Example 6: CREATE_BOOKING (all info provided)
                        User: "عايز احجز مع دكتور محمد يوم الأحد الساعة 4، عندي صداع"
                        → {"action":"CREATE_BOOKING","intent":"CREATE_BOOKING","reply":"تمام، هحجزلك مع دكتور محمد يوم الأحد الساعة 4 لعلاج الصداع","providerName":"دكتور محمد","requestedDate":"2026-05-07","requestedTime":"16:00","problemDescription":"صداع"}

                        ### Example 7: CREATE_BOOKING (missing info - needs clarification)
                        User: "عايز احجز مع دكتور محمد"
                        → {"action":"CREATE_BOOKING","intent":"CREATE_BOOKING","reply":"تمام، هحجزلك مع دكتور محمد. محتاج منك التاريخ والوقت ووصف المشكلة","providerName":"دكتور محمد","needsClarification":true,"missingFields":["requestedDate","requestedTime","problemDescription"]}

                        ### Example 8: SEARCH_PROVIDERS (medical)
                        User: "أنا تعبان وعندي صداع"
                        → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"ممكن توصفلي الأعراض بالتفصيل عشان أقترح دكتور مناسب؟","serviceTypeName":"طبيب","needsClarification":true,"missingFields":["symptoms"]}

                        ### Example 9: SEARCH_PROVIDERS (home service)
                        User: "الحوض عندي مسرب"
                        → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، سأبحث لك عن سباكين متاحين في منطقتك.","serviceTypeName":"سباك","searchRadiusKm":10}

                        ### Example 10: SEARCH_PROVIDERS (engineering)
                        User: "عايز مهندس معماري يصمم فيلا"
                        → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، بدورلك على مهندسين معماريين متخصصين في تصميم الفلل. ممكن تقولي في أي منطقة؟","serviceTypeName":"مهندس معماري","searchRadiusKm":10}

                        ### Example 11: SEARCH_PROVIDERS (legal)
                        User: "عايز محامي للقضية بتاعتي"
                        → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"فهمت، محتاج محامي. ممكن توصف نوع القضية عشان ألاقي لك المتخصص المناسب؟","serviceTypeName":"محامي","needsClarification":true,"missingFields":["caseType"]}

                        ### Example 12: SEARCH_PROVIDERS (tech)
                        User: "عايز مبرمج يعمل لي تطبيق"
                        → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، هدورلك على مبرمجين متخصصين في تطوير التطبيقات. نوع التطبيق إيه؟","serviceTypeName":"مبرمج","needsClarification":true,"missingFields":["appType"]}

                        ### Example 13: SEARCH_PROVIDERS (urgent/emergency)
                        User: "طوارئ كهربائي البيت وقعت الكهرباء"
                        → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"فهمت، حالة طارئة. هدورلك على كهربائي قريب منك جداً.","serviceTypeName":"كهربائي","problemDescription":"emergency","searchRadiusKm":5}

                        ### Example 14: GENERAL (greeting)
                        User: "السلام عليكم"
                        → {"action":"GENERAL","intent":"GENERAL","reply":"وعليكم السلام ورحمة الله وبركاته! كيف أقدر أساعدك اليوم؟ ممكن تطلب أي خدمة - دكتور، مهندس، محامي، سباك، كهربائي، أو تحجز مع أي مزود."}

                        ### Example 15: GENERAL (question about app)
                        User: "إيه الخدمات اللي عندكم؟"
                        → {"action":"GENERAL","intent":"GENERAL","reply":"أي خدمة - أي حرفة - أي مهنة. عندنا أطباء، مهندسين، محامين، سباكين، كهربائيين، مبرمجين، معلمين، ومئات المهن التانية. ممكن تطلب أي خدمة واحنا ندورلك على أفضل مقدمي الخدمة في منطقتك."}

                        ## DATE HANDLING:
                        Today's date is: """
                + LocalDate.now() + """
                        - "بكرا" / "tomorrow" → """ + LocalDate.now().plusDays(1)
                + """
                        - "الأحد" / "Sunday" → next Sunday from today
                        - "الأحد القادم" / "next Sunday" → the Sunday after this week
                        - Always use format "yyyy-MM-dd"
                        - If user doesn't provide a date, set requestedDate = null (DO NOT ask for it in CHECK_AVAILABILITY)

                        ## TIME HANDLING:
                        - "الساعة 4" / "4" → If user provides afternoon context, use "16:00", otherwise ask for clarification
                        - "الساعة 4 العصر" / "4 PM" / "16:00" → "16:00" (24-hour format)
                        - "الساعة 10 صباحاً" / "10 AM" / "10:00" → "10:00" (24-hour format)
                        - "1:30" / "1.30" / "الساعة 1:30" → interpret as "13:30" in afternoon context, otherwise "01:30" if explicitly AM
                        - Always use 24-hour format "HH:mm" (00:00 to 23:59)
                        - IMPORTANT: When time is ambiguous (like "1:30" without AM/PM), prefer afternoon (13:xx) unless context suggests morning

                        ## CONTEXT:
                        Current user role: """
                + userRole
                + """
                        - If role = "anonymous" or role = "null" or role = "ANONYMOUS", user is NOT logged in
                        - For CREATE_BOOKING when user is anonymous, set action=ASK_CLARIFICATION and reply: "يرجى تسجيل الدخول أولاً للحجز / Please login first to book"
                        - For SEARCH_PROVIDERS and CHECK_AVAILABILITY, anonymous users ARE allowed

                        ## REMEMBER:
                        - Return ONLY valid JSON
                        - No explanation outside JSON
                        - No markdown formatting around JSON
                        - Always use double quotes for JSON properties
                        - Keep replies concise, friendly, and helpful in Arabic or English matching the user's language
                        """;
    }

    private String buildConversationContext(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "No previous conversation.";
        }

        StringBuilder context = new StringBuilder();
        context.append("## PREVIOUS CONVERSATION CONTEXT:\n");

        int start = Math.max(0, history.size() - 5);
        for (int i = start; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            String role = msg.getSenderRole() == MessageRole.USER ? "User" : "Assistant";
            context.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        return context.toString();
    }

    private UnifiedAssistantResponse parseUnifiedResponse(String modelResponse) {
        if (!StringUtils.hasText(modelResponse)) {
            return null;
        }

        try {
            String json = extractJson(modelResponse);
            if (!StringUtils.hasText(json)) {
                return null;
            }

            JsonNode node = objectMapper.readTree(json);
            UnifiedAssistantResponse response = new UnifiedAssistantResponse();

            String actionText = node.path("action").asText(null);
            if (StringUtils.hasText(actionText)) {
                try {
                    response.action = Action.valueOf(actionText.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }

            String intentText = node.path("intent").asText(null);
            if (StringUtils.hasText(intentText)) {
                try {
                    response.intent = Intent.valueOf(intentText.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }

            response.reply = node.path("reply").asText(null);
            response.providerId = node.hasNonNull("providerId") && node.path("providerId").canConvertToLong()
                    ? node.path("providerId").asLong()
                    : null;
            response.providerName = node.path("providerName").asText(null);
            response.serviceTypeName = node.path("serviceTypeName").asText(null);
            response.serviceTypeId = node.hasNonNull("serviceTypeId") && node.path("serviceTypeId").canConvertToLong()
                    ? node.path("serviceTypeId").asLong()
                    : null;
            response.requestedDate = parseDateSafe(node.path("requestedDate").asText(null));
            response.requestedTime = parseTimeSafe(node.path("requestedTime").asText(null));
            response.problemDescription = node.path("problemDescription").asText(null);
            response.searchRadiusKm = node.hasNonNull("searchRadiusKm") && node.path("searchRadiusKm").canConvertToInt()
                    ? node.path("searchRadiusKm").asInt()
                    : DEFAULT_SEARCH_RADIUS_KM;

            // Parse needsClarification from Gemini
            response.needsClarification = node.path("needsClarification").asBoolean(false);

            // Parse missingFields array and filter based on action
            if (node.has("missingFields") && node.path("missingFields").isArray()) {
                response.missingFields = new ArrayList<>();
                for (JsonNode field : node.path("missingFields")) {
                    String missingField = field.asText();

                    // For CHECK_AVAILABILITY, requestedDate is optional - don't treat as missing
                    if (response.action == Action.CHECK_AVAILABILITY && "requestedDate".equals(missingField)) {
                        log.debug("CHECK_AVAILABILITY: Ignoring requestedDate as missing field (date is optional)");
                        continue;
                    }

                    response.missingFields.add(missingField);
                }
            }

            // If missingFields is empty after filtering, reset needsClarification to false
            if (response.missingFields != null && response.missingFields.isEmpty()) {
                response.needsClarification = false;
                response.missingFields = null;
            }

            // Set default action if still null
            if (response.action == null) {
                response.action = Action.ASK_CLARIFICATION;
                response.intent = Intent.CLARIFICATION;
                if (!StringUtils.hasText(response.reply)) {
                    response.reply = "كيف يمكنني مساعدتك؟ / How can I help you?";
                }
            }

            return response;
        } catch (Exception ex) {
            log.debug("Failed to parse unified response: {}", ex.getMessage());
            return null;
        }
    }

    private UnifiedAssistantResponse smartFallback(String message, List<ChatMessage> history) {
        String normalized = normalize(message);
        UnifiedAssistantResponse response = new UnifiedAssistantResponse();
        response.searchRadiusKm = DEFAULT_SEARCH_RADIUS_KM;

        if (containsAny(normalized, "طوارئ", "emergency", "urgent", "دلوقتي", "حالاً", "اسعاف")) {
            response.action = Action.SEARCH_PROVIDERS;
            response.intent = Intent.SEARCH_PROVIDERS;
            response.problemDescription = "emergency";
            response.searchRadiusKm = 5;
            response.reply = "فهمت، محتاج مساعدة عاجلة. هدورلك على أقرب مقدم خدمة متاح. وصِف لي المشكلة باختصار.";
            return response;
        }

        response.action = Action.ASK_CLARIFICATION;
        response.intent = Intent.CLARIFICATION;
        response.reply = "كيف أقدر أساعدك؟ ممكن توصف المشكلة بالتفصيل، وهحاول ألاقي حل مناسب أو أرشحلك مختص.";
        return response;
    }

    private ChatResponse executeAction(AiChatRequest request, User currentUser,
            UnifiedAssistantResponse unified, ChatSession session) {
        ChatResponse.ChatResponseBuilder responseBuilder = ChatResponse.builder()
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .message(unified.reply)
                .detectedLanguage(detectLanguage(request.getMessage()));

        if (unified.needsClarification || unified.action == Action.ASK_CLARIFICATION) {
            return responseBuilder.responseType(ChatResponseType.CLARIFICATION).build();
        }

        return switch (unified.action) {
            case SEARCH_PROVIDERS -> handleProviderSearchAction(request, currentUser, unified, responseBuilder);
            case SUGGEST_SOLUTIONS -> handleSuggestionAction(request, unified, responseBuilder);
            case CHECK_AVAILABILITY -> handleAvailabilityAction(unified, session, responseBuilder);
            case CREATE_BOOKING -> handleBookingAction(request, currentUser, unified, responseBuilder);
            default -> responseBuilder.responseType(ChatResponseType.TEXT).build();
        };
    }

    private ChatResponse handleProviderSearchAction(AiChatRequest request, User currentUser,
            UnifiedAssistantResponse unified, ChatResponse.ChatResponseBuilder responseBuilder) {

        String userMessage = request.getMessage();


        ServiceType selectedService = null;
        if (unified.serviceTypeId != null) {
            selectedService = getCachedServiceTypes().stream()
                    .filter(st -> st.getId().equals(unified.serviceTypeId))
                    .findFirst()
                    .orElse(null);
            log.info("Using serviceTypeId from Gemini: {} -> {}", unified.serviceTypeId,
                    selectedService != null ? selectedService.getName() : "not found");        
        }

        if (selectedService == null && StringUtils.hasText(unified.serviceTypeName)) {
            selectedService = resolveServiceTypeByName(unified.serviceTypeName);
        }

        if (selectedService == null) {
            selectedService = resolveServiceTypeByMeaning(userMessage);
        }

        if (selectedService == null) {
            return responseBuilder
                    .responseType(ChatResponseType.CLARIFICATION)
                    .message("ممكن توصف لي الخدمة اللي محتاجها بشكل أوضح؟")
                    .build();
        }

        log.info("Selected service by meaning: {} ({})", selectedService.getName(), selectedService.getNameAr());

        LocationDTO searchLocation = resolveSearchLocation(currentUser, request);
        Integer radiusKm = unified.searchRadiusKm != null ? unified.searchRadiusKm : DEFAULT_SEARCH_RADIUS_KM;

        List<ProviderSummaryResponse> providers = findAndSortProviders(
                selectedService.getId(), searchLocation, radiusKm, currentUser);

        log.info("Found {} providers for service: {}", providers.size(), selectedService.getName());

        String replyMessage = buildSmartReply(providers, selectedService, userMessage);

        return responseBuilder
                .responseType(ChatResponseType.PROVIDER_LIST)
                .providers(providers)
                .message(replyMessage)
                .build();
    }

    private ServiceType resolveServiceTypeByName(String name) {
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

    private ChatResponse handleSuggestionAction(AiChatRequest request,
            UnifiedAssistantResponse unified, ChatResponse.ChatResponseBuilder responseBuilder) {

        String suggestionReply = unified.reply;

        if (!StringUtils.hasText(suggestionReply)) {
            suggestionReply = "ممكن توضح المشكلة بالتفصيل عشان أقدر أساعدك بشكل أفضل.";
        }

        return responseBuilder
                .responseType(ChatResponseType.SUGGESTION)
                .message(suggestionReply)
                .build();
    }

    private ServiceType resolveServiceTypeByMeaning(String userMessage) {

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

    // ---- Catalog caching helpers ----

    private List<ServiceType> getCachedServiceTypes() {
        CachedValue<List<ServiceType>> cached = serviceTypesCache;
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }
        List<ServiceType> fresh = serviceTypeRepository.findAll();
        serviceTypesCache = new CachedValue<>(fresh, CATALOG_CACHE_TTL);
        return fresh;
    }

    private List<ServiceCategory> getCachedCategories() {
        CachedValue<List<ServiceCategory>> cached = categoriesCache;
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }
        List<ServiceCategory> fresh = categoryRepository.findAll();
        categoriesCache = new CachedValue<>(fresh, CATALOG_CACHE_TTL);
        return fresh;
    }

    private String getServiceCatalogJsonOrNull() {
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

    private List<ProviderSummaryResponse> findAndSortProviders(Long serviceTypeId,
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

    private double calculateScore(ProviderSummaryResponse provider) {
        double score = 0.0;

        // Weight 1: Distance (closer is better) - max 50 points
        if (provider.getDistance() != null && provider.getDistance() > 0) {
            // Distance 0km = 50 points, distance 10km = 0 points
            double distanceScore = Math.max(0, 50 - (provider.getDistance() * 5));
            score += distanceScore;
        } else {
            score += 25; // average if no distance
        }

        // Weight 2: Rating - max 50 points
        if (provider.getAverageRating() != null) {
            double ratingScore = provider.getAverageRating() * 10;
            score += ratingScore;
        } else {
            score += 25;
        }

        return score;
    }

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
                    response.setDistance(Math.round(distance * 10.0) / 10.0); // round to 1 decimal
                }
            } catch (Exception e) {
                log.debug("Could not calculate distance for provider {}: {}", provider.getId(), e.getMessage());
            }
        }

        return response;
    }

    private String buildSmartReply(List<ProviderSummaryResponse> providers, ServiceType serviceType,
            String userMessage) {

        String serviceName = serviceType.getNameAr() != null ? serviceType.getNameAr() : serviceType.getName();

        if (providers.isEmpty()) {
            return "عذراً، لم أجد أي " + serviceName
                    + " متاحين في منطقتك حالياً. جرب توسيع نطاق البحث أو حاول مرة أخرى لاحقاً.";
        }

        if (providers.size() == 1) {
            ProviderSummaryResponse p = providers.get(0);
            String distanceText = p.getDistance() != null ? " (يبعد " + p.getDistance() + " كم)" : "";
            String ratingText = p.getAverageRating() != null ? " - تقييم " + p.getAverageRating() + "/5" : "";
            return "وجدت " + serviceName + " واحد مناسب" + distanceText + ratingText + ".\n" +
                    "الاسم: " + p.getName();
        }

        // 2+ providers
        String topProviders = providers.stream()
                .limit(3)
                .map(p -> {
                    String name = p.getName();
                    String rating = p.getAverageRating() != null ? " ⭐" + p.getAverageRating() : "";
                    String distance = p.getDistance() != null ? " 📍" + p.getDistance() + "km" : "";
                    return name + rating + distance;
                })
                .collect(Collectors.joining("\n• ", "• ", ""));

        return "وجدت " + providers.size() + " " + serviceName + " مناسبين:\n" + topProviders;
    }

    /**
     * Helper: escape JSON strings
     */
    private String escapeJson(String value) {
        if (value == null)
            return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private List<ProviderSummaryResponse> searchProvidersOptimized(ServiceType serviceType,
            String queryText, LocationDTO locationDTO, Integer radiusKm) {

        log.info("Searching for providers - serviceType: {}, queryText: {}, locationDTO: {}",
                serviceType != null ? serviceType.getName() : "null", queryText, locationDTO);

        List<Provider> providers;

        if (serviceType != null) {
            log.info("Finding providers by serviceTypeId: {} and VERIFIED status", serviceType.getId());
            providers = providerRepository.findByServiceTypeIdAndVerificationStatus(
                    serviceType.getId(), VerificationStatus.VERIFIED);
        } else {
            log.info("Finding all VERIFIED providers");
            providers = providerRepository.findByVerificationStatus(VerificationStatus.VERIFIED);
        }

        log.info("Found {} providers from database", providers.size());

        if (!providers.isEmpty()) {
            providers.forEach(p -> log.info("   - Provider: id={}, name={}, serviceType={}",
                    p.getId(), p.getName(),
                    p.getServiceType() != null ? p.getServiceType().getName() : "null"));
        }
        Set<String> queryTokens = tokenize(queryText);
        if (!queryTokens.isEmpty()) {
            providers = providers.stream()
                    .filter(p -> matchesSearchText(p, serviceType, queryTokens))
                    .sorted((p1, p2) -> Integer.compare(scoreProvider(p2, queryTokens), scoreProvider(p1, queryTokens)))
                    .limit(DEFAULT_PROVIDER_LIMIT * 2)
                    .collect(Collectors.toList());
        }

        return providers.stream()
                .limit(DEFAULT_PROVIDER_LIMIT)
                .map(p -> toProviderSummary(p, locationDTO, radiusKm))
                .sorted(Comparator.comparing(ProviderSummaryResponse::getDistance,
                        Comparator.nullsLast(Double::compareTo)))
                .collect(Collectors.toList());
    }

    private ChatResponse handleBookingAction(AiChatRequest request, User currentUser,
            UnifiedAssistantResponse unified, ChatResponse.ChatResponseBuilder responseBuilder) {

        if (!isConsumer(currentUser)) {
            return responseBuilder
                    .responseType(ChatResponseType.CLARIFICATION)
                    .message("يرجى تسجيل الدخول كمستهلك لإنشاء حجز.")
                    .build();
        }

        Long providerId = unified.providerId;
        String providerName = unified.providerName;

        if (providerId == null) {
            ChatSession session = chatSessionRepository.findById(request.getSessionId()).orElse(null);
            if (session != null && session.getLastSuggestedProviderId() != null) {
                providerId = session.getLastSuggestedProviderId();
                providerName = session.getLastSuggestedProviderName();
                log.info("Using provider from session: {} (ID: {})", providerName, providerId);
            }
        }

        if (providerId == null && StringUtils.hasText(unified.providerName)) {
            providerId = resolveProviderIdByName(unified.providerName);
            log.info("Resolved provider by name from unified: {} -> ID {}", providerName, providerId);
        }

        if (providerId == null) {
            return responseBuilder
                    .responseType(ChatResponseType.CLARIFICATION)
                    .message("ممكن توضح أي مزود تقصد؟ / Could you specify which provider you mean?")
                    .build();
        }

        if (unified.requestedDate == null || unified.requestedTime == null || !StringUtils.hasText(unified.problemDescription)) {

            List<String> missing = new ArrayList<>();
            if (unified.requestedDate == null)
                missing.add("date");
            if (unified.requestedTime == null)
                missing.add("time");
            if (!StringUtils.hasText(unified.problemDescription))
                missing.add("problemDescription");

            return responseBuilder
                    .responseType(ChatResponseType.BOOKING_REDIRECT)
                    .message("محتاج هذه المعلومات لإتمام الحجز: " + String.join(", ", missing))
                    .build();
        }

        try {
            Provider provider = providerRepository.findById(providerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

            if (provider.getSchedule() == null) {
                return responseBuilder
                        .responseType(ChatResponseType.ERROR)
                        .message("لا يمتلك المزود جدول مواعيد محدد.")
                        .build();
            }

            boolean isTimeAvailable = timeSlotRepository.isTimeWithinAvailableSlot(
                    provider.getSchedule().getId(),
                    unified.requestedDate,
                    unified.requestedTime);

            if (!isTimeAvailable) {
                List<ScheduleResponse.TimeSlotResponse> availableSlots = providerService
                        .getAvailableTimeSlots(providerId, unified.requestedDate);

                String slotMessage;
                if (availableSlots.isEmpty()) {
                    slotMessage = "للأسف، لا توجد مواعيد متاحة في هذا التاريخ. الرجاء اختيار تاريخ آخر.";
                } else {
                    slotMessage = "المكان المطلوب غير متاح. اختر من المواعيد المتاحة التالية:";
                }

                return responseBuilder
                        .responseType(ChatResponseType.AVAILABLE_SLOTS)
                        .availableTimeSlots(availableSlots)
                        .message(slotMessage)
                        .build();
            }

            BookingResponse bookingResponse = bookingService.requestBooking(currentUser.getId(),
                    BookingRequest.builder()
                            .providerId(providerId)
                            .requestedDate(unified.requestedDate)
                            .requestedTime(unified.requestedTime)
                            .problemDescription(unified.problemDescription)
                            .build());

            return responseBuilder
                    .responseType(ChatResponseType.BOOKING_CREATED)
                    .booking(bookingResponse)
                    .message("تم إنشاء طلب الحجز بنجاح رقم #" + bookingResponse.getId())
                    .build();
        } catch (BadRequestException ex) {
            log.warn("Booking validation failed: {}", ex.getMessage());

            if (ex.getMessage() != null && ex.getMessage().contains("TimeSlot not available")) {
                List<ScheduleResponse.TimeSlotResponse> availableSlots = providerService
                        .getAvailableTimeSlots(providerId, unified.requestedDate);

                String slotMessage;
                if (availableSlots.isEmpty()) {
                    slotMessage = "للأسف، لا توجد مواعيد متاحة في هذا التاريخ. الرجاء اختيار تاريخ آخر.";
                } else {
                    slotMessage = "المكان المطلوب غير متاح. اختر من المواعيد المتاحة التالية:";
                }

                return responseBuilder
                        .responseType(ChatResponseType.AVAILABLE_SLOTS)
                        .availableTimeSlots(availableSlots)
                        .message(slotMessage)
                        .build();
            }

            return responseBuilder
                    .responseType(ChatResponseType.ERROR)
                    .message("خطأ في البيانات: " + ex.getMessage())
                    .build();
        } catch (Exception ex) {
            log.error("Booking creation failed: {}", ex.getMessage());
            return responseBuilder
                    .responseType(ChatResponseType.ERROR)
                    .message("حدث خطأ أثناء إنشاء الحجز. حاول مرة أخرى.")
                    .build();
        }
    }

    private ChatResponse handleAvailabilityAction(UnifiedAssistantResponse unified, ChatSession session,
            ChatResponse.ChatResponseBuilder responseBuilder) {

        Long providerId = unified.providerId;

        if (providerId == null && StringUtils.hasText(unified.providerName)) {
            providerId = resolveProviderIdByName(unified.providerName);
        }

        if (providerId == null && session != null && session.getLastSuggestedProviderId() != null) {
            providerId = session.getLastSuggestedProviderId();
            if (!StringUtils.hasText(unified.providerName)) {
                unified.providerName = session.getLastSuggestedProviderName();
            }
            log.debug("Using last suggested provider context for availability check: {}",
                    session.getLastSuggestedProviderName());
        }

        if (providerId == null) {
            return responseBuilder
                    .responseType(ChatResponseType.CLARIFICATION)
                    .message("محتاج اسم المزود عشان أقدر أوريك مواعيده. ممكن تقولي اسمه بالكامل؟")
                    .build();
        }

        LocalDate targetDate = unified.requestedDate;
        List<ScheduleResponse.TimeSlotResponse> slots;

        if (targetDate == null) {
            log.info("No date specified for provider {}, fetching upcoming availability for next 7 days", providerId);
            slots = providerService.getAvailableTimeSlotsForDateRange(providerId,
                    LocalDate.now().plusDays(1),
                    LocalDate.now().plusDays(7));

            if (slots.isEmpty()) {
                return responseBuilder
                        .responseType(ChatResponseType.AVAILABLE_SLOTS)
                        .availableTimeSlots(slots)
                        .message("عذراً، لا توجد مواعيد متاحة حالياً للأيام القادمة. حاول مرة أخرى لاحقاً.")
                        .build();
            }

            return responseBuilder
                    .responseType(ChatResponseType.AVAILABLE_SLOTS)
                    .availableTimeSlots(slots)
                    .message(formatUpcomingAvailabilityMessage(unified.providerName, slots))
                    .build();
        } else {
            slots = providerService.getAvailableTimeSlots(providerId, targetDate);

            return responseBuilder
                    .responseType(ChatResponseType.AVAILABLE_SLOTS)
                    .availableTimeSlots(slots)
                    .message(formatAvailabilityMessage(providerId, targetDate, slots))
                    .build();
        }
    }

    private String formatUpcomingAvailabilityMessage(String providerName,
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

    private LocationDTO resolveSearchLocation(User currentUser, AiChatRequest request) {
        if (isConsumer(currentUser)) {
            Optional<Consumer> consumer = consumerRepository.findById(currentUser.getId());
            if (consumer.isPresent() && consumer.get().getLocation() != null) {
                return locationMapper.toDto(consumer.get().getLocation());
            }
        }
        return request.getLocation();
    }

    private Long resolveProviderIdByName(String providerName) {
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

    private int scoreProvider(Provider provider, Set<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty())
            return 0;

        String providerText = normalize(
                (provider.getName() != null ? provider.getName() : "") + " " +
                        (provider.getBio() != null ? provider.getBio() : "") + " " +
                        (provider.getServiceType() != null ? provider.getServiceType().getName() : "") + " " +
                        (provider.getServiceType() != null ? provider.getServiceType().getNameAr() : ""));

        return (int) queryTokens.stream().filter(providerText::contains).count();
    }

    private boolean matchesSearchText(Provider provider, ServiceType serviceType, Set<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty())
            return true;
        return scoreProvider(provider, queryTokens) > 0;
    }

    private ProviderSummaryResponse toProviderSummary(Provider provider, LocationDTO locationDTO, Integer radiusKm) {
        ProviderSummaryResponse response = providerMapper.toProviderSummaryResponse(provider);
        if (locationDTO != null && provider.getLocation() != null) {
            try {
                Location searchLocation = locationMapper.toEntity(locationDTO);
                if (searchLocation.getLatitude() != null && searchLocation.getLongitude() != null) {
                    double distance = provider.getLocation().calculateDistance(searchLocation);
                    response.setDistance(distance);
                }
            } catch (Exception e) {
                log.debug("Could not calculate distance for provider {}: {}", provider.getId(), e.getMessage());
            }
        }
        return response;
    }

    private Set<String> tokenize(String text) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized))
            return new HashSet<>();

        return Arrays.stream(normalized.split("\\s+"))
                .filter(token -> token.length() >= 3)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String buildProviderSummaryMessage(List<ProviderSummaryResponse> providers, ServiceType serviceType) {
        String header = serviceType != null
                ? "لقد وجدت " + providers.size() + " مقدم خدمة موثوق لـ " + serviceType.getName() + ":"
                : "لقد وجدت مقدمي الخدمة الموثوقين:";

        String details = providers.stream()
                .limit(3)
                .map(p -> p.getName() + (p.getAverageRating() != null ? " (" + p.getAverageRating() + "/5)" : ""))
                .collect(Collectors.joining(", "));

        return details.isBlank() ? header : header + " " + details;
    }

    private String formatAvailabilityMessage(Long providerId, LocalDate date,
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

    private void saveUserMessage(ChatSession session, Long userId, String content) {
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

    private void saveAssistantMessage(ChatSession session, ChatResponse response) {
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

    private void applySessionProviderContext(UnifiedAssistantResponse unified, ChatSession session) {
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

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start)
            return null;
        return text.substring(start, end + 1);
    }

    private LocalDate parseDateSafe(String dateText) {
        if (!StringUtils.hasText(dateText))
            return null;
        try {
            return LocalDate.parse(dateText.trim(), DATE_FORMAT);
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalTime parseTimeSafe(String timeText) {
        if (!StringUtils.hasText(timeText))
            return null;
        try {
            return LocalTime.parse(timeText.trim(), TIME_FORMAT);
        } catch (Exception ex) {
            return null;
        }
    }

    private String detectLanguage(String text) {
        if (text == null)
            return "en";
        return text.matches(".*[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF].*") ? "ar" : "en";
    }

    private boolean isConsumer(User currentUser) {
        return currentUser != null && currentUser.getRole() == UserType.CONSUMER;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value))
            return "";
        return NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private boolean containsAny(String normalizedText, String... keywords) {
        for (String keyword : keywords) {
            if (normalizedText.contains(normalize(keyword)))
                return true;
        }
        return false;
    }

    private String buildSolutionSuggestionReply(String normalizedMessage) {
        if (containsAny(normalizedMessage, "كهرب", "electric", "power", "نور", "لمبة", "فيشة", "breaker", "قاطع")) {
            return "جرب أولاً: 1) تأكد إن القاطع الرئيسي شغال، 2) راجع الفيشة والريموت، 3) افصل الجهاز 5 دقائق ورجعه تاني، 4) لو في شرر أو سخونة، افصل الكهرباء فوراً. لو المشكلة مستمرة أقدر أدورلك على كهربائي.";
        }

        if (containsAny(normalizedMessage, "تكييف", "ac", "air", "cool", "برد")) {
            return "جرب أولاً: 1) تأكد من وضع التبريد والحرارة، 2) نظف الفلتر، 3) راجع البطاريات والريموت، 4) افصل التكييف 5 دقائق ثم شغله. لو ما اتحلّش أقدر أدورلك على فني تكييف.";
        }

        if (containsAny(normalizedMessage, "مياه", "water", "تسريب", "بيسرب", "حنفية", "ماسورة", "صرف")) {
            return "جرب أولاً: 1) اقفل مصدر المياه، 2) راقب مكان التسريب، 3) تأكد إن الوصلات مش مفكوكة، 4) لو في كسر واضح أو التسريب كبير أقدر أدورلك على سباك.";
        }

        if (containsAny(normalizedMessage, "باب", "قفل", "lock", "مفتاح", "handle")) {
            return "جرب أولاً: 1) تأكد إن الباب مش عالق، 2) استخدم زيت خفيف للمفصلة لو بتحتك، 3) راجع المفتاح/القفل، 4) لو القفل مكسور أقدر أدورلك على فني.";
        }

        return "ممكن نبدأ بخطوات بسيطة: 1) تأكد من مصدر المشكلة، 2) افصل/شغّل الجهاز لو ده آمن، 3) راقب إذا كان في جزء مفكوك أو توقف مفاجئ. لو المشكلة مستمرة أقدر أدورلك على مختص مناسب.";
    }

    // ===== INNER CLASSES =====

    @Data
    private static class UnifiedAssistantResponse {
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

        boolean isValid() {
            return action != null;
        }
    }

    private enum Intent {
        GENERAL, SEARCH_PROVIDERS, SUGGEST_SOLUTIONS, GET_AVAILABILITY, CREATE_BOOKING, CLARIFICATION
    }

    private enum Action {
        GENERAL, SEARCH_PROVIDERS, SUGGEST_SOLUTIONS, CHECK_AVAILABILITY, CREATE_BOOKING, ASK_CLARIFICATION
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