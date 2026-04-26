package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.model.booking.BookingStatus;
import com.aykhedma.model.chat.*;
import com.aykhedma.model.emergency.EmergencyRequest;
import com.aykhedma.model.emergency.EmergencyStatus;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.mapper.LocationMapper;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.repository.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final int DEFAULT_SEARCH_RADIUS_KM = 10;
    private static final int DEFAULT_PROVIDER_LIMIT = 5;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Pattern PROVIDER_ID_PATTERN = Pattern
            .compile("(?i)provider\\s*(?:id\\s*)?(\\d+)|مزود\\s*(\\d+)");
    private static final Pattern PROVIDER_NAME_PATTERN = Pattern
            .compile("(?i)provider\\s+(?!id\\b)([\\p{L}][\\p{L}\\s]{1,50})|مزود\\s+([\\p{L}][\\p{L}\\s]{1,50})");
    private static final Pattern TIME_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b");

    private final GeminiClient geminiClient;
    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;
    private final ProviderService providerService;
    private final BookingService bookingService;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ConsumerRepository consumerRepository;
    private final EmergencyRequestRepository emergencyRequestRepository;
    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;
    private final ObjectMapper objectMapper;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

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

        String message = session.getLastMessage() != null ? session.getLastMessage().getContent() : "";

        return ChatResponse.builder()
                .sessionId(session.getSessionId())
                .messages(messages)
                .timestamp(LocalDateTime.now())
                .message(message)
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

        // Resolve or create chat session
        ChatSession session = resolveSession(request, userId);

        // Load previous messages for context
        List<ChatMessage> history = chatMessageRepository
                .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId());

        // Persist user message
        ChatMessage userMessage = ChatMessage.builder()
                .chatSession(session)
                .senderId(userId != null ? userId : 0L)
                .senderRole(MessageRole.USER)
                .content(request.getMessage())
                .type(MessageType.TEXT)
                .isRead(true)
                .build();
        chatMessageRepository.save(userMessage);

        // Classify intent with full conversation history
        AssistantPlan plan = classify(request, currentUser, history);
        plan = enrichPlanFromConversation(plan, request, history);

        ChatResponse.ChatResponseBuilder response = ChatResponse.builder()
                .sessionId(session.getSessionId())
                .timestamp(LocalDateTime.now())
                .message(plan.getReply())
                .detectedLanguage(StringUtils.hasText(plan.getDetectedLanguage()) ? plan.getDetectedLanguage() : "en");

        try {
            switch (plan.getIntent()) {
                case SEARCH_PROVIDERS -> handleProviderSearch(request, currentUser, plan, response);
                case GET_AVAILABILITY -> handleAvailability(request, plan, response);
                case CREATE_BOOKING -> handleBooking(request, currentUser, plan, response);
                // case CREATE_EMERGENCY -> handleEmergency(request, currentUser, plan,
                // response);
                case CLARIFICATION -> response.responseType(ChatResponseType.CLARIFICATION);
                case GENERAL -> response.responseType(ChatResponseType.TEXT);
            }
        } catch (Exception ex) {
            log.warn("Chatbot action failed: {}", ex.getMessage());
            response.responseType(ChatResponseType.ERROR)
                    .message("I could not complete that request right now. Please try again with more details.");
        }

        ChatResponse chatResponse = response.build();

        // Persist assistant response so it becomes part of history for next turn
        ChatMessage assistantMessage = ChatMessage.builder()
                .chatSession(session)
                .senderId(0L)
                .senderRole(MessageRole.ASSISTANT)
                .content(chatResponse.getMessage())
                .type(MessageType.TEXT)
                .isRead(true)
                .build();
        chatMessageRepository.save(assistantMessage);

        return chatResponse;
    }

    @Override
    public ChatResponse startNewChat(User currentUser) {
        Long userId = currentUser != null ? currentUser.getId() : null;

        // End any existing active session for this user
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
        // If client provides a sessionId, reuse it (must be active)
        if (StringUtils.hasText(request.getSessionId())) {
            return chatSessionRepository.findBySessionIdAndIsActiveTrue(request.getSessionId())
                    .orElseThrow(() -> new BadRequestException("Invalid or expired session ID"));
        }

        // For authenticated users, try to find an existing active session
        if (userId != null) {
            Optional<ChatSession> activeSession = chatSessionRepository.findActiveSessionByUser(userId);
            if (activeSession.isPresent()) {
                return activeSession.get();
            }
        }

        // Create a brand-new session
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .isActive(true)
                .detectedLanguage("en")
                .build();
        return chatSessionRepository.save(session);
    }

    private void handleProviderSearch(AiChatRequest request,
            User currentUser,
            AssistantPlan plan,
            ChatResponse.ChatResponseBuilder response) {
        ServiceType serviceType = resolveServiceType(plan, request.getMessage()).orElse(null);
        String queryText = firstText(plan.getProblemDescription(), request.getMessage());
        LocationDTO searchLocation = resolveSearchLocation(currentUser, request, plan);
        List<ProviderSummaryResponse> providers = searchProviders(serviceType, queryText, searchLocation,
                plan.getSearchRadiusKm());

        response.responseType(ChatResponseType.PROVIDER_LIST)
                .providers(providers)
                .message(StringUtils.hasText(plan.getReply()) ? plan.getReply()
                        : (providers.isEmpty()
                                ? "I could not find verified providers for that request yet."
                                : buildProviderSummaryMessage(providers, serviceType)));
    }

    private LocationDTO resolveSearchLocation(User currentUser, AiChatRequest request, AssistantPlan plan) {
        if (isConsumer(currentUser)) {
            Optional<Consumer> consumer = consumerRepository.findById(currentUser.getId());
            if (consumer.isPresent() && consumer.get().getLocation() != null) {
                return locationMapper.toDto(consumer.get().getLocation());
            }
        }

        if (request.getLocation() != null) {
            return request.getLocation();
        }

        return plan.getLocation();
    }

    private void handleAvailability(AiChatRequest request,
            AssistantPlan plan,
            ChatResponse.ChatResponseBuilder response) {
        Long providerId = resolveProviderId(request, plan);
        LocalDate requestedDate = firstNonNull(request.getRequestedDate(), plan.getRequestedDate());

        if (providerId == null || requestedDate == null) {
            response.responseType(ChatResponseType.CLARIFICATION)
                    .message("Please send the provider ID or name and the date so I can check availability.");
            return;
        }

        List<ScheduleResponse.TimeSlotResponse> slots = providerService.getAvailableTimeSlots(providerId,
                requestedDate);
        response.responseType(ChatResponseType.AVAILABLE_SLOTS)
                .availableTimeSlots(slots)
                .message(StringUtils.hasText(plan.getReply()) ? plan.getReply()
                        : formatAvailabilityMessage(providerId, requestedDate, slots));
    }

    private void handleBooking(AiChatRequest request,
            User currentUser,
            AssistantPlan plan,
            ChatResponse.ChatResponseBuilder response) {
        if (!isConsumer(currentUser)) {
            response.responseType(ChatResponseType.CLARIFICATION)
                    .message("You need to sign in as a consumer to create a booking request.");
            return;
        }

        Long providerId = resolveProviderId(request, plan);
        LocalDate requestedDate = firstNonNull(request.getRequestedDate(), plan.getRequestedDate());
        LocalTime requestedTime = firstNonNull(request.getRequestedTime(), plan.getRequestedTime());
        String problemDescription = firstText(plan.getProblemDescription(), request.getMessage());

        if (providerId == null || requestedDate == null || requestedTime == null
                || !StringUtils.hasText(problemDescription)) {
            response.responseType(ChatResponseType.BOOKING_REDIRECT)
                    .message(StringUtils.hasText(plan.getReply()) ? plan.getReply()
                            : "To create the booking I still need the provider, the date, the time, and a short issue description.");
            return;
        }

        BookingResponse bookingResponse = bookingService.requestBooking(currentUser.getId(), BookingRequest.builder()
                .providerId(providerId)
                .requestedDate(requestedDate)
                .requestedTime(requestedTime)
                .problemDescription(problemDescription)
                .build());

        response.responseType(ChatResponseType.BOOKING_CREATED)
                .booking(bookingResponse)
                .message(StringUtils.hasText(plan.getReply()) ? plan.getReply()
                        : "Your booking request was created successfully.");
    }

    // private void handleEmergency(AiChatRequest request,
    // User currentUser,
    // AssistantPlan plan,
    // ChatResponse.ChatResponseBuilder response) {
    // if (!isConsumer(currentUser)) {
    // response.responseType(ChatResponseType.CLARIFICATION)
    // .message("You need to sign in as a consumer to create an emergency
    // request.");
    // return;
    // }

    // Optional<ServiceType> serviceType = resolveServiceType(plan,
    // request.getMessage());
    // LocationDTO locationDTO = firstNonNull(request.getLocation(),
    // plan.getLocation());

    // if (serviceType.isEmpty() || locationDTO == null || locationDTO.getLatitude()
    // == null
    // || locationDTO.getLongitude() == null) {
    // response.responseType(ChatResponseType.CLARIFICATION)
    // .message("For an emergency request I need the service type and your live
    // location coordinates.");
    // return;
    // }

    // Consumer consumer = consumerRepository.findById(currentUser.getId())
    // .orElseThrow(() -> new BadRequestException("Consumer profile not found"));

    // Location location =
    // locationRepository.save(locationMapper.toEntity(locationDTO));
    // Integer searchRadius = plan.getSearchRadiusKm() != null ?
    // plan.getSearchRadiusKm() : DEFAULT_SEARCH_RADIUS_KM;

    // EmergencyRequest emergencyRequest = EmergencyRequest.builder()
    // .consumer(consumer)
    // .serviceType(serviceType.get())
    // .location(location)
    // .status(EmergencyStatus.BROADCASTING)
    // .searchRadius(searchRadius)
    // .description(firstText(plan.getProblemDescription(), request.getMessage()))
    // .expiresAt(LocalDateTime.now().plusMinutes(30))
    // .build();

    // EmergencyRequest savedRequest =
    // emergencyRequestRepository.save(emergencyRequest);
    // List<ProviderSummaryResponse> providers =
    // findEmergencyProviders(serviceType.get(), location, searchRadius);

    // EmergencyResponse emergencyResponse = EmergencyResponse.builder()
    // .id(savedRequest.getId())
    // .serviceType(serviceType.get().getName())
    // .location(locationMapper.toDto(location))
    // .status(savedRequest.getStatus())
    // .emergencyFeeMultiplier(savedRequest.getEmergencyFeeMultiplier())
    // .createdAt(savedRequest.getCreatedAt())
    // .expiresAt(savedRequest.getExpiresAt())
    // .providerResponses(new ArrayList<>())
    // .build();

    // response.responseType(ChatResponseType.EMERGENCY_CREATED)
    // .emergency(emergencyResponse)
    // .providers(providers)
    // .message(providers.isEmpty()
    // ? "Your emergency request was created, but I could not find nearby verified
    // providers yet."
    // : "Your emergency request was created and nearby providers were found.");
    // }

    private AssistantPlan classify(AiChatRequest request, User currentUser, List<ChatMessage> history) {
        String prompt = buildPrompt(request, currentUser, history);
        String modelResponse = geminiClient.generateJson(prompt);

        AssistantPlan plan = parsePlan(modelResponse);
        if (plan != null && plan.getIntent() != null) {
            return applyRequestOverrides(plan, request);
        }

        return applyRequestOverrides(fallbackPlan(request.getMessage()), request);
    }

    private String buildPrompt(AiChatRequest request, User currentUser, List<ChatMessage> history) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are the Ay Khedma assistant for a home-services app. ");
        builder.append("Classify the user message and extract booking or emergency fields. ");
        builder.append("Return ONLY valid JSON with this schema: ");
        builder.append("{intent, reply, detectedLanguage, providerId, providerName, serviceTypeId, serviceTypeName, ");
        builder.append(
                "requestedDate, requestedTime, problemDescription, searchRadiusKm, location{latitude,longitude,address,area,city}}. ");
        builder.append(
                "Intent must be one of GENERAL, SEARCH_PROVIDERS, GET_AVAILABILITY, CREATE_BOOKING, CLARIFICATION. ");
        builder.append("For requestedDate, use strictly 'yyyy-MM-dd' format or null. ");
        builder.append("For requestedTime, use strictly 'HH:mm' format or null. ");
        builder.append("If required data is missing, choose CLARIFICATION and ask for the missing info. ");
        builder.append(
                "If the user asks for booking or emergency creation, do not invent provider IDs or coordinates. ");
        builder.append("Current user role: ").append(currentUser != null ? currentUser.getRole() : "anonymous")
                .append('.').append(' ');

        // Inject conversation history so Gemini understands context
        if (history != null && !history.isEmpty()) {
            builder.append("Previous conversation:\n");
            for (ChatMessage msg : history) {
                String roleLabel = msg.getSenderRole() == MessageRole.USER ? "User" : "Assistant";
                builder.append(roleLabel).append(": ").append(msg.getContent()).append("\n");
            }
        }

        builder.append("User message: ").append(request.getMessage());

        if (request.getLocation() != null) {
            builder.append(" Request location: ").append(request.getLocation());
        }

        return builder.toString();
    }

    private AssistantPlan parsePlan(String modelResponse) {
        if (!StringUtils.hasText(modelResponse)) {
            return null;
        }

        try {
            String json = extractJson(modelResponse);
            if (!StringUtils.hasText(json)) {
                return null;
            }

            JsonNode node = objectMapper.readTree(json);
            AssistantPlan.AssistantPlanBuilder builder = AssistantPlan.builder();

            String intentText = node.path("intent").asText(null);
            if (StringUtils.hasText(intentText)) {
                try {
                    builder.intent(Intent.valueOf(intentText.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // Keep intent null and let fallback handling decide.
                }
            }

            builder.reply(node.path("reply").asText(null));
            builder.detectedLanguage(node.path("detectedLanguage").asText(null));

            if (node.hasNonNull("providerId") && node.path("providerId").canConvertToLong()) {
                builder.providerId(node.path("providerId").asLong());
            }
            builder.providerName(node.path("providerName").asText(null));

            if (node.hasNonNull("serviceTypeId") && node.path("serviceTypeId").canConvertToLong()) {
                builder.serviceTypeId(node.path("serviceTypeId").asLong());
            }
            builder.serviceTypeName(node.path("serviceTypeName").asText(null));

            builder.requestedDate(parseDateSafe(node.path("requestedDate").asText(null)));
            builder.requestedTime(parseTimeSafe(node.path("requestedTime").asText(null)));
            builder.problemDescription(node.path("problemDescription").asText(null));

            if (node.hasNonNull("searchRadiusKm") && node.path("searchRadiusKm").canConvertToInt()) {
                builder.searchRadiusKm(node.path("searchRadiusKm").asInt());
            }

            JsonNode locationNode = node.path("location");
            if (!locationNode.isMissingNode() && !locationNode.isNull()) {
                LocationDTO location = new LocationDTO();

                if (locationNode.hasNonNull("latitude") && locationNode.path("latitude").isNumber()) {
                    location.setLatitude(locationNode.path("latitude").asDouble());
                }
                if (locationNode.hasNonNull("longitude") && locationNode.path("longitude").isNumber()) {
                    location.setLongitude(locationNode.path("longitude").asDouble());
                }
                location.setAddress(locationNode.path("address").asText(null));
                location.setArea(locationNode.path("area").asText(null));
                location.setCity(locationNode.path("city").asText(null));

                builder.location(location);
            }

            return builder.build();
        } catch (Exception ex) {
            log.debug("Failed to parse Gemini assistant plan: {}", ex.getMessage());
            return null;
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private AssistantPlan fallbackPlan(String message) {
        String normalized = normalize(message);
        Intent intent;
        // if (containsAny(normalized, "emergency", "urgent", "asap", "help now",
        // "critical")) {
        // intent = Intent.CREATE_EMERGENCY;
        // } else
        if (containsAny(normalized,
                "book", "booking", "reserve", "appointment", "schedule",
                "احجز", "حجز", "ميعاد", "موعد", "احجزلي", "حجزلي")) {
            intent = Intent.CREATE_BOOKING;
        } else if (containsAny(normalized,
                "available", "availability", "free slot", "time slot",
                "متاح", "المواعيد", "الوقت", "اوقات", "الميعاد")) {
            intent = Intent.GET_AVAILABILITY;
        } else if (containsAny(normalized,
                "find", "search", "recommend", "suggest", "provider",
                "مزود", "مزودين", "فني", "فنيين", "عامل", "عمال", "ممكنين")) {
            intent = Intent.SEARCH_PROVIDERS;
        } else if (containsAny(normalized,
                "السبت", "الاحد", "الاثنين", "الثلاثاء", "الاربعاء", "الخميس", "الجمعة",
                "الحي", "منطقة", "شارع", "عنوان", "القادم", "بكرة", "بعد بكرة", "next", "tomorrow")) {
            intent = Intent.CLARIFICATION;
        } else {
            intent = Intent.GENERAL;
        }

        return AssistantPlan.builder()
                .intent(intent)
                .reply(defaultReply(intent))
                .detectedLanguage("en")
                .searchRadiusKm(DEFAULT_SEARCH_RADIUS_KM)
                .build();
    }

    private LocalDate parseDateSafe(String dateText) {
        if (!StringUtils.hasText(dateText)) {
            return null;
        }

        try {
            return LocalDate.parse(dateText.trim(), DATE_FORMAT);
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalTime parseTimeSafe(String timeText) {
        if (!StringUtils.hasText(timeText)) {
            return null;
        }

        try {
            return LocalTime.parse(timeText.trim(), TIME_FORMAT);
        } catch (Exception ex) {
            return null;
        }
    }

    private AssistantPlan applyRequestOverrides(AssistantPlan plan, AiChatRequest request) {
        if (request.getProviderId() != null) {
            plan.setProviderId(request.getProviderId());
        }
        if (request.getServiceTypeId() != null) {
            plan.setServiceTypeId(request.getServiceTypeId());
        }
        if (request.getRequestedDate() != null) {
            plan.setRequestedDate(request.getRequestedDate());
        }
        if (request.getRequestedTime() != null) {
            plan.setRequestedTime(request.getRequestedTime());
        }
        if (request.getLocation() != null) {
            plan.setLocation(request.getLocation());
        }
        if (!StringUtils.hasText(plan.getReply())) {
            plan.setReply(defaultReply(plan.getIntent()));
        }
        return plan;
    }

    private AssistantPlan enrichPlanFromConversation(AssistantPlan plan, AiChatRequest request,
            List<ChatMessage> history) {
        if (plan == null) {
            plan = fallbackPlan(request.getMessage());
        }

        if (plan.getProviderId() == null) {
            Long providerId = firstNonNull(extractProviderId(request.getMessage()), findRecentProviderId(history));
            if (providerId == null) {
                String providerName = firstText(
                        firstText(plan.getProviderName(), extractProviderName(request.getMessage())),
                        findRecentProviderName(history));
                providerId = resolveProviderIdByName(providerName);
                if (!StringUtils.hasText(plan.getProviderName())) {
                    plan.setProviderName(providerName);
                }
            }
            plan.setProviderId(providerId);
        }

        if (plan.getRequestedDate() == null) {
            LocalDate date = firstNonNull(parseDateFromText(request.getMessage()), findRecentRequestedDate(history));
            plan.setRequestedDate(date);
        }

        if (plan.getRequestedTime() == null) {
            LocalTime time = firstNonNull(parseTimeFromText(request.getMessage()), findRecentRequestedTime(history));
            plan.setRequestedTime(time);
        }

        if (!StringUtils.hasText(plan.getProblemDescription())) {
            String problem = firstNonNull(extractProblemDescription(request.getMessage()),
                    findRecentProblemDescription(history));
            plan.setProblemDescription(problem);
        }

        String normalized = normalize(request.getMessage());
        if (containsAny(normalized,
                "available", "availability", "time slot", "free slot",
                "متاح", "المواعيد", "الوقت", "اوقات", "available time")) {
            plan.setIntent(Intent.GET_AVAILABILITY);
            if (!StringUtils.hasText(plan.getReply())) {
                plan.setReply(defaultReply(Intent.GET_AVAILABILITY));
            }
        }

        if (plan.getIntent() == Intent.CLARIFICATION && plan.getProviderId() != null
                && plan.getRequestedDate() != null) {
            plan.setIntent(Intent.GET_AVAILABILITY);
            if (!StringUtils.hasText(plan.getReply())) {
                plan.setReply(defaultReply(Intent.GET_AVAILABILITY));
            }
        }

        return plan;
    }

    private Long resolveProviderId(AiChatRequest request, AssistantPlan plan) {
        Long providerId = firstNonNull(request.getProviderId(), plan.getProviderId());
        if (providerId != null) {
            return providerId;
        }

        String providerName = firstText(plan.getProviderName(), extractProviderName(request.getMessage()));
        return resolveProviderIdByName(providerName);
    }

    private Long extractProviderId(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher matcher = PROVIDER_ID_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String idText = firstText(matcher.group(1), matcher.group(2));
        if (!StringUtils.hasText(idText)) {
            return null;
        }

        try {
            return Long.parseLong(idText);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate parseDateFromText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        LocalDate parsed = parseDateSafe(text);
        if (parsed != null) {
            return parsed;
        }

        String normalized = normalize(text);
        if (containsAny(normalized, "next wednesday", "wednesday القادم", "الاربعاء القادم", "الأربعاء القادم")) {
            return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        }
        if (containsAny(normalized, "next thursday", "thursday القادم", "الخميس القادم")) {
            return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.THURSDAY));
        }
        if (containsAny(normalized, "next friday", "friday القادم", "الجمعة القادمة", "الجمعه القادمه")) {
            return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        }
        if (containsAny(normalized, "next saturday", "saturday القادم", "السبت القادم")) {
            return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        }
        if (containsAny(normalized, "next sunday", "sunday القادم", "الاحد القادم", "الأحد القادم")) {
            return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        }
        if (containsAny(normalized, "next monday", "monday القادم", "الاثنين القادم", "الإثنين القادم")) {
            return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
        if (containsAny(normalized, "next tuesday", "tuesday القادم", "الثلاثاء القادم")) {
            return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        }

        return null;
    }

    private String extractProviderName(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        Matcher matcher = PROVIDER_NAME_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String name = firstText(matcher.group(1), matcher.group(2));
        if (!StringUtils.hasText(name)) {
            return null;
        }

        return name.trim();
    }

    private Long resolveProviderIdByName(String providerName) {
        if (!StringUtils.hasText(providerName)) {
            return null;
        }

        String targetName = normalize(providerName);
        if (!StringUtils.hasText(targetName)) {
            return null;
        }

        List<Provider> providers = providerRepository.findAll();

        Optional<Provider> exact = providers.stream()
                .filter(provider -> normalize(provider.getName()).equals(targetName))
                .findFirst();
        if (exact.isPresent()) {
            return exact.get().getId();
        }

        return providers.stream()
                .filter(provider -> {
                    String providerNameNormalized = normalize(provider.getName());
                    return providerNameNormalized.contains(targetName) || targetName.contains(providerNameNormalized);
                })
                .findFirst()
                .map(Provider::getId)
                .orElse(null);
    }

    private LocalTime parseTimeFromText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        LocalTime parsed = parseTimeSafe(text);
        if (parsed != null) {
            return parsed;
        }

        Matcher matcher = TIME_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        int hour;
        int minute = 0;
        try {
            hour = Integer.parseInt(matcher.group(1));
            if (StringUtils.hasText(matcher.group(2))) {
                minute = Integer.parseInt(matcher.group(2));
            }
        } catch (NumberFormatException ex) {
            return null;
        }

        String meridiem = matcher.group(3);
        if (StringUtils.hasText(meridiem)) {
            String meridiemNormalized = meridiem.toLowerCase(Locale.ROOT);
            if ("pm".equals(meridiemNormalized) && hour < 12) {
                hour += 12;
            }
            if ("am".equals(meridiemNormalized) && hour == 12) {
                hour = 0;
            }
        }

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }

        return LocalTime.of(hour, minute);
    }

    private Long findRecentProviderId(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message.getSenderRole() != MessageRole.USER || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            Long providerId = extractProviderId(message.getContent());
            if (providerId != null) {
                return providerId;
            }
        }
        return null;
    }

    private String findRecentProviderName(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message.getSenderRole() != MessageRole.USER || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String providerName = extractProviderName(message.getContent());
            if (StringUtils.hasText(providerName)) {
                return providerName;
            }
        }
        return null;
    }

    private LocalDate findRecentRequestedDate(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message.getSenderRole() != MessageRole.USER || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            LocalDate date = parseDateFromText(message.getContent());
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private LocalTime findRecentRequestedTime(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message.getSenderRole() != MessageRole.USER || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            LocalTime time = parseTimeFromText(message.getContent());
            if (time != null) {
                return time;
            }
        }
        return null;
    }

    private String extractProblemDescription(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        String normalized = normalize(text);
        if (containsAny(normalized,
                "available", "availability", "time slot", "book", "booking", "provider",
                "متاح", "المواعيد", "الوقت", "احجز", "حجز", "مزود", "مين المزودين")) {
            return null;
        }

        return text.trim();
    }

    private String findRecentProblemDescription(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message.getSenderRole() != MessageRole.USER || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String problem = extractProblemDescription(message.getContent());
            if (StringUtils.hasText(problem)) {
                return problem;
            }
        }
        return null;
    }

    private Optional<ServiceType> resolveServiceType(AssistantPlan plan, String message) {
        if (plan == null) {
            return Optional.empty();
        }

        if (plan.getServiceTypeId() != null) {
            return serviceTypeRepository.findById(plan.getServiceTypeId());
        }

        List<ServiceType> serviceTypes = serviceTypeRepository.findAll();
        if (serviceTypes.isEmpty()) {
            return Optional.empty();
        }

        String query = firstText(
                firstText(plan.getServiceTypeName(), plan.getProblemDescription()),
                message);

        Optional<ServiceType> aiChosen = resolveServiceTypeWithAi(query, serviceTypes);
        if (aiChosen.isPresent()) {
            return aiChosen;
        }

        Set<String> queryTokens = tokenize(query);
        return serviceTypes.stream()
                .max(Comparator.comparingInt(serviceType -> scoreServiceType(serviceType, queryTokens)))
                .filter(serviceType -> scoreServiceType(serviceType, queryTokens) > 0);
    }

    private Optional<ServiceType> resolveServiceTypeWithAi(String query, List<ServiceType> serviceTypes) {
        if (!StringUtils.hasText(query) || serviceTypes == null || serviceTypes.isEmpty()) {
            return Optional.empty();
        }

        String serviceList = serviceTypes.stream()
                .map(serviceType -> "{id:" + serviceType.getId()
                        + ",name:'" + sanitizeForPrompt(serviceType.getName()) + "'"
                        + ",nameAr:'" + sanitizeForPrompt(serviceType.getNameAr()) + "'"
                        + ",description:'" + sanitizeForPrompt(serviceType.getDescription()) + "'}")
                .collect(Collectors.joining(","));

        String prompt = "Select the best matching service type ID for this user request. "
                + "Return JSON only in format {\"serviceTypeId\": number|null}. "
                + "If uncertain, return null. "
                + "User request: " + query + " "
                + "Available services: [" + serviceList + "]";

        String modelResponse = geminiClient.generateJson(prompt);
        if (!StringUtils.hasText(modelResponse)) {
            return Optional.empty();
        }

        try {
            String json = extractJson(modelResponse);
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }

            Long chosenId = objectMapper.readTree(json).path("serviceTypeId").isNumber()
                    ? objectMapper.readTree(json).path("serviceTypeId").asLong()
                    : null;

            if (chosenId == null) {
                return Optional.empty();
            }

            return serviceTypes.stream()
                    .filter(serviceType -> chosenId.equals(serviceType.getId()))
                    .findFirst();
        } catch (Exception ex) {
            log.debug("Failed to AI-resolve service type: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private List<ProviderSummaryResponse> searchProviders(ServiceType serviceType,
            String queryText,
            LocationDTO locationDTO,
            Integer radiusKm) {
        Set<String> queryTokens = tokenize(queryText);

        List<Provider> providers = providerRepository.findAll().stream()
                .filter(provider -> provider.getVerificationStatus() == null
                        || provider.getVerificationStatus().name().equals("VERIFIED"))
                .filter(provider -> serviceType == null
                        || (provider.getServiceType() != null
                                && serviceType.getId().equals(provider.getServiceType().getId())))
                .filter(provider -> matchesSearchText(provider, serviceType, queryTokens))
                .sorted(Comparator
                        .comparingInt((Provider provider) -> scoreProvider(provider, queryTokens)).reversed()
                        .thenComparing(provider -> provider.getAverageRating(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(DEFAULT_PROVIDER_LIMIT)
                .collect(Collectors.toList());

        return providers.stream()
                .map(provider -> toProviderSummary(provider, locationDTO, radiusKm))
                .collect(Collectors.toList());
    }

    private int scoreProvider(Provider provider, Set<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return 0;
        }

        String providerText = normalize(
                Optional.ofNullable(provider.getName()).orElse("") + " "
                        + Optional.ofNullable(provider.getBio()).orElse("") + " "
                        + Optional.ofNullable(provider.getServiceType()).map(ServiceType::getName).orElse("") + " "
                        + Optional.ofNullable(provider.getServiceType()).map(ServiceType::getNameAr).orElse(""));

        return (int) queryTokens.stream().filter(providerText::contains).count();
    }

    private int scoreServiceType(ServiceType serviceType, Set<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return 0;
        }

        String serviceText = normalize(
                Optional.ofNullable(serviceType.getName()).orElse("") + " "
                        + Optional.ofNullable(serviceType.getNameAr()).orElse("") + " "
                        + Optional.ofNullable(serviceType.getDescription()).orElse(""));

        int score = (int) queryTokens.stream().filter(serviceText::contains).count();
        if (score == 0) {
            return 0;
        }

        // Favor direct name matches over description-only matches.
        String nameText = normalize(
                Optional.ofNullable(serviceType.getName()).orElse("") + " "
                        + Optional.ofNullable(serviceType.getNameAr()).orElse(""));
        int nameScore = (int) queryTokens.stream().filter(nameText::contains).count();
        return score + (nameScore * 2);
    }

    private boolean matchesSearchText(Provider provider, ServiceType serviceType, Set<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return true;
        }
        int providerScore = scoreProvider(provider, queryTokens);
        int serviceScore = serviceType != null ? scoreServiceType(serviceType, queryTokens) : 0;
        return providerScore > 0 || serviceScore > 0;
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
        if (!StringUtils.hasText(normalized)) {
            return new HashSet<>();
        }

        return Arrays.stream(normalized.split("\\s+"))
                .filter(token -> token.length() >= 3)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String sanitizeForPrompt(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", " ").replace("\"", " ");
    }

    private String buildProviderSummaryMessage(List<ProviderSummaryResponse> providers, ServiceType serviceType) {
        String header = serviceType != null
                ? "I found " + providers.size() + " verified providers for " + serviceType.getName() + ":"
                : "I found these verified providers:";

        String details = providers.stream()
                .limit(3)
                .map(provider -> provider.getName()
                        + (provider.getAverageRating() != null ? " (" + provider.getAverageRating() + "/5)" : ""))
                .collect(Collectors.joining(", "));

        return details.isBlank() ? header : header + " " + details;
    }

    private String formatAvailabilityMessage(Long providerId, LocalDate date,
            List<ScheduleResponse.TimeSlotResponse> slots) {
        if (slots == null || slots.isEmpty()) {
            return "No available slots were found for provider " + providerId + " on " + DATE_FORMAT.format(date) + ".";
        }

        String slotSummary = slots.stream()
                .limit(5)
                .map(slot -> TIME_FORMAT.format(slot.getStartTime()) + " - " + TIME_FORMAT.format(slot.getEndTime()))
                .collect(Collectors.joining(", "));

        return "Available slots for provider " + providerId + " on " + DATE_FORMAT.format(date) + ": " + slotSummary;
    }

    private boolean isConsumer(User currentUser) {
        return currentUser != null && currentUser.getRole() == UserType.CONSUMER;
    }

    private String defaultReply(Intent intent) {
        return switch (intent) {
            case SEARCH_PROVIDERS -> "Tell me what service you need and I will suggest providers.";
            case GET_AVAILABILITY -> "Share the provider ID and the date, and I will check the available times.";
            case CREATE_BOOKING ->
                "I can create the booking once I have the provider, date, time, and issue description.";
            // case CREATE_EMERGENCY ->
            // "Share the service type and your live location so I can create the emergency
            // request.";
            case CLARIFICATION -> "I need a bit more information to continue.";
            case GENERAL -> "How can I help you today?";
        };
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private boolean containsAny(String normalizedText, String... keywords) {
        for (String keyword : keywords) {
            if (normalizedText.contains(normalize(keyword))) {
                return true;
            }
        }
        return false;
    }

    private String firstText(String primary, String secondary) {
        return StringUtils.hasText(primary) ? primary : secondary;
    }

    private <T> T firstNonNull(T primary, T secondary) {
        return primary != null ? primary : secondary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AssistantPlan {
        private Intent intent;
        private String reply;
        private String detectedLanguage;
        private Long providerId;
        private String providerName;
        private Long serviceTypeId;
        private String serviceTypeName;

        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate requestedDate;

        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm")
        private LocalTime requestedTime;

        private String problemDescription;
        private Integer searchRadiusKm;
        private LocationDTO location;
    }

    enum Intent {
        GENERAL,
        SEARCH_PROVIDERS,
        GET_AVAILABILITY,
        CREATE_BOOKING,
        // CREATE_EMERGENCY,
        CLARIFICATION
    }
}