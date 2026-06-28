package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.model.booking.BookingStatus;
import com.aykhedma.model.chat.ChatResponseType;
import com.fasterxml.jackson.databind.JsonNode;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.model.chat.ChatMessage;
import com.aykhedma.model.chat.ChatSession;
import com.aykhedma.model.chat.MessageRole;
import com.aykhedma.model.chat.MessageType;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.mapper.LocationMapper;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.aykhedma.service.AiAssistantServiceImpl.UnifiedAssistantResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AiAssistantOldService {

    private final AiAssistantServiceImpl baseService;
    private final GeminiClient geminiClient;
    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;
    private final ProviderService providerService;
    private final BookingService bookingService;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final ConsumerRepository consumerRepository;
    private final LocationMapper locationMapper;
    private final LocationService locationService;
    private final ObjectMapper objectMapper;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final SpeechToTextService speechToTextService;

    // ===== MAIN OLD CHAT =====

    public ChatResponse chatWithExisting(AiChatRequest request, User currentUser) {
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
                    ChatSession session = baseService.resolveSession(request, userId);
                    baseService.saveUserMessage(session, userId != null ? userId : 0L,
                            "[Voice note - transcription failed]");

                    ChatResponse response = ChatResponse.builder()
                            .sessionId(session.getSessionId())
                            .timestamp(LocalDateTime.now())
                            .message("عذراً، لم أتمكن من تحويل الرسالة الصوتية. ممكن تعيد تسجيلها أو تكتبها نصياً؟")
                            .responseType(ChatResponseType.CLARIFICATION)
                            .detectedLanguage("ar")
                            .build();
                    baseService.saveAssistantMessage(session, response);
                    return response;
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
        ChatSession session = baseService.resolveSession(request, userId);

        String detectedLanguage = baseService.detectLanguage(userMessage);
        if (!detectedLanguage.equals(session.getDetectedLanguage())) {
            session.setDetectedLanguage(detectedLanguage);
            chatSessionRepository.save(session);
        }

        List<ChatMessage> fullHistory = chatMessageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId());
        List<ChatMessage> recentHistory = baseService.getRecentHistory(fullHistory,
                AiAssistantServiceImpl.MAX_HISTORY_TURNS);

        String messageToStore = isVoiceNote ? "🎤 [Voice] " + userMessage : userMessage;
        baseService.saveUserMessage(session, userId != null ? userId : 0L, messageToStore);

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
        baseService.applySessionProviderContext(unifiedResponse, session);

        ChatResponse chatResponse = executeAction(effectiveRequest, currentUser, unifiedResponse, session);
        baseService.saveAssistantMessage(session, chatResponse);

        return chatResponse;
    }

    // ===== AI RESPONSE =====

    private UnifiedAssistantResponse getUnifiedResponse(AiChatRequest request, User currentUser,
            List<ChatMessage> history) {
        if (!geminiClient.isEnabled()) {
            return smartFallback(request.getMessage(), history);
        }

        String serviceCatalogJson = baseService.getServiceCatalogJsonOrNull();
        String conversationContext = baseService.buildConversationContext(history);

        String systemPrompt = buildUnifiedSystemPrompt(currentUser, serviceCatalogJson);

        List<GeminiClient.ConversationTurn> turns = baseService.toConversationTurns(history);
        turns.add(new GeminiClient.ConversationTurn("user", request.getMessage()));

        String modelResponse = geminiClient.generateJson(turns, systemPrompt);
        UnifiedAssistantResponse parsed = parseUnifiedResponse(modelResponse);

        if (parsed != null && parsed.isValid()) {
            if (parsed.getServiceTypeId() != null) {
                log.info("Gemini resolved serviceTypeId: {}", parsed.getServiceTypeId());
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
                - REQUIRED fields: providerName OR providerId (extract from user message) - REQUIRED!
                - OPTIONAL fields: requestedDate - If user provides a date, use it. If NOT provided, set to null (DO NOT ask for it!)
                - NEVER set needsClarification=true just because requestedDate is missing
                - NEVER add requestedDate to missingFields

                ## CRITICAL RULES FOR CREATE_BOOKING:
                - Use CREATE_BOOKING when user wants to MAKE a booking or appointment
                - Keywords: "احجز", "حجز", "book", "appointment", "عايز أحجز"
                - REQUIRED: providerName/providerId, requestedDate, requestedTime, problemDescription
                - If missing ANY of these, set needsClarification=true and list missingFields
                - For unauthenticated users, set action=ASK_CLARIFICATION with message to login

                ## CRITICAL RULES FOR SEARCH_PROVIDERS:
                - Use SEARCH_PROVIDERS when user wants to FIND providers
                - Always set "serviceTypeName" to your best guess of the service category in Arabic or English
                - Additionally set "serviceTypeId" using the catalog below when you can confidently match one

                ## CRITICAL: PROVIDER CONTEXT IN CONVERSATION
                - The conversation history is provided before the current user message
                - ALWAYS check the last assistant message for provider names and IDs
                - If the last assistant message mentioned a provider, use that as context

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
                        - Always use 24-hour format "HH:mm" (00:00 to 23:59)

                        ## CONTEXT:
                        Current user role: """
                + userRole
                + """
                        - If role = "anonymous" or role = "null" or role = "ANONYMOUS", user is NOT logged in
                        - For CREATE_BOOKING when user is anonymous, set action=ASK_CLARIFICATION

                        ## REMEMBER:
                        - Return ONLY valid JSON
                        - No explanation outside JSON
                        - No markdown formatting around JSON
                        - Always use double quotes for JSON properties
                        - Keep replies concise, friendly, and helpful in Arabic or English matching the user's language
                        """;
    }

    private UnifiedAssistantResponse parseUnifiedResponse(String modelResponse) {
        if (!StringUtils.hasText(modelResponse)) {
            return null;
        }

        try {
            String json = baseService.extractJson(modelResponse);
            if (!StringUtils.hasText(json)) {
                return null;
            }

            JsonNode node = objectMapper.readTree(json);
            UnifiedAssistantResponse response = new UnifiedAssistantResponse();

            String actionText = node.path("action").asText(null);
            if (StringUtils.hasText(actionText)) {
                try {
                    response.setAction(
                            AiAssistantServiceImpl.Action.valueOf(actionText.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }

            String intentText = node.path("intent").asText(null);
            if (StringUtils.hasText(intentText)) {
                try {
                    response.setIntent(
                            AiAssistantServiceImpl.Intent.valueOf(intentText.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }

            response.setReply(node.path("reply").asText(null));
            response.setProviderId(node.hasNonNull("providerId") && node.path("providerId").canConvertToLong()
                    ? node.path("providerId").asLong()
                    : null);
            response.setProviderName(node.path("providerName").asText(null));
            response.setServiceTypeName(node.path("serviceTypeName").asText(null));
            response.setServiceTypeId(node.hasNonNull("serviceTypeId") && node.path("serviceTypeId").canConvertToLong()
                    ? node.path("serviceTypeId").asLong()
                    : null);
            response.setRequestedDate(baseService.parseDateSafe(node.path("requestedDate").asText(null)));
            response.setRequestedTime(baseService.parseTimeSafe(node.path("requestedTime").asText(null)));
            response.setProblemDescription(node.path("problemDescription").asText(null));
            response.setSearchRadiusKm(
                    node.hasNonNull("searchRadiusKm") && node.path("searchRadiusKm").canConvertToInt()
                            ? node.path("searchRadiusKm").asInt()
                            : AiAssistantServiceImpl.DEFAULT_SEARCH_RADIUS_KM);

            response.setNeedsClarification(node.path("needsClarification").asBoolean(false));

            if (node.has("missingFields") && node.path("missingFields").isArray()) {
                List<String> missingFields = new ArrayList<>();
                for (JsonNode field : node.path("missingFields")) {
                    String missingField = field.asText();
                    if (response.getAction() == AiAssistantServiceImpl.Action.CHECK_AVAILABILITY
                            && "requestedDate".equals(missingField)) {
                        log.debug("CHECK_AVAILABILITY: Ignoring requestedDate as missing field (date is optional)");
                        continue;
                    }
                    missingFields.add(missingField);
                }
                response.setMissingFields(missingFields);
            }

            if (response.getMissingFields() != null && response.getMissingFields().isEmpty()) {
                response.setNeedsClarification(false);
                response.setMissingFields(null);
            }

            if (response.getAction() == null) {
                response.setAction(AiAssistantServiceImpl.Action.ASK_CLARIFICATION);
                response.setIntent(AiAssistantServiceImpl.Intent.CLARIFICATION);
                if (!StringUtils.hasText(response.getReply())) {
                    response.setReply("كيف يمكنني مساعدتك؟ / How can I help you?");
                }
            }

            return response;
        } catch (Exception ex) {
            log.debug("Failed to parse unified response: {}", ex.getMessage());
            return null;
        }
    }

    private UnifiedAssistantResponse smartFallback(String message, List<ChatMessage> history) {
        String normalized = baseService.normalize(message);
        UnifiedAssistantResponse response = new UnifiedAssistantResponse();
        response.setSearchRadiusKm(AiAssistantServiceImpl.DEFAULT_SEARCH_RADIUS_KM);

        if (baseService.containsAny(normalized, "طوارئ", "emergency", "urgent", "دلوقتي", "حالاً", "اسعاف")) {
            response.setAction(AiAssistantServiceImpl.Action.SEARCH_PROVIDERS);
            response.setIntent(AiAssistantServiceImpl.Intent.SEARCH_PROVIDERS);
            response.setProblemDescription("emergency");
            response.setSearchRadiusKm(5);
            response.setReply("فهمت، محتاج مساعدة عاجلة. هدورلك على أقرب مقدم خدمة متاح. وصِف لي المشكلة باختصار.");
            return response;
        }

        response.setAction(AiAssistantServiceImpl.Action.ASK_CLARIFICATION);
        response.setIntent(AiAssistantServiceImpl.Intent.CLARIFICATION);
        response.setReply("كيف أقدر أساعدك؟ ممكن توصف المشكلة بالتفصيل، وهحاول ألاقي حل مناسب أو أرشحلك مختص.");
        return response;
    }

    // ===== ACTION EXECUTION =====

    private ChatResponse executeAction(AiChatRequest request, User currentUser,
            UnifiedAssistantResponse unified, ChatSession session) {
        ChatResponse.ChatResponseBuilder responseBuilder = ChatResponse.builder()
                .sessionId(request.getSessionId())
                .timestamp(LocalDateTime.now())
                .message(unified.getReply())
                .detectedLanguage(baseService.detectLanguage(request.getMessage()));

        if (unified.isNeedsClarification() || unified.getAction() == AiAssistantServiceImpl.Action.ASK_CLARIFICATION) {
            return responseBuilder.responseType(ChatResponseType.CLARIFICATION).build();
        }

        return switch (unified.getAction()) {
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
        if (unified.getServiceTypeId() != null) {
            selectedService = baseService.getCachedServiceTypes().stream()
                    .filter(st -> st.getId().equals(unified.getServiceTypeId()))
                    .findFirst()
                    .orElse(null);
            log.info("Using serviceTypeId from Gemini: {} -> {}", unified.getServiceTypeId(),
                    selectedService != null ? selectedService.getName() : "not found");
        }

        if (selectedService == null && StringUtils.hasText(unified.getServiceTypeName())) {
            selectedService = baseService.resolveServiceTypeByName(unified.getServiceTypeName());
        }

        if (selectedService == null) {
            selectedService = baseService.resolveServiceTypeByMeaning(userMessage);
        }

        if (selectedService == null) {
            return responseBuilder
                    .responseType(ChatResponseType.CLARIFICATION)
                    .message("ممكن توصف لي الخدمة اللي محتاجها بشكل أوضح؟")
                    .build();
        }

        log.info("Selected service by meaning: {} ({})", selectedService.getName(), selectedService.getNameAr());

        LocationDTO searchLocation = baseService.resolveSearchLocation(currentUser, request);
        Integer radiusKm = unified.getSearchRadiusKm() != null ? unified.getSearchRadiusKm()
                : AiAssistantServiceImpl.DEFAULT_SEARCH_RADIUS_KM;

        List<ProviderSummaryResponse> providers = baseService.findAndSortProviders(
                selectedService.getId(), searchLocation, radiusKm, currentUser);

        log.info("Found {} providers for service: {}", providers.size(), selectedService.getName());

        String replyMessage = baseService.buildSmartReply(providers, selectedService, userMessage);

        return responseBuilder
                .responseType(ChatResponseType.PROVIDER_LIST)
                .providers(providers)
                .message(replyMessage)
                .build();
    }

    private ChatResponse handleSuggestionAction(AiChatRequest request,
            UnifiedAssistantResponse unified, ChatResponse.ChatResponseBuilder responseBuilder) {

        String suggestionReply = unified.getReply();

        if (!StringUtils.hasText(suggestionReply)) {
            suggestionReply = "ممكن توضح المشكلة بالتفصيل عشان أقدر أساعدك بشكل أفضل.";
        }

        return responseBuilder
                .responseType(ChatResponseType.SUGGESTION)
                .message(suggestionReply)
                .build();
    }

    private ChatResponse handleAvailabilityAction(UnifiedAssistantResponse unified, ChatSession session,
            ChatResponse.ChatResponseBuilder responseBuilder) {

        Long providerId = unified.getProviderId();

        if (providerId == null && StringUtils.hasText(unified.getProviderName())) {
            providerId = baseService.resolveProviderIdByName(unified.getProviderName());
        }

        if (providerId == null && session != null && session.getLastSuggestedProviderId() != null) {
            providerId = session.getLastSuggestedProviderId();
            if (!StringUtils.hasText(unified.getProviderName())) {
                unified.setProviderName(session.getLastSuggestedProviderName());
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

        LocalDate targetDate = unified.getRequestedDate();
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
                    .message(baseService.formatUpcomingAvailabilityMessage(unified.getProviderName(), slots))
                    .build();
        } else {
            slots = providerService.getAvailableTimeSlots(providerId, targetDate);

            return responseBuilder
                    .responseType(ChatResponseType.AVAILABLE_SLOTS)
                    .availableTimeSlots(slots)
                    .message(baseService.formatAvailabilityMessage(providerId, targetDate, slots))
                    .build();
        }
    }

    private ChatResponse handleBookingAction(AiChatRequest request, User currentUser,
            UnifiedAssistantResponse unified, ChatResponse.ChatResponseBuilder responseBuilder) {

        if (!baseService.isConsumer(currentUser)) {
            return responseBuilder
                    .responseType(ChatResponseType.CLARIFICATION)
                    .message("يرجى تسجيل الدخول كمستهلك لإنشاء حجز.")
                    .build();
        }

        Long providerId = unified.getProviderId();
        String providerName = unified.getProviderName();

        if (providerId == null) {
            ChatSession session = chatSessionRepository.findById(request.getSessionId()).orElse(null);
            if (session != null && session.getLastSuggestedProviderId() != null) {
                providerId = session.getLastSuggestedProviderId();
                providerName = session.getLastSuggestedProviderName();
                log.info("Using provider from session: {} (ID: {})", providerName, providerId);
            }
        }

        if (providerId == null && StringUtils.hasText(unified.getProviderName())) {
            providerId = baseService.resolveProviderIdByName(unified.getProviderName());
            log.info("Resolved provider by name from unified: {} -> ID {}", providerName, providerId);
        }

        if (providerId == null) {
            return responseBuilder
                    .responseType(ChatResponseType.CLARIFICATION)
                    .message("ممكن توضح أي مزود تقصد؟ / Could you specify which provider you mean?")
                    .build();
        }

        if (unified.getRequestedDate() == null || unified.getRequestedTime() == null
                || !StringUtils.hasText(unified.getProblemDescription())) {

            List<String> missing = new ArrayList<>();
            if (unified.getRequestedDate() == null)
                missing.add("date");
            if (unified.getRequestedTime() == null)
                missing.add("time");
            if (!StringUtils.hasText(unified.getProblemDescription()))
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
                    unified.getRequestedDate(),
                    unified.getRequestedTime());

            if (!isTimeAvailable) {
                List<ScheduleResponse.TimeSlotResponse> availableSlots = providerService
                        .getAvailableTimeSlots(providerId, unified.getRequestedDate());

                String slotMessage;
                if (availableSlots.isEmpty()) {
                    slotMessage = "للأسف، لا توجد مواعيد متاحة في هذا التاريخ. الرجاء اختيار تاريخ آخر.";
                } else {
                    slotMessage = "الموعد المطلوب غير متاح. اختر من المواعيد المتاحة التالية:";
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
                            .requestedDate(unified.getRequestedDate())
                            .requestedTime(unified.getRequestedTime())
                            .problemDescription(unified.getProblemDescription())
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
                        .getAvailableTimeSlots(providerId, unified.getRequestedDate());

                String slotMessage;
                if (availableSlots.isEmpty()) {
                    slotMessage = "للأسف، لا توجد مواعيد متاحة في هذا التاريخ. الرجاء اختيار تاريخ آخر.";
                } else {
                    slotMessage = "الموعد المطلوب غير متاح. اختر من المواعيد المتاحة التالية:";
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
}