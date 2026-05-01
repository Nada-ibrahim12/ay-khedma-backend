package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.BadRequestException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    @Override
    public ChatResponse getChat(String sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BadRequestException("Chat session not found"));

        List<ChatMessageResponse> messages = chatMessageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(sessionId)
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

        return ChatResponse.builder()
                .sessionId(session.getSessionId())
                .messages(messages)
                .timestamp(LocalDateTime.now())
                .message(session.getLastMessage() != null ? session.getLastMessage().getContent() : "")
                .detectedLanguage(session.getDetectedLanguage())
                .responseType(ChatResponseType.TEXT)
                .build();
    }

    @Override
    public ChatResponse chat(AiChatRequest request, User currentUser) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new BadRequestException("Message is required");
        }

        Long userId = currentUser != null ? currentUser.getId() : null;
        ChatSession session = resolveSession(request, userId);

        // Load only recent history
        List<ChatMessage> fullHistory = chatMessageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId());
        List<ChatMessage> recentHistory = getRecentHistory(fullHistory, MAX_HISTORY_TURNS);

        saveUserMessage(session, userId, request.getMessage());

        UnifiedAssistantResponse unifiedResponse = getUnifiedResponse(request, currentUser, recentHistory);

        ChatResponse chatResponse = executeAction(request, currentUser, unifiedResponse);

        saveAssistantMessage(session, chatResponse.getMessage());

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
                .detectedLanguage("en")
                .build();

        ChatSession savedSession = chatSessionRepository.save(session);

        return ChatResponse.builder()
                .sessionId(savedSession.getSessionId())
                .timestamp(LocalDateTime.now())
                .message("New chat started. How can I help you today?")
                .responseType(ChatResponseType.TEXT)
                .detectedLanguage("en")
                .build();
    }

    private ChatSession resolveSession(AiChatRequest request, Long userId) {
        if (StringUtils.hasText(request.getSessionId())) {
            return chatSessionRepository.findBySessionIdAndIsActiveTrue(request.getSessionId())
                    .orElseThrow(() -> new BadRequestException("Invalid or expired session ID"));
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
                .detectedLanguage("en")
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

        String systemPrompt = buildUnifiedSystemPrompt(currentUser);
        List<GeminiClient.ConversationTurn> turns = toConversationTurns(history);
        turns.add(new GeminiClient.ConversationTurn("user", request.getMessage()));

        String modelResponse = geminiClient.generateJson(turns, systemPrompt);

        UnifiedAssistantResponse parsed = parseUnifiedResponse(modelResponse);
        if (parsed != null && parsed.isValid()) {
            return parsed;
        }

        log.warn("Failed to parse unified response from Gemini, using smart fallback");
        return smartFallback(request.getMessage(), history);
    }

    private String buildUnifiedSystemPrompt(User currentUser) {
        String userRole = (currentUser != null && currentUser.getRole() != null)
                ? currentUser.getRole().name()
                : "anonymous";

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

                ## CRITICAL RULES:
                1. This platform covers ALL professions - don't assume it's only "home services"
                2. Understand the user's request by MEANING, not exact keywords
                3. If the user describes a profession or need, map it to the appropriate service type
                4. Never invent provider IDs or fake data
                5. If missing critical information, set needsClarification=true and list missingFields
                6. For unauthenticated users, prevent CREATE_BOOKING action
                7. For urgent requests (طوارئ, emergency), set searchRadiusKm=5

                ## HOW TO UNDERSTAND DIFFERENT REQUESTS:

                | User says (Arabic/English) | Meaning | Expected action |
                |---------------------------|---------|-----------------|
                | "أنا تعبان / دكتور / عندي وجع" | Needs medical service | SEARCH_PROVIDERS with serviceTypeName="طبيب" or any medical service |
                | "مهندس / تصميم فيلا / مخطط" | Needs architectural/engineering service | SEARCH_PROVIDERS with serviceTypeName related to engineering |
                | "محامي / قضية / عقد" | Needs legal service | SEARCH_PROVIDERS for lawyers or legal services |
                | "مبرمج / موقع ويب / تطبيق" | Needs tech/programming service | SEARCH_PROVIDERS for developers |
                | "سباك / مواسير / حمام بايظ" | Needs plumbing service | SEARCH_PROVIDERS for plumbing |
                | "كهربائي / فيشة / قطع الكهرباء" | Needs electrical service | SEARCH_PROVIDERS for electrical |
                | "الحوض مسرب / الحنفية بتقطر" | Needs plumbing repair | SEARCH_PROVIDERS for pipe repair |
                | "عايز أحجز مع [اسم] يوم [تاريخ]" | Wants to book appointment | CREATE_BOOKING with extracted data |
                | "متاح دلوقتي / طوارئ" | Urgent immediate need | SEARCH_PROVIDERS with problemDescription="emergency", searchRadiusKm=5 |

                ## OUTPUT FORMAT:
                Return ONLY valid JSON. No extra text, no explanation.

                {
                  "action": "SEARCH_PROVIDERS | CHECK_AVAILABILITY | CREATE_BOOKING | ASK_CLARIFICATION | GENERAL",
                  "intent": "SEARCH_PROVIDERS | GET_AVAILABILITY | CREATE_BOOKING | CLARIFICATION | GENERAL",
                  "reply": "natural language response to user (Arabic if user message is Arabic, otherwise English)",
                  "providerId": number or null,
                  "providerName": string or null,
                  "serviceTypeName": string or null,
                  "requestedDate": "yyyy-MM-dd" or null,
                  "requestedTime": "HH:mm" or null,
                  "problemDescription": string or null,
                  "searchRadiusKm": number or null,
                  "needsClarification": boolean,
                  "missingFields": ["field1", "field2"] or null
                }

                ## EXAMPLES:

                ### Medical Request:
                User: "أنا تعبان وعندي صداع"
                → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"ممكن توصفلي الأعراض بالتفصيل عشان أقترح دكتور مناسب؟","serviceTypeName":"طبيب","needsClarification":true,"missingFields":["symptoms"]}

                ### Engineering Request:
                User: "عايز مهندس معماري يصمم فيلا"
                → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، بدورلك على مهندسين معماريين متخصصين في تصميم الفلل. ممكن تقولي في أي منطقة؟","serviceTypeName":"مهندس معماري","searchRadiusKm":10}

                ### Legal Request:
                User: "عايز محامي للقضية بتاعتي"
                → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"فهمت، محتاج محامي. ممكن توصف نوع القضية عشان ألاقي لك المتخصص المناسب؟","serviceTypeName":"محامي","needsClarification":true,"missingFields":["caseType"]}

                ### Tech Request:
                User: "عايز مبرمج يعمل لي تطبيق"
                → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، هدورلك على مبرمجين متخصصين في تطوير التطبيقات. نوع التطبيق إيه؟","serviceTypeName":"مبرمج","needsClarification":true,"missingFields":["appType"]}

                ### Home Service (Plumbing):
                User: "الحوض عندي مسرب"
                → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، سأبحث لك عن سباكين متاحين في منطقتك.","serviceTypeName":"سباك","searchRadiusKm":10}

                ### Urgent:
                User: "طوارئ كهربائي البيت وقعت الكهرباء"
                → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"فهمت، حالة طارئة. هدورلك على كهربائي قريب منك جداً.","serviceTypeName":"كهربائي","problemDescription":"emergency","searchRadiusKm":5}

                ### Booking with Provider Name:
                User: "عايز احجز مع دكتور محمد يوم الأحد الساعة 4"
                → {"action":"CREATE_BOOKING","intent":"CREATE_BOOKING","reply":"تمام، هحجزلك مع دكتور محمد يوم الأحد الساعة 4. محتاج وصف المشكلة","providerName":"محمد","requestedDate":"2026-05-07","requestedTime":"16:00","needsClarification":true,"missingFields":["problemDescription"]}

                ### General Chat:
                User: "السلام عليكم"
                → {"action":"GENERAL","intent":"GENERAL","reply":"وعليكم السلام ورحمة الله! كيف أقدر أساعدك اليوم؟ ممكن تطلب أي خدمة أو تحجز مع أي مزود."}

                ## CONTEXT:
                Current user role: """
                + userRole + """
                        Today's date: """ + LocalDate.now() + """
                        """;
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
            response.requestedDate = parseDateSafe(node.path("requestedDate").asText(null));
            response.requestedTime = parseTimeSafe(node.path("requestedTime").asText(null));
            response.problemDescription = node.path("problemDescription").asText(null);
            response.searchRadiusKm = node.hasNonNull("searchRadiusKm") && node.path("searchRadiusKm").canConvertToInt()
                    ? node.path("searchRadiusKm").asInt()
                    : DEFAULT_SEARCH_RADIUS_KM;
            response.needsClarification = node.path("needsClarification").asBoolean(false);

            if (node.has("missingFields") && node.path("missingFields").isArray()) {
                response.missingFields = new ArrayList<>();
                for (JsonNode field : node.path("missingFields")) {
                    response.missingFields.add(field.asText());
                }
            }

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

        if (containsAny(normalized, "تعبان", "مرض", "sick", "doctor", "طبيب", "دكتور", "صحتي")) {
            response.action = Action.SEARCH_PROVIDERS;
            response.intent = Intent.SEARCH_PROVIDERS;
            response.serviceTypeName = "طبيب";
            response.reply = "ممكن توصفلي الأعراض عشان أقترح دكتور مناسب؟";
            response.needsClarification = true;
            response.missingFields = List.of("symptoms");
            return response;
        }

        if (containsAny(normalized, "سباك", "plumb", "مواسير", "مطبخ", "حمام", "تسريب")) {
            response.action = Action.SEARCH_PROVIDERS;
            response.intent = Intent.SEARCH_PROVIDERS;
            response.serviceTypeName = "سباك";
            response.reply = "بدورلك على سباكين موثوقين في منطقتك...";
            return response;
        }

        if (containsAny(normalized, "حجز", "book", "موعد", "appointment")) {
            response.action = Action.CREATE_BOOKING;
            response.intent = Intent.CREATE_BOOKING;
            response.reply = "تمام، هساعدك تحجز. محتاج منك: نوع الخدمة، المزود، التاريخ والوقت، ووصف المشكلة.";
            response.needsClarification = true;
            response.missingFields = List.of("serviceType", "provider", "date", "time", "problemDescription");
            return response;
        }

        response.action = Action.ASK_CLARIFICATION;
        response.intent = Intent.CLARIFICATION;
        response.reply = "كيف يمكنني مساعدتك؟ ممكن تطلب: دكتور، سباك، كهربائي، أو حجز موعد.";
        return response;
    }

    private ChatResponse executeAction(AiChatRequest request, User currentUser, UnifiedAssistantResponse unified) {
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
            case CHECK_AVAILABILITY -> handleAvailabilityAction(unified, responseBuilder);
            case CREATE_BOOKING -> handleBookingAction(request, currentUser, unified, responseBuilder);
            default -> responseBuilder.responseType(ChatResponseType.TEXT).build();
        };
    }

    private ChatResponse handleProviderSearchAction(AiChatRequest request, User currentUser,
            UnifiedAssistantResponse unified, ChatResponse.ChatResponseBuilder responseBuilder) {

        String userMessage = request.getMessage();

        ServiceType selectedService = resolveServiceTypeByMeaning(userMessage);

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

    private ServiceType resolveServiceTypeByMeaning(String userMessage) {

        List<ServiceType> allServices = serviceTypeRepository.findAll();

        if (allServices.isEmpty()) {
            log.warn("No service types in database");
            return null;
        }

        if (allServices.size() <= 50 && geminiClient.isEnabled()) {
            return resolveServiceTypeWithAiByMeaning(userMessage, allServices);
        } else {
            return resolveServiceTypeWithCategoriesThenAi(userMessage);
        }
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

        List<ServiceCategory> allCategories = categoryRepository.findAll();
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
        if (providerId == null && StringUtils.hasText(unified.providerName)) {
            providerId = resolveProviderIdByName(unified.providerName);
        }

        if (providerId == null || unified.requestedDate == null ||
                unified.requestedTime == null || !StringUtils.hasText(unified.problemDescription)) {

            List<String> missing = new ArrayList<>();
            if (providerId == null)
                missing.add("provider");
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
        } catch (Exception ex) {
            log.error("Booking creation failed: {}", ex.getMessage());
            return responseBuilder
                    .responseType(ChatResponseType.ERROR)
                    .message("حدث خطأ أثناء إنشاء الحجز. حاول مرة أخرى.")
                    .build();
        }
    }

    private ChatResponse handleAvailabilityAction(UnifiedAssistantResponse unified,
            ChatResponse.ChatResponseBuilder responseBuilder) {

        if (unified.providerId == null || unified.requestedDate == null) {
            return responseBuilder
                    .responseType(ChatResponseType.CLARIFICATION)
                    .message("محتاج معرف المزود والتاريخ للتحقق من المواعيد.")
                    .build();
        }

        List<ScheduleResponse.TimeSlotResponse> slots = providerService.getAvailableTimeSlots(
                unified.providerId, unified.requestedDate);

        return responseBuilder
                .responseType(ChatResponseType.AVAILABLE_SLOTS)
                .availableTimeSlots(slots)
                .message(formatAvailabilityMessage(unified.providerId, unified.requestedDate, slots))
                .build();
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

    private void saveAssistantMessage(ChatSession session, String content) {
        ChatMessage assistantMessage = ChatMessage.builder()
                .chatSession(session)
                .senderId(0L)
                .senderRole(MessageRole.ASSISTANT)
                .content(content)
                .type(MessageType.TEXT)
                .isRead(true)
                .build();
        chatMessageRepository.save(assistantMessage);
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

    // ==================== INNER CLASSES ====================

    @Data
    private static class UnifiedAssistantResponse {
        private Action action;
        private Intent intent;
        private String reply;
        private Long providerId;
        private String providerName;
        private String serviceTypeName;
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
        GENERAL, SEARCH_PROVIDERS, GET_AVAILABILITY, CREATE_BOOKING, CLARIFICATION
    }

    private enum Action {
        GENERAL, SEARCH_PROVIDERS, CHECK_AVAILABILITY, CREATE_BOOKING, ASK_CLARIFICATION
    }
}