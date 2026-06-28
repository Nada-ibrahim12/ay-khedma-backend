// package com.aykhedma.service;

// import com.aykhedma.dto.location.LocationDTO;
// import com.aykhedma.dto.request.AiChatRequest;
// import com.aykhedma.dto.request.BookingRequest;
// import com.aykhedma.dto.response.*;
// import com.aykhedma.exception.BadRequestException;
// import com.aykhedma.exception.ForbiddenException;
// import com.aykhedma.exception.ResourceNotFoundException;
// import com.aykhedma.model.booking.BookingStatus;
// import com.aykhedma.model.chat.*;
// import com.aykhedma.model.location.Location;
// import com.aykhedma.model.service.ServiceCategory;
// import com.aykhedma.model.service.ServiceType;
// import com.aykhedma.model.user.Consumer;
// import com.aykhedma.model.user.Provider;
// import com.aykhedma.model.user.User;
// import com.aykhedma.model.user.UserType;
// import com.aykhedma.model.user.VerificationStatus;
// import com.aykhedma.mapper.LocationMapper;
// import com.aykhedma.mapper.ProviderMapper;
// import com.aykhedma.mcp.server.McpServer;
// import com.aykhedma.repository.*;
// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import lombok.Data;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;
// import org.springframework.util.StringUtils;

// import java.io.IOException;
// import java.time.Duration;
// import java.time.Instant;
// import java.time.LocalDate;
// import java.time.LocalDateTime;
// import java.time.LocalTime;
// import java.time.format.DateTimeFormatter;
// import java.util.*;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.regex.Pattern;
// import java.util.stream.Collectors;

// @Service
// @Slf4j
// @RequiredArgsConstructor
// @Transactional
// public class AiAssistantServiceImpl implements AiAssistantService {

//     private static final int MAX_HISTORY_TURNS = 6;
//     private static final int MAX_SERVICE_CANDIDATES_FOR_AI = 8;
//     private static final int DEFAULT_SEARCH_RADIUS_KM = 10;
//     private static final int DEFAULT_PROVIDER_LIMIT = 5;
//     private static final int MAX_SERVICES_FOR_INLINE_CATALOG = 80;
//     private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
//     private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
//     private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");

//     private final GeminiClient geminiClient;
//     private final ProviderRepository providerRepository;
//     private final ProviderMapper providerMapper;
//     private final ProviderService providerService;
//     private final BookingService bookingService;
//     private final ServiceTypeRepository serviceTypeRepository;
//     private final ConsumerRepository consumerRepository;
//     private final LocationRepository locationRepository;
//     private final LocationMapper locationMapper;
//     private final LocationService locationService;
//     private final ObjectMapper objectMapper;
//     private final ChatSessionRepository chatSessionRepository;
//     private final ChatMessageRepository chatMessageRepository;
//     private final ServiceCategoryRepository categoryRepository;
//     private final SpeechToTextService speechToTextService;
//     private final TimeSlotRepository timeSlotRepository;

//     // MCP Configuration
//     @Value("${mcp.use-mcp:false}")
//     private boolean useMcp;

//     @Value("${mcp.enabled:true}")
//     private boolean mcpEnabled;

//     private final Optional<McpServer> mcpServer;

//     // ---- Lightweight in-memory caches to cut down on DB hits and repeated
//     // ---- Gemini calls for identical/near-identical lookups. These are simple
//     // ---- TTL caches; swap for Caffeine/Redis if you need multi-instance sharing.
//     private static final Duration CATALOG_CACHE_TTL = Duration.ofMinutes(5);
//     private static final Duration AI_LOOKUP_CACHE_TTL = Duration.ofMinutes(10);

//     private volatile CachedValue<List<ServiceType>> serviceTypesCache;
//     private volatile CachedValue<List<ServiceCategory>> categoriesCache;
//     private volatile CachedValue<String> serviceCatalogJsonCache;
//     private final Map<String, CachedValue<Long>> serviceTypeByMeaningCache = new ConcurrentHashMap<>();

//     @Override
//     public List<ChatResponse> getUserChats(User currentUser) {
//         if (currentUser == null) {
//             throw new ForbiddenException("User must be authenticated to view chat history");
//         }

//         List<ChatSession> sessions = chatSessionRepository.findByUserIdOrderByStartTimeDesc(currentUser.getId());

//         return sessions.stream()
//                 .map(session -> {
//                     List<ChatMessage> messages = chatMessageRepository
//                             .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId());

//                     String lastMessageContent = messages.isEmpty() ? ""
//                             : messages.get(messages.size() - 1).getContent();
//                     LocalDateTime lastMessageTime = messages.isEmpty() ? session.getStartTime()
//                             : messages.get(messages.size() - 1).getTimestamp();

//                     return ChatResponse.builder()
//                             .sessionId(session.getSessionId())
//                             .timestamp(lastMessageTime)
//                             .message(lastMessageContent)
//                             .detectedLanguage(session.getDetectedLanguage())
//                             .build();
//                 })
//                 .collect(Collectors.toList());
//     }

//     @Override
//     public ChatResponse getChat(String sessionId, User currentUser) {
//         ChatSession session = chatSessionRepository.findById(sessionId)
//                 .orElseThrow(() -> new BadRequestException("Chat session not found"));

//         if (!Objects.equals(session.getUserId(), currentUser != null ? currentUser.getId() : null)) {
//             throw new ForbiddenException("You are not allowed to access this chat session");
//         }

//         List<ChatMessage> messageEntities = chatMessageRepository
//                 .findByChatSessionSessionIdOrderByTimestampAsc(sessionId);

//         List<ChatMessageResponse> messages = messageEntities
//                 .stream()
//                 .map(message -> ChatMessageResponse.builder()
//                         .id(message.getId())
//                         .roomId(sessionId)
//                         .senderId(message.getSenderId())
//                         .senderName(message.getSenderRole() == MessageRole.ASSISTANT ? "Assistant" : "User")
//                         .senderRole(message.getSenderRole())
//                         .content(message.getContent())
//                         .type(message.getType())
//                         .mediaUrls(message.getMediaUrls())
//                         .timestamp(message.getTimestamp())
//                         .isRead(Boolean.TRUE.equals(message.getIsRead()))
//                         .build())
//                 .collect(Collectors.toList());

//         ChatMessage lastAssistantMessage = messageEntities.stream()
//                 .filter(ChatMessage::isAssistantMessage)
//                 .reduce((first, second) -> second)
//                 .orElse(null);

//         ChatResponseType lastResponseType = lastAssistantMessage != null
//                 && lastAssistantMessage.getResponseType() != null
//                         ? lastAssistantMessage.getResponseType()
//                         : ChatResponseType.TEXT;

//         List<ProviderSummaryResponse> providers = lastAssistantMessage != null
//                 ? parseProvidersPayload(lastAssistantMessage.getProvidersPayload())
//                 : List.of();

//         List<ScheduleResponse.TimeSlotResponse> availableSlots = lastAssistantMessage != null
//                 ? parseAvailableSlotsPayload(lastAssistantMessage.getAvailableSlotsPayload())
//                 : List.of();

//         String lastMessage = lastAssistantMessage != null && StringUtils.hasText(lastAssistantMessage.getContent())
//                 ? lastAssistantMessage.getContent()
//                 : (session.getLastMessage() != null ? session.getLastMessage().getContent() : "");

//         return ChatResponse.builder()
//                 .sessionId(session.getSessionId())
//                 .messages(messages)
//                 .timestamp(LocalDateTime.now())
//                 .message(lastMessage)
//                 .detectedLanguage(
//                         StringUtils.hasText(session.getDetectedLanguage()) ? session.getDetectedLanguage() : "en")
//                 .responseType(lastResponseType)
//                 .providers(providers)
//                 .availableTimeSlots(availableSlots)
//                 .build();
//     }

//     // =======================================================================================================

//     public ChatResponse chat(AiChatRequest request, User currentUser) {
//         // If MCP is enabled and we have the server, use MCP
//         if (mcpEnabled && useMcp && mcpServer.isPresent()) {
//             log.info("Using MCP implementation for chat");
//             return chatWithMcp(request, currentUser);
//         }

//         log.info("Using existing implementation for chat");
//         return chatWithExisting(request, currentUser);
//     }

//     // =======================================================================================================
//     /**
//      * MCP-based chat implementation with AI Tool Calling
//      */
//     private ChatResponse chatWithMcp(AiChatRequest request, User currentUser) {

//         String userMessage = request.getMessage();
//         boolean isVoiceNote = request.getVoiceNote() != null && !request.getVoiceNote().isEmpty();

//         if (isVoiceNote) {
//             try {
//                 String transcribedText = speechToTextService.transcribeAudio(request.getVoiceNote());
//                 if (StringUtils.hasText(transcribedText)) {
//                     userMessage = transcribedText;
//                     request.setMessage(userMessage);
//                     log.info("Voice transcribed for MCP: {}", userMessage);
//                 } else {
//                     // Transcription failed – return clarification
//                     Long userId = currentUser != null ? currentUser.getId() : null;
//                     ChatSession session = resolveSession(request, userId);
//                     saveUserMessage(session, userId != null ? userId : 0L, "[Voice note - transcription failed]");
//                     saveAssistantMessage(session, ChatResponse.builder()
//                             .sessionId(session.getSessionId())
//                             .timestamp(LocalDateTime.now())
//                             .message("عذراً، لم أتمكن من تحويل الرسالة الصوتية. ممكن تعيد تسجيلها أو تكتبها نصياً؟")
//                             .responseType(ChatResponseType.CLARIFICATION)
//                             .detectedLanguage("ar")
//                             .build());
//                     return ChatResponse.builder()
//                             .sessionId(session.getSessionId())
//                             .timestamp(LocalDateTime.now())
//                             .message("عذراً، لم أتمكن من تحويل الرسالة الصوتية. ممكن تعيد تسجيلها أو تكتبها نصياً؟")
//                             .responseType(ChatResponseType.CLARIFICATION)
//                             .detectedLanguage("ar")
//                             .build();
//                 }
//             } catch (IOException e) {
//                 log.error("Voice transcription error in MCP", e);
//                 // Fallback: use original message if any, else return error
//                 if (!StringUtils.hasText(userMessage)) {
//                     ChatSession session = resolveSession(request, currentUser != null ? currentUser.getId() : null);
//                     return ChatResponse.builder()
//                             .sessionId(session.getSessionId())
//                             .timestamp(LocalDateTime.now())
//                             .message("عذراً، حدث خطأ أثناء معالجة الصوت. حاول مرة أخرى.")
//                             .responseType(ChatResponseType.ERROR)
//                             .detectedLanguage("ar")
//                             .build();
//                 }
//             }
//         }

//         if (!StringUtils.hasText(userMessage)) {
//             throw new BadRequestException("Message or voice note is required");
//         }

//         log.info("Processing chat with MCP - message: {}", request.getMessage());

//         try {

//             Long userId = currentUser != null ? currentUser.getId() : null;
//             ChatSession session = resolveSession(request, userId);

//             List<ChatMessage> recentHistory = getRecentHistory(
//                     chatMessageRepository.findByChatSessionSessionIdOrderByTimestampAsc(request.getSessionId()),
//                     MAX_HISTORY_TURNS);

//             // Step 1: Get tool schemas from MCP server
//             List<Map<String, Object>> toolSchemas = mcpServer.get().getToolSchemas();
//             log.info("Available MCP tools: {}", toolSchemas.size());

//             // Step 2: Gemini decides which tool to call
//             McpToolCallResponse toolResponse = getMcpToolCallResponse(request, currentUser, recentHistory, toolSchemas);

//             if (toolResponse == null) {
//                 log.warn("No tool response from Gemini, falling back to existing implementation");
//                 return chatWithExisting(request, currentUser);
//             }

//             log.info("Gemini selected tool: {} with args: {}", toolResponse.tool, toolResponse.arguments);

//             // Step 3: Check if clarification needed
//             if (toolResponse.needsClarification) {
//                 String reply = toolResponse.reply != null ? toolResponse.reply
//                         : "محتاج هذه المعلومات لإتمام طلبك: " + String.join(", ", toolResponse.missingFields);

//                 saveUserMessage(session, userId != null ? userId : 0L, request.getMessage());

//                 ChatResponse clarificationResponse = ChatResponse.builder()
//                         .sessionId(request.getSessionId())
//                         .message(reply)
//                         .timestamp(LocalDateTime.now())
//                         .detectedLanguage(detectLanguage(request.getMessage()))
//                         .responseType(ChatResponseType.CLARIFICATION)
//                         .build();
//                 saveAssistantMessage(session, clarificationResponse);

//                 return clarificationResponse;
//             }

//             // Step 4: Execute the tool via MCP
//             String toolName = toolResponse.tool;
//             if (!StringUtils.hasText(toolName)) {
//                 log.warn("No tool name in response");
//                 return chatWithExisting(request, currentUser);
//             }

//             // Step 5: Build and execute MCP request
//             Map<String, Object> arguments = toolResponse.arguments != null ? toolResponse.arguments : new HashMap<>();

//             if ("search_providers".equals(toolName)) {
//                 double userLatitude = 0.0;
//                 double userLongitude = 0.0;

//                 if (currentUser != null && currentUser.getRole() == UserType.CONSUMER) {
//                     if (currentUser instanceof Consumer) {
//                         Consumer consumer = (Consumer) currentUser;
//                         if (consumer.getLocation() != null) {
//                             userLatitude = consumer.getLocation().getLatitude();
//                             userLongitude = consumer.getLocation().getLongitude();
//                             log.info("Using REAL consumer location: {}, {}", userLatitude, userLongitude);
//                         }
//                     }
//                 }

//                 arguments.put("latitude", userLatitude);
//                 arguments.put("longitude", userLongitude);
//                 log.info("Overrode location to REAL user coordinates: {}, {}", userLatitude, userLongitude);
//             }

//             if ("create_booking".equals(toolName)) {
//                 Long consumerId = null;
//                 if (currentUser != null && currentUser.getRole() == UserType.CONSUMER) {
//                     consumerId = currentUser.getId();
//                     log.info("Injecting consumer ID for booking: {}", consumerId);
//                 }
//                 if (consumerId != null) {
//                     arguments.put("consumerId", consumerId);
//                 } else {
//                     log.warn("No consumer ID available for booking");
//                     ChatResponse errorResponse = ChatResponse.builder()
//                             .sessionId(request.getSessionId())
//                             .message("يجب تسجيل الدخول كمستهلك لحجز خدمة. يرجى تسجيل الدخول والمحاولة مرة أخرى.")
//                             .timestamp(LocalDateTime.now())
//                             .detectedLanguage("ar")
//                             .responseType(ChatResponseType.ERROR)
//                             .build();
//                     saveUserMessage(session, userId != null ? userId : 0L, request.getMessage());
//                     saveAssistantMessage(session, errorResponse);
//                     return errorResponse;
//                 }
//             }
//             Map<String, Object> mcpRequest = buildMcpRequest(toolName, arguments);

//             log.info("MCP Request: {}", mcpRequest);

//             Map<String, Object> mcpResponse = mcpServer.get().handleRequest(mcpRequest);
//             log.info("MCP Response: {}", mcpResponse);

//             // Step 6: Build response based on tool
//             ChatResponse chatResponse = buildResponseFromMcpResult(toolName, mcpResponse, request, toolResponse.reply);

//             saveUserMessage(session, userId != null ? userId : 0L, request.getMessage());
//             saveAssistantMessage(session, chatResponse);

//             return chatResponse;
//         } catch (Exception e) {
//             log.error("MCP chat failed: {}", e.getMessage(), e);
//             return chatWithExisting(request, currentUser);
//         }
//     }

//     private McpToolCallResponse getMcpToolCallResponse(AiChatRequest request, User currentUser,
//             List<ChatMessage> history, List<Map<String, Object>> toolSchemas) {
//         if (!geminiClient.isEnabled()) {
//             return null;
//         }

//         String systemPrompt = buildMcpSystemPrompt(currentUser, toolSchemas);
//         String conversationContext = buildConversationContext(history);

//         String userMessage = conversationContext + "\n\nCurrent User Request: " + request.getMessage();

//         List<GeminiClient.ConversationTurn> turns = toConversationTurns(history);
//         turns.add(new GeminiClient.ConversationTurn("user", userMessage));

//         String modelResponse = geminiClient.generateJson(turns, systemPrompt);
//         return parseMcpToolCallResponse(modelResponse);
//     }

//     /**
//      * Build system prompt for MCP tool calling
//      */
//     private String buildMcpSystemPrompt(User currentUser, List<Map<String, Object>> tools) {
//         String userRole = (currentUser != null && currentUser.getRole() != null)
//                 ? currentUser.getRole().name()
//                 : "anonymous";

//         double userLatitude = 0.0;
//         double userLongitude = 0.0;
//         String userLocationInfo = "";

//         if (currentUser != null) {
//             if (currentUser.getRole() == UserType.CONSUMER) {
//                 if (currentUser instanceof Consumer) {
//                     Consumer consumer = (Consumer) currentUser;
//                     if (consumer.getLocation() != null) {
//                         userLatitude = consumer.getLocation().getLatitude();
//                         userLongitude = consumer.getLocation().getLongitude();
//                         userLocationInfo = String.format("User location (from consumer): (%.4f, %.4f)", userLatitude,
//                                 userLongitude);
//                         log.info("Using consumer location: {}, {}", userLatitude, userLongitude);
//                     }
//                 }
//             } else {
//                 log.info("User is not a consumer");
//             }
//         }

//         // Build tools description
//         StringBuilder toolsDesc = new StringBuilder();
//         toolsDesc.append("## AVAILABLE TOOLS:\n\n");
//         for (Map<String, Object> tool : tools) {
//             String name = (String) tool.get("name");
//             String description = (String) tool.get("description");
//             @SuppressWarnings("unchecked")
//             Map<String, Object> inputSchema = (Map<String, Object>) tool.get("inputSchema");
//             @SuppressWarnings("unchecked")
//             Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");

//             toolsDesc.append("### Tool: ").append(name).append("\n");
//             toolsDesc.append("Description: ").append(description).append("\n");
//             toolsDesc.append("Parameters:\n");
//             if (properties != null) {
//                 List<String> required = (List<String>) inputSchema.get("required");
//                 for (Map.Entry<String, Object> entry : properties.entrySet()) {
//                     @SuppressWarnings("unchecked")
//                     Map<String, Object> prop = (Map<String, Object>) entry.getValue();
//                     boolean isRequired = required != null && required.contains(entry.getKey());
//                     toolsDesc.append("  - ").append(entry.getKey())
//                             .append(" (").append(prop.get("type")).append(")")
//                             .append(isRequired ? " [REQUIRED]" : " [OPTIONAL]")
//                             .append(": ").append(prop.get("description")).append("\n");
//                 }
//             }
//             toolsDesc.append("\n");
//         }

//         List<ServiceType> allServices = getCachedServiceTypes();
//         List<ServiceCategory> allCategories = getCachedCategories();

//         String categoriesStr = allCategories.stream()
//                 .map(cat -> "- Category: " + cat.getName() + " (ID: " + cat.getId() + " description: "
//                         + cat.getDescription() + ")")
//                 .collect(Collectors.joining("\n"));

//         String servicesStr = allServices.stream()
//                 .map(st -> "- Service Type: " + st.getName() + " (ID: " + st.getId() + ", Category ID: "
//                         + (st.getCategory() != null ? st.getCategory().getId() : "null") + ", description: "
//                         + st.getDescription() + ")")
//                 .collect(Collectors.joining("\n"));

//         return """
//                 You are Ay Khedma AI Assistant - a comprehensive service marketplace assistant.

//                  ## USER LOCATION INFORMATION:
//                 %s
//                 - Latitude: %.4f
//                 - Longitude: %.4f
//                 - ALWAYS use these coordinates when calling search_providers
//                 - Do NOT ask the user for their location - you already have it!

//                 ## YOUR TASK:
//                 Analyze the user's message and decide which tool to call.

//                 """
//                 + toolsDesc.toString()
//                 + """

//                         - Today Date is %s

//                         ## DATE AND TIME HANDLING

//                         ### Input Formats
//                         - **Dates**: "30/6" or "30/6/2026" → June 30, 2026 | "15-6" → June 15 | "يوم 30" → 30th of current month
//                         - **Days**: "الجمعة", "السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس" → Next [day]
//                         - **Relative**: "بعد اسبوع" → +7 days | "بعد 3 ايام" → +3 days
//                         - **Times**: "7" or "7 صباحاً" → 07:00 | "7 مساءً" → 19:00 | "الظهر" → 12:00 | "العصر" → 16:00 | "المغرب" → 18:00 | "العشاء" → 20:00

//                         ### Day Rules (CRITICAL)
//                         - **"الجمعة"/Friday** → FIRST Friday AFTER today (if today IS Friday → next Friday, +7 days)
//                         - **Other days** → NEXT occurrence of that day
//                         - **"الجمعة الجاي"** → Friday of next week (not this coming Friday)

//                         ### Day Mapping
//                         - الأحد → Sunday | الاثنين → Monday | الثلاثاء → Tuesday | الأربعاء → Wednesday | الخميس → Thursday | الجمعة → Friday | السبت → Saturday

//                         ### Date Parsing Rules
//                         1. **Day+Month only** (e.g., "30/6"): Use current year (2026); if date passed → ADD 1 YEAR
//                         2. **Day only** (e.g., "يوم 30"): Use current month; if day passed → ADD 1 MONTH
//                         3. **Relative**: "بعد X يوم/اسبوع" → Today + X days
//                         4. **Output**: Always `"yyyy-MM-dd"` (e.g., "2026-06-30")

//                         ### Time Parsing Rules
//                         1. **Number 1-12** → AM (morning) | **13-24** → 24-hour format
//                         2. **Arabic references**: "صباحاً/صبح" → AM | "مساءً/مغرب" → PM (+12) | "ظهراً" → 12:00 | "العصر" → 16:00 | "المغرب" → 18:00 | "العشاء" → 20:00
//                         3. **Output**: Always `"HH:mm"` (e.g., "07:00", "19:00")

//                         ### Examples
//                         1. User: "الجمعة الساعة 10" (Today Monday) → date="2026-07-03", time="10:00"
//                         2. User: "السبت الجاي الساعة 4 العصر" (Today Monday) → date="2026-07-04", time="16:00"
//                         3. User: "يوم 30/6 الساعة 7" (Today June 28) → date="2026-06-30", time="07:00"
//                         4. User: "يوم 15/6 الساعة 8" (Today June 28, past) → date="2027-06-15", time="08:00"

//                                 you need to analyze service categories and service types from the database
//                                 """
//                 + categoriesStr
//                 + "\n\n"
//                 + servicesStr
//                 + """

//                         ## INSTRUCTIONS:
//                         1. Analyze user's problem, there is no unauthenticated user can access the chatbot
//                         2. Choose the most appropriate tool based on the user's request
//                         3. Extract all required parameters from the user's message
//                         4. Choose the most appropriate services can solve the user's issue
//                         5. If required parameters are missing, set needsClarification=true and list missing fields
//                         6. For dates, always use format "yyyy-MM-dd"
//                         7. For times, always use format "HH:mm" (24-hour format)
//                         8. For search_providers, ALWAYS include the user's latitude and longitude from above

//                         ## OUTPUT FORMAT:
//                         Return ONLY valid JSON. No extra text, no explanation, no markdown.

//                         {
//                           "tool": "tool_name",
//                           "arguments": {
//                             "param1": "value1",
//                             "param2": "value2"
//                           },
//                           "needsClarification": false,
//                           "missingFields": ["field1", "field2"] or null,
//                           "reply": "natural language response to user"
//                         }

//                         ## TOOL USAGE RULES:

//                         ### search_providers
//                         - Use when user wants to FIND or SEARCH for providers
//                         - Extract serviceTypes from understanding of user message (e.g., "electrician", "plumbing", "AC Repair")
//                         - Use consumer location to calculate distance between them
//                         - IMPORTANT: serviceTypes must be a JSON ARRAY (list), not a string!
//                         - Example: "محتاج فني تكييف" → tool="search_providers", serviceTypes=["AC Repair", "HVAC", "Air Conditioner Maintenance"], latitude=%.4f, longitude=%.4f

//                         ### check_availability
//                         - Use when user wants to SEE available time slots for a SPECIFIC provider
//                         - Must have providerId or providerName
//                         - Date is optional - if not provided, show next 7 days
//                         - Example: "وريني مواعيده / وريني مواعيد احمد ابراهيم" → tool="check_availability", providerId=20

//                         ### create_booking
//                         - Use when user wants to BOOK or RESERVE an appointment
//                         - Required: providerId, date, time, problemDescription
//                         - If any required field is missing, set needsClarification=true
//                         - Apply the date/time/day-of-week parsing rules above
//                         - Example: "احجز معاه/ مع احمد الساعة 4 يوم السبت" → tool="create_booking"

//                         ### get_provider_details
//                         - Use when user wants DETAILED information about a provider
//                         - Must have providerId or providerName
//                         - Example: "عايز معلومات عنه/عن ياسر عبده" → tool="get_provider_details"

//                         ## CONTEXT:
//                         Current user role: """
//                 + userRole
//                 + """

//                         - If role = "anonymous" or role = "null" or role = "ANONYMOUS" , user is NOT logged in
//                         - The user must be authenticated to access any tool

//                         ## REMEMBER:
//                         - Return ONLY valid JSON
//                         - Dates: ALWAYS use "yyyy-MM-dd" format
//                         - Times: ALWAYS use "HH:mm" 24-hour format
//                         - If a date is in the past, add 1 year
//                         - Days of the week = next occurrence of that day
//                         - No explanation outside JSON
//                         - No markdown formatting around JSON
//                         - Always use double quotes for JSON properties
//                         - For Arabic user messages, respond in Arabic in the reply field
//                         - For English user messages, respond in English
//                         """.formatted(userLocationInfo, userLatitude, userLongitude,
//                         LocalDate.now().toString(), userLatitude, userLongitude);
//     }

//     /**
//      * Parse MCP tool call response from Gemini
//      */
//     private McpToolCallResponse parseMcpToolCallResponse(String modelResponse) {
//         if (!StringUtils.hasText(modelResponse)) {
//             return null;
//         }

//         try {
//             String json = extractJson(modelResponse);
//             if (!StringUtils.hasText(json)) {
//                 return null;
//             }

//             JsonNode node = objectMapper.readTree(json);

//             McpToolCallResponse response = new McpToolCallResponse();
//             response.tool = node.path("tool").asText(null);
//             response.needsClarification = node.path("needsClarification").asBoolean(false);
//             response.reply = node.path("reply").asText(null);

//             // Parse arguments
//             JsonNode argsNode = node.path("arguments");
//             if (argsNode.isObject()) {
//                 response.arguments = new HashMap<>();
//                 argsNode.fields().forEachRemaining(entry -> {
//                     JsonNode value = entry.getValue();
//                     String key = entry.getKey();
//                     if ("serviceTypes".equals(key)) {
//                         try {
//                             if (value.isArray()) {
//                                 List<String> serviceList = new ArrayList<>();
//                                 for (JsonNode item : value) {
//                                     serviceList.add(item.asText());
//                                 }
//                                 response.arguments.put(key, serviceList);
//                             } else if (value.isTextual()) {
//                                 String strValue = value.asText().trim();
//                                 if (strValue.startsWith("[") && strValue.endsWith("]")) {
//                                     try {
//                                         JsonNode parsedArray = objectMapper.readTree(strValue);
//                                         if (parsedArray.isArray()) {
//                                             List<String> serviceList = new ArrayList<>();
//                                             for (JsonNode item : parsedArray) {
//                                                 serviceList.add(item.asText());
//                                             }
//                                             response.arguments.put(key, serviceList);
//                                             log.info("Parsed serviceTypes from string to list: {}", serviceList);
//                                             return;
//                                         }
//                                     } catch (Exception e) {
//                                         log.warn("Failed to parse serviceTypes as JSON array: {}", e.getMessage());
//                                     }
//                                 }
//                                 response.arguments.put(key, List.of(strValue));
//                             } else {
//                                 response.arguments.put(key, value.toString());
//                             }
//                         } catch (Exception e) {
//                             log.warn("Error processing serviceTypes: {}", e.getMessage());
//                             response.arguments.put(key, value.toString());
//                         }
//                     } else {
//                         if (value.isTextual()) {
//                             response.arguments.put(key, value.asText());
//                         } else if (value.isNumber()) {
//                             response.arguments.put(key, value.asDouble());
//                         } else if (value.isBoolean()) {
//                             response.arguments.put(key, value.asBoolean());
//                         } else {
//                             response.arguments.put(key, value.toString());
//                         }
//                     }
//                 });
//             }

//             // Parse missing fields
//             if (node.has("missingFields") && node.path("missingFields").isArray()) {
//                 response.missingFields = new ArrayList<>();
//                 for (JsonNode field : node.path("missingFields")) {
//                     response.missingFields.add(field.asText());
//                 }
//             }

//             return response;

//         } catch (Exception ex) {
//             log.debug("Failed to parse MCP tool call response: {}", ex.getMessage());
//             return null;
//         }
//     }

//     /**
//      * Build response from MCP result
//      */
//     private ChatResponse buildResponseFromMcpResult(String toolName, Map<String, Object> mcpResponse,
//             AiChatRequest request, String defaultReply) {

//         String reply = defaultReply != null ? defaultReply : "تم تنفيذ طلبك بنجاح.";

//         return switch (toolName) {
//             case "search_providers" -> handleSearchProvidersResponse(mcpResponse, request, reply);
//             case "check_availability" -> handleCheckAvailabilityResponse(mcpResponse, request);
//             case "create_booking" -> handleCreateBookingResponse(mcpResponse, request);
//             case "get_provider_details" -> handleGetProviderDetailsResponse(mcpResponse, request);
//             default -> ChatResponse.builder()
//                     .sessionId(request.getSessionId())
//                     .message(reply)
//                     .timestamp(LocalDateTime.now())
//                     .detectedLanguage(detectLanguage(request.getMessage()))
//                     .responseType(ChatResponseType.TEXT)
//                     .build();
//         };
//     }

//     /**
//      * Handle search providers MCP response
//      */
//     private ChatResponse handleSearchProvidersResponse(Map<String, Object> mcpResponse,
//             AiChatRequest request, String defaultReply) {
//         List<ProviderSummaryResponse> providers = parseMcpResponse(mcpResponse);
//         String reply = defaultReply;

//         if (providers.isEmpty()) {
//             reply = "عذراً، لم أجد أي مقدمي خدمة متاحين في منطقتك حالياً. جرب توسيع نطاق البحث أو حاول مرة أخرى لاحقاً.";
//         } else {
//             // Get service type from first provider
//             ServiceType serviceType = null;
//             if (providers.get(0).getServiceType() != null) {
//                 serviceType = getCachedServiceTypes().stream()
//                         .filter(st -> st.getName().equalsIgnoreCase(providers.get(0).getServiceType()))
//                         .findFirst()
//                         .orElse(null);
//             }
//             if (serviceType == null && providers.get(0).getServiceTypeAr() != null) {
//                 serviceType = getCachedServiceTypes().stream()
//                         .filter(st -> st.getNameAr().equalsIgnoreCase(providers.get(0).getServiceTypeAr()))
//                         .findFirst()
//                         .orElse(null);
//             }
//             reply = buildSmartReply(providers, serviceType, request.getMessage());
//         }

//         return ChatResponse.builder()
//                 .sessionId(request.getSessionId())
//                 .message(reply)
//                 .timestamp(LocalDateTime.now())
//                 .detectedLanguage(detectLanguage(request.getMessage()))
//                 .responseType(ChatResponseType.PROVIDER_LIST)
//                 .providers(providers)
//                 .build();
//     }

//     /**
//      * Handle check availability MCP response
//      */
//     private ChatResponse handleCheckAvailabilityResponse(Map<String, Object> mcpResponse,
//             AiChatRequest request) {
//         Map<String, Object> content = parseMcpContent(mcpResponse);
//         if (content == null || !Boolean.TRUE.equals(content.get("success"))) {
//             String error = content != null ? (String) content.get("error") : "Unknown error";
//             return ChatResponse.builder()
//                     .sessionId(request.getSessionId())
//                     .message("حدث خطأ أثناء التحقق من المواعيد: " + error)
//                     .timestamp(LocalDateTime.now())
//                     .detectedLanguage(detectLanguage(request.getMessage()))
//                     .responseType(ChatResponseType.ERROR)
//                     .build();
//         }

//         @SuppressWarnings("unchecked")
//         List<Map<String, Object>> slots = (List<Map<String, Object>>) content.get("slots");
//         String providerName = (String) content.get("providerName");

//         List<ScheduleResponse.TimeSlotResponse> timeSlots = new ArrayList<>();
//         if (slots != null) {
//             for (Map<String, Object> slot : slots) {
//                 ScheduleResponse.TimeSlotResponse timeSlot = new ScheduleResponse.TimeSlotResponse();
//                 timeSlot.setDate((String) slot.get("date"));
//                 try {
//                     timeSlot.setStartTime(LocalTime.parse((String) slot.get("startTime")));
//                     timeSlot.setEndTime(LocalTime.parse((String) slot.get("endTime")));
//                 } catch (Exception e) {
//                     log.warn("Failed to parse time: {}", e.getMessage());
//                 }
//                 timeSlots.add(timeSlot);
//             }
//         }

//         String reply = timeSlots.isEmpty() ? "لا توجد مواعيد متاحة لـ " + providerName + " في الفترة المطلوبة."
//                 : formatAvailabilityMessageForMCP(providerName, timeSlots);

//         return ChatResponse.builder()
//                 .sessionId(request.getSessionId())
//                 .message(reply)
//                 .timestamp(LocalDateTime.now())
//                 .detectedLanguage(detectLanguage(request.getMessage()))
//                 .responseType(ChatResponseType.AVAILABLE_SLOTS)
//                 .availableTimeSlots(timeSlots)
//                 .build();
//     }

//     /**
//      * Handle create booking MCP response
//      */
//     private ChatResponse handleCreateBookingResponse(Map<String, Object> mcpResponse,
//             AiChatRequest request) {
//         Map<String, Object> content = parseMcpContent(mcpResponse);

//         if (content == null || !Boolean.TRUE.equals(content.get("success"))) {
//             String error = content != null ? (String) content.get("error") : "Unknown error";

//             // Check for available slots suggestions
//             @SuppressWarnings("unchecked")
//             List<Map<String, Object>> availableSlots = (List<Map<String, Object>>) content.get("availableSlots");
//             if (availableSlots != null && !availableSlots.isEmpty()) {
//                 List<ScheduleResponse.TimeSlotResponse> slots = new ArrayList<>();
//                 for (Map<String, Object> slot : availableSlots) {
//                     try {
//                         ScheduleResponse.TimeSlotResponse timeSlot = new ScheduleResponse.TimeSlotResponse();
//                         timeSlot.setStartTime(LocalTime.parse((String) slot.get("time")));
//                         timeSlot.setEndTime(LocalTime.parse((String) slot.get("endTime")));
//                         slots.add(timeSlot);
//                     } catch (Exception e) {
//                         log.warn("Failed to parse time: {}", e.getMessage());
//                     }
//                 }
//                 return ChatResponse.builder()
//                         .sessionId(request.getSessionId())
//                         .message("الموعد المطلوب غير متاح. المواعيد المتاحة:")
//                         .timestamp(LocalDateTime.now())
//                         .detectedLanguage(detectLanguage(request.getMessage()))
//                         .responseType(ChatResponseType.AVAILABLE_SLOTS)
//                         .availableTimeSlots(slots)
//                         .build();
//             }

//             return ChatResponse.builder()
//                     .sessionId(request.getSessionId())
//                     .message("فشل إنشاء الحجز: " + error)
//                     .timestamp(LocalDateTime.now())
//                     .detectedLanguage(detectLanguage(request.getMessage()))
//                     .responseType(ChatResponseType.ERROR)
//                     .build();
//         }

//         Long bookingId = content.get("bookingId") != null ? Long.parseLong(content.get("bookingId").toString()) : null;
//         String status = (String) content.get("status");

//         return ChatResponse.builder()
//                 .sessionId(request.getSessionId())
//                 .message("تم إنشاء طلب الحجز بنجاح رقم #" + bookingId)
//                 .timestamp(LocalDateTime.now())
//                 .detectedLanguage(detectLanguage(request.getMessage()))
//                 .responseType(ChatResponseType.BOOKING_CREATED)
//                 .booking(BookingResponse.builder()
//                         .id(bookingId)
//                         .status(BookingStatus.valueOf(status != null ? status : "PENDING"))
//                         .build())
//                 .build();
//     }

//     /**
//      * Handle get provider details MCP response
//      */
//     private ChatResponse handleGetProviderDetailsResponse(Map<String, Object> mcpResponse,
//             AiChatRequest request) {
//         Map<String, Object> content = parseMcpContent(mcpResponse);

//         if (content == null || !Boolean.TRUE.equals(content.get("success"))) {
//             String error = content != null ? (String) content.get("error") : "Unknown error";
//             return ChatResponse.builder()
//                     .sessionId(request.getSessionId())
//                     .message("فشل جلب معلومات المزود: " + error)
//                     .timestamp(LocalDateTime.now())
//                     .detectedLanguage(detectLanguage(request.getMessage()))
//                     .responseType(ChatResponseType.ERROR)
//                     .build();
//         }

//         // Extract provider details
//         String name = (String) content.get("name");
//         String email = (String) content.get("email");
//         String phoneNumber = (String) content.get("phoneNumber");
//         String bio = (String) content.get("bio");
//         Double avgRating = content.get("averageRating") != null ? ((Number) content.get("averageRating")).doubleValue()
//                 : 0.0;
//         Integer totalReviews = (Integer) content.get("totalReviews");
//         Integer yearsExp = (Integer) content.get("yearsOfExperience");
//         String verificationStatus = (String) content.get("verificationStatus");
//         Boolean hasSchedule = (Boolean) content.get("hasSchedule");

//         @SuppressWarnings("unchecked")
//         Map<String, Object> serviceType = (Map<String, Object>) content.get("serviceType");
//         @SuppressWarnings("unchecked")
//         Map<String, Object> location = (Map<String, Object>) content.get("location");

//         // Build response message
//         StringBuilder message = new StringBuilder();
//         message.append("**معلومات المزود**\n\n");
//         message.append("**الاسم**: ").append(name).append("\n");

//         if (verificationStatus != null) {
//             String statusEmoji = "VERIFIED".equals(verificationStatus) ? "✅" : "⏳";
//             message.append("**الحالة**: ").append(statusEmoji).append(" ").append(verificationStatus).append("\n");
//         }

//         if (avgRating > 0) {
//             message.append("⭐ **التقييم**: ").append(String.format("%.1f", avgRating)).append("/5");
//             if (totalReviews != null && totalReviews > 0) {
//                 message.append(" (").append(totalReviews).append(" تقييم)");
//             }
//             message.append("\n");
//         }

//         if (yearsExp != null && yearsExp > 0) {
//             message.append("**سنوات الخبرة**: ").append(yearsExp).append("\n");
//         }

//         if (phoneNumber != null) {
//             message.append("**الهاتف**: ").append(phoneNumber).append("\n");
//         }

//         if (email != null) {
//             message.append("**البريد الإلكتروني**: ").append(email).append("\n");
//         }

//         if (bio != null && !bio.isEmpty()) {
//             message.append("\n**عن المزود**:\n").append(bio).append("\n");
//         }

//         if (serviceType != null) {
//             String serviceName = (String) serviceType.get("name");
//             String serviceNameAr = (String) serviceType.get("nameAr");
//             message.append("\n **الخدمة**: ").append(serviceNameAr != null ? serviceNameAr : serviceName)
//                     .append("\n");
//         }

//         if (location != null) {
//             String address = (String) location.get("address");
//             if (address != null && !address.isEmpty()) {
//                 message.append("**العنوان**: ").append(address).append("\n");
//             }
//         }

//         if (hasSchedule != null && hasSchedule) {
//             message.append("\n✅ متاح للحجز");
//         }

//         // Create provider summary
//         ProviderSummaryResponse providerSummary = ProviderSummaryResponse.builder()
//                 .id(content.get("id") != null ? ((Number) content.get("id")).longValue() : null)
//                 .name(name)
//                 .averageRating(avgRating)
//                 .distance(0.0)
//                 .build();

//         return ChatResponse.builder()
//                 .sessionId(request.getSessionId())
//                 .message(message.toString())
//                 .timestamp(LocalDateTime.now())
//                 .detectedLanguage(detectLanguage(request.getMessage()))
//                 .responseType(ChatResponseType.PROVIDER_DETAILS)
//                 .providers(List.of(providerSummary))
//                 .build();
//     }

//     /**
//      * Build MCP request for tool call
//      */
//     private Map<String, Object> buildMcpRequest(String toolName, Map<String, Object> arguments) {
//         Map<String, Object> mcpRequest = new HashMap<>();
//         mcpRequest.put("jsonrpc", "2.0");
//         mcpRequest.put("method", "tools/call");
//         mcpRequest.put("id", UUID.randomUUID().toString());

//         Map<String, Object> params = new HashMap<>();
//         params.put("name", toolName);
//         params.put("arguments", arguments);
//         mcpRequest.put("params", params);

//         return mcpRequest;
//     }

//     /**
//      * Parse MCP content from response
//      */
//     @SuppressWarnings("unchecked")
//     private Map<String, Object> parseMcpContent(Map<String, Object> mcpResponse) {
//         try {
//             Map<String, Object> result = (Map<String, Object>) mcpResponse.get("result");
//             if (result == null) {
//                 return null;
//             }

//             Boolean isError = (Boolean) result.get("isError");
//             if (isError != null && isError) {
//                 return null;
//             }

//             Object contentObj = result.get("content");
//             if (contentObj == null) {
//                 return null;
//             }

//             String text = null;
//             if (contentObj instanceof List) {
//                 List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
//                 if (!contentList.isEmpty()) {
//                     text = (String) contentList.get(0).get("text");
//                 }
//             } else if (contentObj instanceof Object[]) {
//                 Object[] contentArray = (Object[]) contentObj;
//                 if (contentArray.length > 0) {
//                     Map<String, Object> contentMap = (Map<String, Object>) contentArray[0];
//                     text = (String) contentMap.get("text");
//                 }
//             }

//             if (text == null || text.isEmpty()) {
//                 return null;
//             }

//             return objectMapper.readValue(text, Map.class);

//         } catch (Exception e) {
//             log.error("Failed to parse MCP content: {}", e.getMessage(), e);
//             return null;
//         }
//     }

//     /**
//      * Parse MCP response to extract providers
//      */
//     @SuppressWarnings("unchecked")
//     private List<ProviderSummaryResponse> parseMcpResponse(Map<String, Object> mcpResponse) {
//         try {
//             Map<String, Object> result = (Map<String, Object>) mcpResponse.get("result");
//             if (result == null) {
//                 log.warn("No 'result' field in MCP response");
//                 return List.of();
//             }

//             Boolean isError = (Boolean) result.get("isError");
//             if (isError != null && isError) {
//                 Object contentObj = result.get("content");
//                 String errorText = null;
//                 if (contentObj instanceof List) {
//                     List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
//                     if (!contentList.isEmpty()) {
//                         errorText = (String) contentList.get(0).get("text");
//                     }
//                 } else if (contentObj instanceof Object[]) {
//                     Object[] contentArray = (Object[]) contentObj;
//                     if (contentArray.length > 0) {
//                         Map<String, Object> contentMap = (Map<String, Object>) contentArray[0];
//                         errorText = (String) contentMap.get("text");
//                     }
//                 }
//                 if (errorText != null) {
//                     log.warn("MCP tool returned error: {}", errorText);
//                 }
//                 return List.of();
//             }

//             Object contentObj = result.get("content");
//             if (contentObj == null) {
//                 log.warn("No content in MCP response");
//                 return List.of();
//             }

//             String text = null;

//             if (contentObj instanceof List) {
//                 List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
//                 if (!contentList.isEmpty()) {
//                     Map<String, Object> firstContent = contentList.get(0);
//                     text = (String) firstContent.get("text");
//                 }
//             } else if (contentObj instanceof Object[]) {
//                 Object[] contentArray = (Object[]) contentObj;
//                 if (contentArray.length > 0) {
//                     Map<String, Object> firstContent = (Map<String, Object>) contentArray[0];
//                     text = (String) firstContent.get("text");
//                 }
//             }

//             if (text == null || text.isEmpty()) {
//                 log.warn("No text in content");
//                 return List.of();
//             }

//             log.info("MCP response text: {}", text);

//             if (text.equals("[]") || text.isEmpty()) {
//                 return List.of();
//             }

//             Map<String, Object> responseMap = objectMapper.readValue(text, Map.class);

//             Object providersObj = responseMap.get("providers");
//             if (providersObj == null) {
//                 log.warn("No providers array in response");
//                 return List.of();
//             }

//             String providersJson = objectMapper.writeValueAsString(providersObj);
//             List<ProviderSummaryResponse> providers = objectMapper.readValue(
//                     providersJson,
//                     objectMapper.getTypeFactory().constructCollectionType(List.class, ProviderSummaryResponse.class));

//             return providers != null ? providers : List.of();

//         } catch (Exception e) {
//             log.error("Failed to parse MCP response: {}", e.getMessage(), e);
//             return List.of();
//         }
//     }

//     /**
//      * Format availability message for MCP response
//      */
//     private String formatAvailabilityMessageForMCP(String providerName,
//             List<ScheduleResponse.TimeSlotResponse> slots) {
//         if (slots == null || slots.isEmpty()) {
//             return "لا توجد مواعيد متاحة لـ " + providerName;
//         }

//         Map<LocalDate, List<ScheduleResponse.TimeSlotResponse>> slotsByDate = slots.stream()
//                 .collect(Collectors.groupingBy(slot -> parseDateSafe(slot.getDate())));

//         StringBuilder message = new StringBuilder();
//         message.append("المواعيد المتاحة لـ ").append(providerName).append(":\n\n");

//         int dateCount = 0;
//         for (Map.Entry<LocalDate, List<ScheduleResponse.TimeSlotResponse>> entry : slotsByDate.entrySet()) {
//             if (dateCount++ >= 7)
//                 break;
//             message.append("📅 ").append(DATE_FORMAT.format(entry.getKey())).append(":\n");
//             String times = entry.getValue().stream()
//                     .map(slot -> TIME_FORMAT.format(slot.getStartTime()) + " - " +
//                             TIME_FORMAT.format(slot.getEndTime()))
//                     .collect(Collectors.joining(", "));
//             message.append("   🕐 ").append(times).append("\n");
//         }

//         return message.toString();
//     }

//     // =================================================================================================================

//     /**
//      * Existing chat implementation
//      */
//     private ChatResponse chatWithExisting(AiChatRequest request, User currentUser) {
//         String userMessage = request.getMessage();
//         boolean isVoiceNote = request.getVoiceNote() != null && !request.getVoiceNote().isEmpty();

//         if (isVoiceNote) {
//             log.info("Received voice note in chat request, starting transcription");

//             try {
//                 String transcribedText = speechToTextService.transcribeAudio(request.getVoiceNote());
//                 if (StringUtils.hasText(transcribedText)) {
//                     userMessage = transcribedText;
//                     log.info("Voice transcribed to: {}", userMessage);
//                 } else {
//                     Long userId = currentUser != null ? currentUser.getId() : null;
//                     ChatSession session = resolveSession(request, userId);
//                     saveUserMessage(session, userId != null ? userId : 0L, "[Voice note - transcription failed]");
//                     saveAssistantMessage(session, ChatResponse.builder()
//                             .sessionId(session.getSessionId())
//                             .timestamp(LocalDateTime.now())
//                             .message("عذراً، لم أتمكن من تحويل الرسالة الصوتية. ممكن تعيد تسجيلها أو تكتبها نصياً؟")
//                             .responseType(ChatResponseType.CLARIFICATION)
//                             .detectedLanguage("ar")
//                             .build());
//                     return ChatResponse.builder()
//                             .sessionId(session.getSessionId())
//                             .timestamp(LocalDateTime.now())
//                             .message("عذراً، لم أتمكن من تحويل الرسالة الصوتية. ممكن تعيد تسجيلها أو تكتبها نصياً؟")
//                             .responseType(ChatResponseType.CLARIFICATION)
//                             .detectedLanguage("ar")
//                             .build();
//                 }
//             } catch (IOException e) {
//                 log.error("Error occurred while transcribing voice note", e);
//                 userMessage = request.getMessage();
//             }
//         }

//         if (!StringUtils.hasText(userMessage)) {
//             throw new BadRequestException("Message or voice note is required");
//         }

//         Long userId = currentUser != null ? currentUser.getId() : null;
//         ChatSession session = resolveSession(request, userId);

//         String detectedLanguage = detectLanguage(userMessage);
//         if (!detectedLanguage.equals(session.getDetectedLanguage())) {
//             session.setDetectedLanguage(detectedLanguage);
//             chatSessionRepository.save(session);
//         }

//         // Load only recent history
//         List<ChatMessage> fullHistory = chatMessageRepository
//                 .findByChatSessionSessionIdOrderByTimestampAsc(session.getSessionId());
//         List<ChatMessage> recentHistory = getRecentHistory(fullHistory, MAX_HISTORY_TURNS);

//         String messageToStore = isVoiceNote ? "🎤 [Voice] " + userMessage : userMessage;
//         saveUserMessage(session, userId != null ? userId : 0L, messageToStore);

//         AiChatRequest effectiveRequest = AiChatRequest.builder()
//                 .sessionId(request.getSessionId())
//                 .message(userMessage)
//                 .providerId(request.getProviderId())
//                 .serviceTypeId(request.getServiceTypeId())
//                 .requestedDate(request.getRequestedDate())
//                 .requestedTime(request.getRequestedTime())
//                 .location(request.getLocation())
//                 .build();

//         UnifiedAssistantResponse unifiedResponse = getUnifiedResponse(effectiveRequest, currentUser, recentHistory);
//         applySessionProviderContext(unifiedResponse, session);

//         ChatResponse chatResponse = executeAction(effectiveRequest, currentUser, unifiedResponse, session);

//         saveAssistantMessage(session, chatResponse);

//         return chatResponse;
//     }

//     @Override
//     public ChatResponse startNewChat(User currentUser) {
//         Long userId = currentUser != null ? currentUser.getId() : null;

//         if (userId != null) {
//             chatSessionRepository.findActiveSessionByUser(userId).ifPresent(session -> {
//                 session.endSession();
//                 chatSessionRepository.save(session);
//             });
//         }

//         ChatSession session = ChatSession.builder()
//                 .userId(userId)
//                 .isActive(true)
//                 .detectedLanguage("ar")
//                 .build();

//         ChatSession savedSession = chatSessionRepository.save(session);

//         return ChatResponse.builder()
//                 .sessionId(savedSession.getSessionId())
//                 .timestamp(LocalDateTime.now())
//                 .message("New chat started. How can I help you today?")
//                 .responseType(ChatResponseType.TEXT)
//                 .detectedLanguage("ar")
//                 .build();
//     }

//     private ChatSession resolveSession(AiChatRequest request, Long userId) {
//         if (StringUtils.hasText(request.getSessionId())) {
//             ChatSession session = chatSessionRepository.findBySessionIdAndIsActiveTrue(request.getSessionId())
//                     .orElseThrow(() -> new BadRequestException("Invalid or expired session ID"));

//             if (!Objects.equals(session.getUserId(), userId)) {
//                 throw new ForbiddenException("You are not allowed to access this chat session");
//             }

//             return session;
//         }

//         if (userId != null) {
//             Optional<ChatSession> activeSession = chatSessionRepository.findActiveSessionByUser(userId);
//             if (activeSession.isPresent()) {
//                 return activeSession.get();
//             }
//         }

//         ChatSession session = ChatSession.builder()
//                 .userId(userId)
//                 .isActive(true)
//                 .detectedLanguage("ar")
//                 .build();
//         return chatSessionRepository.save(session);
//     }

//     private List<ChatMessage> getRecentHistory(List<ChatMessage> fullHistory, int maxTurns) {
//         if (fullHistory == null || fullHistory.isEmpty()) {
//             return new ArrayList<>();
//         }
//         if (fullHistory.size() <= maxTurns) {
//             return fullHistory;
//         }
//         return fullHistory.subList(fullHistory.size() - maxTurns, fullHistory.size());
//     }

//     private List<GeminiClient.ConversationTurn> toConversationTurns(List<ChatMessage> history) {
//         return history.stream()
//                 .map(msg -> new GeminiClient.ConversationTurn(
//                         msg.getSenderRole() == MessageRole.USER ? "user" : "assistant",
//                         msg.getContent()))
//                 .collect(Collectors.toList());
//     }

//     private UnifiedAssistantResponse getUnifiedResponse(AiChatRequest request, User currentUser,
//             List<ChatMessage> history) {
//         if (!geminiClient.isEnabled()) {
//             return smartFallback(request.getMessage(), history);
//         }

//         String serviceCatalogJson = getServiceCatalogJsonOrNull();
//         String conversationContext = buildConversationContext(history);

//         String systemPrompt = buildUnifiedSystemPrompt(currentUser, serviceCatalogJson);

//         String userMessage = conversationContext + "\n\nUser: " + request.getMessage();

//         List<GeminiClient.ConversationTurn> turns = toConversationTurns(history);
//         turns.add(new GeminiClient.ConversationTurn("user", request.getMessage()));

//         String modelResponse = geminiClient.generateJson(turns, systemPrompt);
//         UnifiedAssistantResponse parsed = parseUnifiedResponse(modelResponse);

//         if (parsed != null && parsed.isValid()) {
//             if (parsed.serviceTypeId != null) {
//                 log.info("Gemini resolved serviceTypeId: {}", parsed.serviceTypeId);
//             }
//             return parsed;
//         }

//         log.warn("Failed to parse unified response from Gemini, using smart fallback");
//         return smartFallback(request.getMessage(), history);
//     }

//     private String buildUnifiedSystemPrompt(User currentUser, String serviceCatalogJson) {
//         String userRole = (currentUser != null && currentUser.getRole() != null)
//                 ? currentUser.getRole().name()
//                 : "anonymous";

//         String lastProviderContext = "";
//         String catalogSection = StringUtils.hasText(serviceCatalogJson)
//                 ? "\n## AVAILABLE SERVICE TYPES (choose serviceTypeId from this list by MEANING, not exact word match):\n"
//                         + serviceCatalogJson
//                         + "\n- If the user's request matches one of these by meaning, set \"serviceTypeId\" to its id.\n"
//                         + "- If unsure or no good match, set \"serviceTypeId\": null - it will be resolved separately.\n"
//                 : "\n## NOTE: Service catalog is too large to include here. Always set \"serviceTypeId\": null; "
//                         + "it will be resolved in a follow-up step using \"serviceTypeName\".\n";

//         return """
//                 You are Ay Khedma AI Assistant - a comprehensive service marketplace connecting consumers with ALL types of service providers across any profession or industry.

//                 ## ABOUT THE APP:
//                 Ay Khedma is a universal platform that includes EVERY profession and service type imaginable:
//                 - MEDICAL: Doctors, Dentists, Specialists, Clinics, Hospitals
//                 - ENGINEERING: Civil, Electrical, Mechanical, Architectural, Structural
//                 - LEGAL: Lawyers, Legal Consultants, Document Drafting, Court Representation
//                 - HOME SERVICES: Plumbers, Electricians, Painters, Cleaners, Movers, HVAC
//                 - TECH: Programmers, IT Support, Web Developers, Cybersecurity Experts
//                 - EDUCATION: Teachers, Tutors, Trainers, Instructors
//                 - DESIGN: Graphic Designers, Interior Designers, UI/UX, Architects
//                 - CONSTRUCTION: Contractors, Builders, Renovation Experts
//                 - AUTOMOTIVE: Mechanics, Car Repair, Detailing
//                 - BUSINESS: Consultants, Accountants, Marketing Experts
//                 - CREATIVE: Photographers, Videographers, Musicians, Artists
//                 - WELLNESS: Coaches, Trainers, Nutritionists, Therapists
//                 - AND ANY OTHER PROFESSION the user might need!

//                 ## YOUR TASK:
//                 Analyze the user's message and return a SINGLE JSON response that captures their intent.

//                 ## AVAILABLE ACTIONS (MUST USE ONE OF THESE):
//                 1. SEARCH_PROVIDERS - When user wants to FIND or SEARCH for providers (e.g., "دكتور", "سباك", "عايز حد يصلح التكييف", "جيبلي دكاترة", "فين اقرب سباك")
//                 2. SUGGEST_SOLUTIONS - When user describes a problem, fault, damage, or malfunction and needs quick troubleshooting steps first
//                 3. CHECK_AVAILABILITY - When user wants to SEE AVAILABLE TIME SLOTS for a SPECIFIC provider (e.g., "وريني المواعيد عند دكتور أحمد", "طارق أحمد متاح امتى", "عندي عنده مواعيد", "شوفيلي جدول دكتور محمد")
//                 4. CREATE_BOOKING - When user wants to BOOK or RESERVE an appointment with specific provider, date, and time
//                 5. ASK_CLARIFICATION - When missing critical information needed to proceed
//                 6. GENERAL - For casual conversation, greetings, or questions not related to finding/booking services

//                 ## CRITICAL RULES FOR SUGGEST_SOLUTIONS:
//                 - Use SUGGEST_SOLUTIONS for simple, DIY issues (loose screw, stuck door, dripping tap)
//                 - Offer 2-4 practical steps the user can try themselves
//                 - Always include safety warnings if needed (electricity, gas, water)
//                 - Ask at the end if they want provider search
//                 - If issue seems dangerous/complex (broken pipe, electrical spark, gas leak), prioritize safety and suggest professional help immediately
//                 ## CRITICAL RULES FOR CHECK_AVAILABILITY:
//                 - Use CHECK_AVAILABILITY when user wants to VIEW available time slots for a SPECIFIC provider
//                 - Keywords: "وريني المواعيد", "متاح امتى", "available slots", "show me schedule", "شوفيلي جدول", "عندي عنده مواعيد"

//                 - REQUIRED fields:
//                   * providerName OR providerId (extract from user message) - REQUIRED!

//                 - OPTIONAL fields (user may or may not provide):
//                   * requestedDate - If user provides a date, use it. If NOT provided, set to null (DO NOT ask for it!)

//                 - BEHAVIOR:
//                   * If user provides a specific date: check availability for that date only
//                   * If user does NOT provide a date: we will show upcoming availability (next 7 days)
//                   * NEVER set needsClarification=true just because requestedDate is missing
//                   * NEVER add requestedDate to missingFields
//                   * When user says "معاه", "هو", or any pronoun referring to a provider, USE THE PROVIDER FROM CONTEXT
//                   * Do NOT ask for clarification about provider when it's implied
//                   * Always maintain conversation context

//                 ## CRITICAL RULES FOR CREATE_BOOKING:
//                 - Use CREATE_BOOKING when user wants to MAKE a booking or appointment
//                 - Keywords: "احجز", "حجز", "book", "appointment", "عايز أحجز"
//                 - REQUIRED: providerName/providerId, requestedDate, requestedTime, problemDescription
//                 - If missing ANY of these, set needsClarification=true and list missingFields
//                 - For unauthenticated users, set action=ASK_CLARIFICATION with message to login
//                 -If the user says "معاه", "هو", "same provider", "هذا" without naming:
//                 → Keep providerId and providerName from the previous turn
//                 → Do NOT set needsClarification for provider

//                 ## CRITICAL RULES FOR SEARCH_PROVIDERS:
//                 - Use SEARCH_PROVIDERS when user wants to FIND providers
//                 - Always set "serviceTypeName" to your best guess of the service category in Arabic or English
//                 - Additionally set "serviceTypeId" using the catalog below when you can confidently match one

//                 ## CRITICAL: PROVIDER CONTEXT IN CONVERSATION
//                 - The conversation history is provided before the current user message
//                 - ALWAYS check the last assistant message for provider names and IDs
//                 - If the last assistant message mentioned a provider, use that as context
//                 - The providerId in the previous turn should be preserved
//                 """
//                 + catalogSection
//                 + """

//                         ## OUTPUT FORMAT:
//                         Return ONLY valid JSON. No extra text, no explanation, no markdown.
//                                         The reply field must be the final user-facing answer, not a raw intent label.

//                         {
//                           "action": "SEARCH_PROVIDERS | SUGGEST_SOLUTIONS | CHECK_AVAILABILITY | CREATE_BOOKING | ASK_CLARIFICATION | GENERAL",
//                           "intent": "SEARCH_PROVIDERS | SUGGEST_SOLUTIONS | GET_AVAILABILITY | CREATE_BOOKING | CLARIFICATION | GENERAL",
//                           "reply": "natural language response to user (Arabic if user message is Arabic, otherwise English)",
//                           "providerId": number or null,
//                           "providerName": string or null,
//                           "serviceTypeName": string or null,
//                           "serviceTypeId": number or null,
//                           "requestedDate": "yyyy-MM-dd" or null,
//                           "requestedTime": "HH:mm" or null,
//                           "problemDescription": string or null,
//                           "searchRadiusKm": number or null,
//                           "needsClarification": boolean,
//                           "missingFields": ["field1", "field2"] or null
//                         }

//                         ## EXAMPLES:

//                         ## EXAMPLES FOR SUGGEST_SOLUTIONS:

//                         ### Example: Electrical Issue
//                         User: "اللمبة مش شغالة"
//                         → {"action":"SUGGEST_SOLUTIONS","intent":"SUGGEST_SOLUTIONS","reply":"جرب: 1) غير اللمبة، 2) تأكد من الفيشة، 3) راجع القاطع. لو لسة مش شغالة، أقدر أدورلك على كهربائي.","needsClarification":false}

//                         ### Example: Plumbing Issue
//                         User: "الحنفية بتقطر"
//                         → {"action":"SUGGEST_SOLUTIONS","intent":"SUGGEST_SOLUTIONS","reply":"جرب شد الصامولة أو غير الجلدة. لو القطر مستمر، أقدر أدورلك على سباك.","needsClarification":false}

//                         ### Example: When user directly asks for provider
//                         User: "جيبلي سباك"
//                         → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، بدورلك على سباكين في منطقتك...","serviceTypeName":"سباك","serviceTypeId":12}

//                         ### Example: Complex issue that needs professional
//                         User: "الدولاب كله متكسر"
//                         → {"action":"SUGGEST_SOLUTIONS","intent":"SUGGEST_SOLUTIONS","reply":"دي مشكلة أكبر من إصلاح بسيط. الأفضل تستعين بنجار محترف. عايز أدورلك على نجارين في منطقتك؟"}

//                         ### Example 1: CHECK_AVAILABILITY (with date provided)
//                         User: "وريني المواعيد عند طارق أحمد يوم الأحد"
//                         → {"action":"CHECK_AVAILABILITY","intent":"GET_AVAILABILITY","reply":"تمام، ببحثلك عن المواعيد المتاحة عند طارق أحمد يوم الأحد","providerName":"طارق أحمد","requestedDate":"2026-05-07","needsClarification":false}

//                         ### Example 2: CHECK_AVAILABILITY (without date - just show upcoming)
//                         User: "وريني المواعيد المتاحة عند طارق أحمد"
//                         → {"action":"CHECK_AVAILABILITY","intent":"GET_AVAILABILITY","reply":"تمام، ببحثلك عن أقرب المواعيد المتاحة عند طارق أحمد","providerName":"طارق أحمد","requestedDate":null,"needsClarification":false}

//                         ### Example 3: CHECK_AVAILABILITY (Arabic slang)
//                         User: "شوفيلي جدول دكتور محمد"
//                         → {"action":"CHECK_AVAILABILITY","intent":"GET_AVAILABILITY","reply":"ببحثلك عن جدول مواعيد دكتور محمد للأيام القادمة","providerName":"دكتور محمد","requestedDate":null,"needsClarification":false}

//                         ### Example 4: CHECK_AVAILABILITY (plural)
//                         User: "عند دكتور أسماء مواعيد امتى"
//                         → {"action":"CHECK_AVAILABILITY","intent":"GET_AVAILABILITY","reply":"ببحثلك عن المواعيد المتاحة عند دكتورة أسماء","providerName":"دكتور أسماء","requestedDate":null,"needsClarification":false}

//                         ### Example 5: CHECK_AVAILABILITY (with provider ID)
//                         User: "وريني المواعيد عند provider 123"
//                         → {"action":"CHECK_AVAILABILITY","intent":"GET_AVAILABILITY","reply":"ببحثلك عن المواعيد المتاحة للمزود رقم 123","providerId":123,"requestedDate":null,"needsClarification":false}

//                         ### Example 6: CREATE_BOOKING (all info provided)
//                         User: "عايز احجز مع دكتور محمد يوم الأحد الساعة 4، عندي صداع"
//                         → {"action":"CREATE_BOOKING","intent":"CREATE_BOOKING","reply":"تمام، هحجزلك مع دكتور محمد يوم الأحد الساعة 4 لعلاج الصداع","providerName":"دكتور محمد","requestedDate":"2026-05-07","requestedTime":"16:00","problemDescription":"صداع"}

//                         ### Example 7: CREATE_BOOKING (missing info - needs clarification)
//                         User: "عايز احجز مع دكتور محمد"
//                         → {"action":"CREATE_BOOKING","intent":"CREATE_BOOKING","reply":"تمام، هحجزلك مع دكتور محمد. محتاج منك التاريخ والوقت ووصف المشكلة","providerName":"دكتور محمد","needsClarification":true,"missingFields":["requestedDate","requestedTime","problemDescription"]}

//                         ### Example 8: SEARCH_PROVIDERS (medical)
//                         User: "أنا تعبان وعندي صداع"
//                         → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"ممكن توصفلي الأعراض بالتفصيل عشان أقترح دكتور مناسب؟","serviceTypeName":"طبيب","needsClarification":true,"missingFields":["symptoms"]}

//                         ### Example 9: SEARCH_PROVIDERS (home service)
//                         User: "الحوض عندي مسرب"
//                         → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، سأبحث لك عن سباكين متاحين في منطقتك.","serviceTypeName":"سباك","searchRadiusKm":10}

//                         ### Example 10: SEARCH_PROVIDERS (engineering)
//                         User: "عايز مهندس معماري يصمم فيلا"
//                         → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، بدورلك على مهندسين معماريين متخصصين في تصميم الفلل. ممكن تقولي في أي منطقة؟","serviceTypeName":"مهندس معماري","searchRadiusKm":10}

//                         ### Example 11: SEARCH_PROVIDERS (legal)
//                         User: "عايز محامي للقضية بتاعتي"
//                         → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"فهمت، محتاج محامي. ممكن توصف نوع القضية عشان ألاقي لك المتخصص المناسب؟","serviceTypeName":"محامي","needsClarification":true,"missingFields":["caseType"]}

//                         ### Example 12: SEARCH_PROVIDERS (tech)
//                         User: "عايز مبرمج يعمل لي تطبيق"
//                         → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"تمام، هدورلك على مبرمجين متخصصين في تطوير التطبيقات. نوع التطبيق إيه؟","serviceTypeName":"مبرمج","needsClarification":true,"missingFields":["appType"]}

//                         ### Example 13: SEARCH_PROVIDERS (urgent/emergency)
//                         User: "طوارئ كهربائي البيت وقعت الكهرباء"
//                         → {"action":"SEARCH_PROVIDERS","intent":"SEARCH_PROVIDERS","reply":"فهمت، حالة طارئة. هدورلك على كهربائي قريب منك جداً.","serviceTypeName":"كهربائي","problemDescription":"emergency","searchRadiusKm":5}

//                         ### Example 14: GENERAL (greeting)
//                         User: "السلام عليكم"
//                         → {"action":"GENERAL","intent":"GENERAL","reply":"وعليكم السلام ورحمة الله وبركاته! كيف أقدر أساعدك اليوم؟ ممكن تطلب أي خدمة - دكتور، مهندس، محامي، سباك، كهربائي، أو تحجز مع أي مزود."}

//                         ### Example 15: GENERAL (question about app)
//                         User: "إيه الخدمات اللي عندكم؟"
//                         → {"action":"GENERAL","intent":"GENERAL","reply":"أي خدمة - أي حرفة - أي مهنة. عندنا أطباء، مهندسين، محامين، سباكين، كهربائيين، مبرمجين، معلمين، ومئات المهن التانية. ممكن تطلب أي خدمة واحنا ندورلك على أفضل مقدمي الخدمة في منطقتك."}

//                         ## DATE HANDLING:
//                         Today's date is: """
//                 + LocalDate.now() + """
//                         - "بكرا" / "tomorrow" → """ + LocalDate.now().plusDays(1)
//                 + """
//                         - "الأحد" / "Sunday" → next Sunday from today
//                         - "الأحد القادم" / "next Sunday" → the Sunday after this week
//                         - Always use format "yyyy-MM-dd"
//                         - If user doesn't provide a date, set requestedDate = null (DO NOT ask for it in CHECK_AVAILABILITY)

//                         ## TIME HANDLING:
//                         - "الساعة 4" / "4" → If user provides afternoon context, use "16:00", otherwise ask for clarification
//                         - "الساعة 4 العصر" / "4 PM" / "16:00" → "16:00" (24-hour format)
//                         - "الساعة 10 صباحاً" / "10 AM" / "10:00" → "10:00" (24-hour format)
//                         - "1:30" / "1.30" / "الساعة 1:30" → interpret as "13:30" in afternoon context, otherwise "01:30" if explicitly AM
//                         - Always use 24-hour format "HH:mm" (00:00 to 23:59)
//                         - IMPORTANT: When time is ambiguous (like "1:30" without AM/PM), prefer afternoon (13:xx) unless context suggests morning

//                         ## CONTEXT:
//                         Current user role: """
//                 + userRole
//                 + """
//                         - If role = "anonymous" or role = "null" or role = "ANONYMOUS", user is NOT logged in
//                         - For CREATE_BOOKING when user is anonymous, set action=ASK_CLARIFICATION and reply: "يرجى تسجيل الدخول أولاً للحجز / Please login first to book"
//                         - For SEARCH_PROVIDERS and CHECK_AVAILABILITY, anonymous users ARE allowed

//                         ## REMEMBER:
//                         - Return ONLY valid JSON
//                         - No explanation outside JSON
//                         - No markdown formatting around JSON
//                         - Always use double quotes for JSON properties
//                         - Keep replies concise, friendly, and helpful in Arabic or English matching the user's language
//                         """;
//     }

//     private String buildConversationContext(List<ChatMessage> history) {
//         if (history == null || history.isEmpty()) {
//             return "No previous conversation.";
//         }

//         StringBuilder context = new StringBuilder();
//         context.append("## PREVIOUS CONVERSATION CONTEXT:\n");

//         int start = Math.max(0, history.size() - 5);
//         for (int i = start; i < history.size(); i++) {
//             ChatMessage msg = history.get(i);
//             String role = msg.getSenderRole() == MessageRole.USER ? "User" : "Assistant";
//             context.append(role).append(": ").append(msg.getContent()).append("\n");
//         }

//         return context.toString();
//     }

//     private UnifiedAssistantResponse parseUnifiedResponse(String modelResponse) {
//         if (!StringUtils.hasText(modelResponse)) {
//             return null;
//         }

//         try {
//             String json = extractJson(modelResponse);
//             if (!StringUtils.hasText(json)) {
//                 return null;
//             }

//             JsonNode node = objectMapper.readTree(json);
//             UnifiedAssistantResponse response = new UnifiedAssistantResponse();

//             String actionText = node.path("action").asText(null);
//             if (StringUtils.hasText(actionText)) {
//                 try {
//                     response.action = Action.valueOf(actionText.trim().toUpperCase(Locale.ROOT));
//                 } catch (IllegalArgumentException ignored) {
//                 }
//             }

//             String intentText = node.path("intent").asText(null);
//             if (StringUtils.hasText(intentText)) {
//                 try {
//                     response.intent = Intent.valueOf(intentText.trim().toUpperCase(Locale.ROOT));
//                 } catch (IllegalArgumentException ignored) {
//                 }
//             }

//             response.reply = node.path("reply").asText(null);
//             response.providerId = node.hasNonNull("providerId") && node.path("providerId").canConvertToLong()
//                     ? node.path("providerId").asLong()
//                     : null;
//             response.providerName = node.path("providerName").asText(null);
//             response.serviceTypeName = node.path("serviceTypeName").asText(null);
//             response.serviceTypeId = node.hasNonNull("serviceTypeId") && node.path("serviceTypeId").canConvertToLong()
//                     ? node.path("serviceTypeId").asLong()
//                     : null;
//             response.requestedDate = parseDateSafe(node.path("requestedDate").asText(null));
//             response.requestedTime = parseTimeSafe(node.path("requestedTime").asText(null));
//             response.problemDescription = node.path("problemDescription").asText(null);
//             response.searchRadiusKm = node.hasNonNull("searchRadiusKm") && node.path("searchRadiusKm").canConvertToInt()
//                     ? node.path("searchRadiusKm").asInt()
//                     : DEFAULT_SEARCH_RADIUS_KM;

//             // Parse needsClarification from Gemini
//             response.needsClarification = node.path("needsClarification").asBoolean(false);

//             // Parse missingFields array and filter based on action
//             if (node.has("missingFields") && node.path("missingFields").isArray()) {
//                 response.missingFields = new ArrayList<>();
//                 for (JsonNode field : node.path("missingFields")) {
//                     String missingField = field.asText();

//                     // For CHECK_AVAILABILITY, requestedDate is optional - don't treat as missing
//                     if (response.action == Action.CHECK_AVAILABILITY && "requestedDate".equals(missingField)) {
//                         log.debug("CHECK_AVAILABILITY: Ignoring requestedDate as missing field (date is optional)");
//                         continue;
//                     }

//                     response.missingFields.add(missingField);
//                 }
//             }

//             // If missingFields is empty after filtering, reset needsClarification to false
//             if (response.missingFields != null && response.missingFields.isEmpty()) {
//                 response.needsClarification = false;
//                 response.missingFields = null;
//             }

//             // Set default action if still null
//             if (response.action == null) {
//                 response.action = Action.ASK_CLARIFICATION;
//                 response.intent = Intent.CLARIFICATION;
//                 if (!StringUtils.hasText(response.reply)) {
//                     response.reply = "كيف يمكنني مساعدتك؟ / How can I help you?";
//                 }
//             }

//             return response;
//         } catch (Exception ex) {
//             log.debug("Failed to parse unified response: {}", ex.getMessage());
//             return null;
//         }
//     }

//     private UnifiedAssistantResponse smartFallback(String message, List<ChatMessage> history) {
//         String normalized = normalize(message);
//         UnifiedAssistantResponse response = new UnifiedAssistantResponse();
//         response.searchRadiusKm = DEFAULT_SEARCH_RADIUS_KM;

//         if (containsAny(normalized, "طوارئ", "emergency", "urgent", "دلوقتي", "حالاً", "اسعاف")) {
//             response.action = Action.SEARCH_PROVIDERS;
//             response.intent = Intent.SEARCH_PROVIDERS;
//             response.problemDescription = "emergency";
//             response.searchRadiusKm = 5;
//             response.reply = "فهمت، محتاج مساعدة عاجلة. هدورلك على أقرب مقدم خدمة متاح. وصِف لي المشكلة باختصار.";
//             return response;
//         }

//         response.action = Action.ASK_CLARIFICATION;
//         response.intent = Intent.CLARIFICATION;
//         response.reply = "كيف أقدر أساعدك؟ ممكن توصف المشكلة بالتفصيل، وهحاول ألاقي حل مناسب أو أرشحلك مختص.";
//         return response;
//     }

//     private ChatResponse executeAction(AiChatRequest request, User currentUser,
//             UnifiedAssistantResponse unified, ChatSession session) {
//         ChatResponse.ChatResponseBuilder responseBuilder = ChatResponse.builder()
//                 .sessionId(request.getSessionId())
//                 .timestamp(LocalDateTime.now())
//                 .message(unified.reply)
//                 .detectedLanguage(detectLanguage(request.getMessage()));

//         if (unified.needsClarification || unified.action == Action.ASK_CLARIFICATION) {
//             return responseBuilder.responseType(ChatResponseType.CLARIFICATION).build();
//         }

//         return switch (unified.action) {
//             case SEARCH_PROVIDERS -> handleProviderSearchAction(request, currentUser, unified, responseBuilder);
//             case SUGGEST_SOLUTIONS -> handleSuggestionAction(request, unified, responseBuilder);
//             case CHECK_AVAILABILITY -> handleAvailabilityAction(unified, session, responseBuilder);
//             case CREATE_BOOKING -> handleBookingAction(request, currentUser, unified, responseBuilder);
//             default -> responseBuilder.responseType(ChatResponseType.TEXT).build();
//         };
//     }

//     // ===========================================================

//     private ChatResponse handleProviderSearchAction(AiChatRequest request, User currentUser,
//             UnifiedAssistantResponse unified, ChatResponse.ChatResponseBuilder responseBuilder) {

//         String userMessage = request.getMessage();

//         ServiceType selectedService = null;
//         if (unified.serviceTypeId != null) {
//             selectedService = getCachedServiceTypes().stream()
//                     .filter(st -> st.getId().equals(unified.serviceTypeId))
//                     .findFirst()
//                     .orElse(null);
//             log.info("Using serviceTypeId from Gemini: {} -> {}", unified.serviceTypeId,
//                     selectedService != null ? selectedService.getName() : "not found");
//         }

//         if (selectedService == null && StringUtils.hasText(unified.serviceTypeName)) {
//             selectedService = resolveServiceTypeByName(unified.serviceTypeName);
//         }

//         if (selectedService == null) {
//             selectedService = resolveServiceTypeByMeaning(userMessage);
//         }

//         if (selectedService == null) {
//             return responseBuilder
//                     .responseType(ChatResponseType.CLARIFICATION)
//                     .message("ممكن توصف لي الخدمة اللي محتاجها بشكل أوضح؟")
//                     .build();
//         }

//         log.info("Selected service by meaning: {} ({})", selectedService.getName(), selectedService.getNameAr());

//         LocationDTO searchLocation = resolveSearchLocation(currentUser, request);
//         Integer radiusKm = unified.searchRadiusKm != null ? unified.searchRadiusKm : DEFAULT_SEARCH_RADIUS_KM;

//         List<ProviderSummaryResponse> providers = findAndSortProviders(
//                 selectedService.getId(), searchLocation, radiusKm, currentUser);

//         log.info("Found {} providers for service: {}", providers.size(), selectedService.getName());

//         String replyMessage = buildSmartReply(providers, selectedService, userMessage);

//         return responseBuilder
//                 .responseType(ChatResponseType.PROVIDER_LIST)
//                 .providers(providers)
//                 .message(replyMessage)
//                 .build();
//     }

//     private ServiceType resolveServiceTypeByName(String name) {
//         if (!StringUtils.hasText(name))
//             return null;
//         return getCachedServiceTypes().stream()
//                 .filter(st -> name.equalsIgnoreCase(st.getName()) ||
//                         name.equalsIgnoreCase(st.getNameAr()) ||
//                         st.getName().toLowerCase().contains(name.toLowerCase()) ||
//                         st.getNameAr().toLowerCase().contains(name.toLowerCase()))
//                 .findFirst()
//                 .orElse(null);
//     }

//     private ChatResponse handleSuggestionAction(AiChatRequest request,
//             UnifiedAssistantResponse unified, ChatResponse.ChatResponseBuilder responseBuilder) {

//         String suggestionReply = unified.reply;

//         if (!StringUtils.hasText(suggestionReply)) {
//             suggestionReply = "ممكن توضح المشكلة بالتفصيل عشان أقدر أساعدك بشكل أفضل.";
//         }

//         return responseBuilder
//                 .responseType(ChatResponseType.SUGGESTION)
//                 .message(suggestionReply)
//                 .build();
//     }

//     private ServiceType resolveServiceTypeByMeaning(String userMessage) {

//         List<ServiceType> allServices = getCachedServiceTypes();

//         if (allServices.isEmpty()) {
//             log.warn("No service types in database");
//             return null;
//         }

//         String cacheKey = normalize(userMessage);
//         CachedValue<Long> cached = StringUtils.hasText(cacheKey) ? serviceTypeByMeaningCache.get(cacheKey) : null;
//         if (cached != null && !cached.isExpired()) {
//             Long cachedId = cached.value;
//             if (cachedId == null) {
//                 return null;
//             }
//             return allServices.stream().filter(st -> st.getId().equals(cachedId)).findFirst().orElse(null);
//         }

//         ServiceType resolved;
//         if (allServices.size() <= 50 && geminiClient.isEnabled()) {
//             resolved = resolveServiceTypeWithAiByMeaning(userMessage, allServices);
//         } else {
//             resolved = resolveServiceTypeWithCategoriesThenAi(userMessage);
//         }

//         if (StringUtils.hasText(cacheKey)) {
//             serviceTypeByMeaningCache.put(cacheKey,
//                     new CachedValue<>(resolved != null ? resolved.getId() : null, AI_LOOKUP_CACHE_TTL));
//         }

//         return resolved;
//     }

//     // ====================================================

//     private ServiceType resolveServiceTypeWithAiByMeaning(String userMessage, List<ServiceType> allServices) {

//         String servicesJson = allServices.stream()
//                 .map(st -> String.format(
//                         "{\"id\":%d,\"name\":\"%s\",\"nameAr\":\"%s\",\"description\":\"%s\"}",
//                         st.getId(),
//                         escapeJson(st.getName()),
//                         escapeJson(st.getNameAr()),
//                         escapeJson(st.getDescription() != null ? st.getDescription() : "")))
//                 .collect(Collectors.joining(","));

//         String prompt = "أنت مساعد ذكي. المستخدم بيوصف خدمة he/she محتاجها.\n"
//                 + "مهمتك: اختار أنسب خدمة من القائمة حسب المعنى، مش لازم تطابق الكلمة بالضبط.\n\n"
//                 + "طلب المستخدم: " + userMessage + "\n\n"
//                 + "الخدمات المتاحة: [" + servicesJson + "]\n\n"
//                 + "قواعد الاختيار:\n"
//                 + "- \"أنا تعبان / دكتور / عندي وجع / صحتي\" -> اختار أي خدمة طبية (Doctor, Dentist, etc)\n"
//                 + "- \"الحوض مسرب / سباك / مواسير\" -> اختار خدمة سباكة (Pipe Repair, Drain Cleaning, etc)\n"
//                 + "- \"كهربائي / فيشة / إضاءة / قطع الكهرباء\" -> اختار خدمة كهرباء\n"
//                 + "- \"محامي / قضية / عقد\" -> اختار خدمة قانونية\n"
//                 + "- \"تنظيف / كناسة / بيت\" -> اختار خدمة تنظيف\n"
//                 + "- \"مهندس / تصميم / بناء\" -> اختار خدمة هندسية\n\n"
//                 + "Return ONLY JSON: {\"serviceTypeId\": number}\n"
//                 + "إذا مش متأكد، ارجع {\"serviceTypeId\": null}";

//         String response = geminiClient.generateJson(prompt);

//         if (StringUtils.hasText(response)) {
//             try {
//                 String json = extractJson(response);
//                 Long id = objectMapper.readTree(json).path("serviceTypeId").asLong();

//                 return allServices.stream()
//                         .filter(st -> st.getId().equals(id))
//                         .findFirst()
//                         .orElse(null);
//             } catch (Exception ex) {
//                 log.debug("AI meaning resolution failed: {}", ex.getMessage());
//             }
//         }

//         return null;
//     }

//     private ServiceType resolveServiceTypeWithCategoriesThenAi(String userMessage) {

//         List<ServiceCategory> allCategories = getCachedCategories();
//         ServiceCategory detectedCategory = detectCategoryWithAi(userMessage, allCategories);

//         if (detectedCategory == null) {
//             return null;
//         }

//         List<ServiceType> servicesInCategory = serviceTypeRepository.findByCategoryId(detectedCategory.getId());

//         if (servicesInCategory.isEmpty()) {
//             return null;
//         }

//         return resolveServiceTypeWithAiByMeaning(userMessage, servicesInCategory);
//     }

//     private ServiceCategory detectCategoryWithAi(String userMessage, List<ServiceCategory> categories) {

//         String categoriesJson = categories.stream()
//                 .map(cat -> String.format("{\"id\":%d,\"name\":\"%s\",\"nameAr\":\"%s\"}",
//                         cat.getId(), escapeJson(cat.getName()), escapeJson(cat.getNameAr())))
//                 .collect(Collectors.joining(","));

//         String prompt = "أنت مساعد في تطبيق يضم جميع المهن والخدمات (أطباء، مهندسين، سباكين، محامين، إلخ).\n"
//                 + "المستخدم يقول: " + userMessage + "\n\n"
//                 + "التصنيفات المتاحة: [" + categoriesJson + "]\n\n"
//                 + "المطلوب: اختار التصنيف الوحيد الأنسب لطلب المستخدم من حيث المعنى، مش لازم كلمة مطابقة.\n\n"
//                 + "أمثلة:\n"
//                 + "- \"أنا تعبان / دكتور / عندي وجع / صحتي\" -> اختار التصنيف الطبي (لو موجود)\n"
//                 + "- \"الحوض مسرب / سباك / مواسير / حمام بايظ\" -> Plumbing\n"
//                 + "- \"عايز محامي / قضية / contract\" -> Legal\n"
//                 + "- \"كهربائي / فيشة / إضاءة\" -> Electrical\n"
//                 + "- \"مهندس / تصميم / بناء\" -> Engineering\n"
//                 + "- \"أنظف / تنظيف / كناسة\" -> Cleaning\n\n"
//                 + "Return ONLY JSON: {\"categoryId\": number}\n"
//                 + "إذا مش متأكد، ترجع {\"categoryId\": null}";

//         String response = geminiClient.generateJson(prompt);

//         if (StringUtils.hasText(response)) {
//             try {
//                 String json = extractJson(response);
//                 JsonNode node = objectMapper.readTree(json);

//                 if (node.has("categoryId") && !node.path("categoryId").isNull()) {
//                     Long categoryId = node.path("categoryId").asLong();
//                     return categories.stream()
//                             .filter(cat -> cat.getId().equals(categoryId))
//                             .findFirst()
//                             .orElse(null);
//                 }
//             } catch (Exception ex) {
//                 log.debug("AI category detection failed: {}", ex.getMessage());
//             }
//         }

//         return null;
//     }

//     // ---- Catalog caching helpers ----

//     private List<ServiceType> getCachedServiceTypes() {
//         CachedValue<List<ServiceType>> cached = serviceTypesCache;
//         if (cached != null && !cached.isExpired()) {
//             return cached.value;
//         }
//         List<ServiceType> fresh = serviceTypeRepository.findAll();
//         serviceTypesCache = new CachedValue<>(fresh, CATALOG_CACHE_TTL);
//         return fresh;
//     }

//     private List<ServiceCategory> getCachedCategories() {
//         CachedValue<List<ServiceCategory>> cached = categoriesCache;
//         if (cached != null && !cached.isExpired()) {
//             return cached.value;
//         }
//         List<ServiceCategory> fresh = categoryRepository.findAll();
//         categoriesCache = new CachedValue<>(fresh, CATALOG_CACHE_TTL);
//         return fresh;
//     }

//     private String getServiceCatalogJsonOrNull() {
//         CachedValue<String> cached = serviceCatalogJsonCache;
//         if (cached != null && !cached.isExpired()) {
//             return cached.value;
//         }

//         List<ServiceType> allServices = getCachedServiceTypes();
//         String json;
//         if (allServices.isEmpty() || allServices.size() > MAX_SERVICES_FOR_INLINE_CATALOG) {
//             json = null;
//         } else {
//             json = "[" + allServices.stream()
//                     .map(st -> String.format("{\"id\":%d,\"name\":\"%s\",\"nameAr\":\"%s\"}",
//                             st.getId(), escapeJson(st.getName()), escapeJson(st.getNameAr())))
//                     .collect(Collectors.joining(",")) + "]";
//         }

//         serviceCatalogJsonCache = new CachedValue<>(json, CATALOG_CACHE_TTL);
//         return json;
//     }

//     private List<ProviderSummaryResponse> findAndSortProviders(Long serviceTypeId,
//             LocationDTO userLocation, Integer radiusKm, User currentUser) {

//         List<Provider> providers = providerRepository.findByServiceTypeIdAndVerificationStatus(
//                 serviceTypeId, VerificationStatus.VERIFIED);

//         if (providers.isEmpty()) {
//             log.info("No verified providers for service type: {}", serviceTypeId);
//             return new ArrayList<>();
//         }

//         List<ProviderSummaryResponse> responses = providers.stream()
//                 .map(provider -> toProviderSummaryWithDistance(provider, userLocation, currentUser))
//                 .collect(Collectors.toList());

//         responses.sort((p1, p2) -> {
//             double score1 = calculateScore(p1);
//             double score2 = calculateScore(p2);
//             return Double.compare(score2, score1);
//         });

//         if (radiusKm != null && radiusKm > 0) {
//             responses = responses.stream()
//                     .filter(provider -> provider.getDistance() == null || provider.getDistance() <= radiusKm)
//                     .collect(Collectors.toList());
//         }

//         return responses;
//     }

//     private double calculateScore(ProviderSummaryResponse provider) {
//         double score = 0.0;

//         // Weight 1: Distance (closer is better) - max 50 points
//         if (provider.getDistance() != null && provider.getDistance() > 0) {
//             // Distance 0km = 50 points, distance 10km = 0 points
//             double distanceScore = Math.max(0, 50 - (provider.getDistance() * 5));
//             score += distanceScore;
//         } else {
//             score += 25; // average if no distance
//         }

//         // Weight 2: Rating - max 50 points
//         if (provider.getAverageRating() != null) {
//             double ratingScore = provider.getAverageRating() * 10;
//             score += ratingScore;
//         } else {
//             score += 25;
//         }

//         return score;
//     }

//     private ProviderSummaryResponse toProviderSummaryWithDistance(Provider provider, LocationDTO userLocation,
//             User currentUser) {
//         ProviderSummaryResponse response = providerMapper.toProviderSummaryResponse(provider);

//         if (currentUser != null && currentUser.getRole() == UserType.CONSUMER) {
//             try {
//                 DistanceResponse distanceResponse = locationService
//                         .calculateDistanceBetweenConsumerAndProvider(currentUser.getId(), provider.getId());
//                 if (distanceResponse != null) {
//                     response.setDistance(Math.round(distanceResponse.getDistanceKm() * 10.0) / 10.0);
//                 }
//             } catch (Exception e) {
//                 log.debug("Could not calculate consumer-provider distance for provider {}: {}", provider.getId(),
//                         e.getMessage());
//             }
//         } else if (userLocation != null && provider.getLocation() != null) {
//             try {
//                 Location userLoc = locationMapper.toEntity(userLocation);
//                 if (userLoc.getLatitude() != null && userLoc.getLongitude() != null) {
//                     double distance = provider.getLocation().calculateDistance(userLoc);
//                     response.setDistance(Math.round(distance * 10.0) / 10.0); // round to 1 decimal
//                 }
//             } catch (Exception e) {
//                 log.debug("Could not calculate distance for provider {}: {}", provider.getId(), e.getMessage());
//             }
//         }

//         return response;
//     }

//     private String buildSmartReply(List<ProviderSummaryResponse> providers, ServiceType serviceType,
//             String userMessage) {

//         String serviceName = serviceType.getNameAr() != null ? serviceType.getNameAr() : serviceType.getName();

//         if (providers.isEmpty()) {
//             return "عذراً، لم أجد أي " + serviceName
//                     + " متاحين في منطقتك حالياً. جرب توسيع نطاق البحث أو حاول مرة أخرى لاحقاً.";
//         }

//         if (providers.size() == 1) {
//             ProviderSummaryResponse p = providers.get(0);
//             String distanceText = p.getDistance() != null ? " (يبعد " + p.getDistance() + " كم)" : "";
//             String ratingText = p.getAverageRating() != null ? " - تقييم " + p.getAverageRating() + "/5" : "";
//             return "وجدت " + serviceName + " واحد مناسب" + distanceText + ratingText + ".\n" +
//                     "الاسم: " + p.getName();
//         }

//         // 2+ providers
//         String topProviders = providers.stream()
//                 .limit(3)
//                 .map(p -> {
//                     String name = p.getName();
//                     String rating = p.getAverageRating() != null ? " ⭐" + p.getAverageRating() : "";
//                     String distance = p.getDistance() != null ? " 📍" + p.getDistance() + "km" : "";
//                     return name + rating + distance;
//                 })
//                 .collect(Collectors.joining("\n• ", "• ", ""));

//         return "وجدت " + providers.size() + " " + serviceName + " مناسبين:\n" + topProviders;
//     }

//     /**
//      * Helper: escape JSON strings
//      */
//     private String escapeJson(String value) {
//         if (value == null)
//             return "";
//         return value.replace("\\", "\\\\")
//                 .replace("\"", "\\\"")
//                 .replace("\n", " ")
//                 .replace("\r", " ");
//     }

//     private List<ProviderSummaryResponse> searchProvidersOptimized(ServiceType serviceType,
//             String queryText, LocationDTO locationDTO, Integer radiusKm) {

//         log.info("Searching for providers - serviceType: {}, queryText: {}, locationDTO: {}",
//                 serviceType != null ? serviceType.getName() : "null", queryText, locationDTO);

//         List<Provider> providers;

//         if (serviceType != null) {
//             log.info("Finding providers by serviceTypeId: {} and VERIFIED status", serviceType.getId());
//             providers = providerRepository.findByServiceTypeIdAndVerificationStatus(
//                     serviceType.getId(), VerificationStatus.VERIFIED);
//         } else {
//             log.info("Finding all VERIFIED providers");
//             providers = providerRepository.findByVerificationStatus(VerificationStatus.VERIFIED);
//         }

//         log.info("Found {} providers from database", providers.size());

//         if (!providers.isEmpty()) {
//             providers.forEach(p -> log.info("   - Provider: id={}, name={}, serviceType={}",
//                     p.getId(), p.getName(),
//                     p.getServiceType() != null ? p.getServiceType().getName() : "null"));
//         }
//         Set<String> queryTokens = tokenize(queryText);
//         if (!queryTokens.isEmpty()) {
//             providers = providers.stream()
//                     .filter(p -> matchesSearchText(p, serviceType, queryTokens))
//                     .sorted((p1, p2) -> Integer.compare(scoreProvider(p2, queryTokens), scoreProvider(p1, queryTokens)))
//                     .limit(DEFAULT_PROVIDER_LIMIT * 2)
//                     .collect(Collectors.toList());
//         }

//         return providers.stream()
//                 .limit(DEFAULT_PROVIDER_LIMIT)
//                 .map(p -> toProviderSummary(p, locationDTO, radiusKm))
//                 .sorted(Comparator.comparing(ProviderSummaryResponse::getDistance,
//                         Comparator.nullsLast(Double::compareTo)))
//                 .collect(Collectors.toList());
//     }

//     private ChatResponse handleBookingAction(AiChatRequest request, User currentUser,
//             UnifiedAssistantResponse unified, ChatResponse.ChatResponseBuilder responseBuilder) {

//         if (!isConsumer(currentUser)) {
//             return responseBuilder
//                     .responseType(ChatResponseType.CLARIFICATION)
//                     .message("يرجى تسجيل الدخول كمستهلك لإنشاء حجز.")
//                     .build();
//         }

//         Long providerId = unified.providerId;
//         String providerName = unified.providerName;

//         if (providerId == null) {
//             ChatSession session = chatSessionRepository.findById(request.getSessionId()).orElse(null);
//             if (session != null && session.getLastSuggestedProviderId() != null) {
//                 providerId = session.getLastSuggestedProviderId();
//                 providerName = session.getLastSuggestedProviderName();
//                 log.info("Using provider from session: {} (ID: {})", providerName, providerId);
//             }
//         }

//         if (providerId == null && StringUtils.hasText(unified.providerName)) {
//             providerId = resolveProviderIdByName(unified.providerName);
//             log.info("Resolved provider by name from unified: {} -> ID {}", providerName, providerId);
//         }

//         if (providerId == null) {
//             return responseBuilder
//                     .responseType(ChatResponseType.CLARIFICATION)
//                     .message("ممكن توضح أي مزود تقصد؟ / Could you specify which provider you mean?")
//                     .build();
//         }

//         if (unified.requestedDate == null || unified.requestedTime == null
//                 || !StringUtils.hasText(unified.problemDescription)) {

//             List<String> missing = new ArrayList<>();
//             if (unified.requestedDate == null)
//                 missing.add("date");
//             if (unified.requestedTime == null)
//                 missing.add("time");
//             if (!StringUtils.hasText(unified.problemDescription))
//                 missing.add("problemDescription");

//             return responseBuilder
//                     .responseType(ChatResponseType.BOOKING_REDIRECT)
//                     .message("محتاج هذه المعلومات لإتمام الحجز: " + String.join(", ", missing))
//                     .build();
//         }

//         try {
//             Provider provider = providerRepository.findById(providerId)
//                     .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

//             if (provider.getSchedule() == null) {
//                 return responseBuilder
//                         .responseType(ChatResponseType.ERROR)
//                         .message("لا يمتلك المزود جدول مواعيد محدد.")
//                         .build();
//             }

//             boolean isTimeAvailable = timeSlotRepository.isTimeWithinAvailableSlot(
//                     provider.getSchedule().getId(),
//                     unified.requestedDate,
//                     unified.requestedTime);

//             if (!isTimeAvailable) {
//                 List<ScheduleResponse.TimeSlotResponse> availableSlots = providerService
//                         .getAvailableTimeSlots(providerId, unified.requestedDate);

//                 String slotMessage;
//                 if (availableSlots.isEmpty()) {
//                     slotMessage = "للأسف، لا توجد مواعيد متاحة في هذا التاريخ. الرجاء اختيار تاريخ آخر.";
//                 } else {
//                     slotMessage = "الموعد المطلوب غير متاح. اختر من المواعيد المتاحة التالية:";
//                 }

//                 return responseBuilder
//                         .responseType(ChatResponseType.AVAILABLE_SLOTS)
//                         .availableTimeSlots(availableSlots)
//                         .message(slotMessage)
//                         .build();
//             }

//             BookingResponse bookingResponse = bookingService.requestBooking(currentUser.getId(),
//                     BookingRequest.builder()
//                             .providerId(providerId)
//                             .requestedDate(unified.requestedDate)
//                             .requestedTime(unified.requestedTime)
//                             .problemDescription(unified.problemDescription)
//                             .build());

//             return responseBuilder
//                     .responseType(ChatResponseType.BOOKING_CREATED)
//                     .booking(bookingResponse)
//                     .message("تم إنشاء طلب الحجز بنجاح رقم #" + bookingResponse.getId())
//                     .build();
//         } catch (BadRequestException ex) {
//             log.warn("Booking validation failed: {}", ex.getMessage());

//             if (ex.getMessage() != null && ex.getMessage().contains("TimeSlot not available")) {
//                 List<ScheduleResponse.TimeSlotResponse> availableSlots = providerService
//                         .getAvailableTimeSlots(providerId, unified.requestedDate);

//                 String slotMessage;
//                 if (availableSlots.isEmpty()) {
//                     slotMessage = "للأسف، لا توجد مواعيد متاحة في هذا التاريخ. الرجاء اختيار تاريخ آخر.";
//                 } else {
//                     slotMessage = "الموعد المطلوب غير متاح. اختر من المواعيد المتاحة التالية:";
//                 }

//                 return responseBuilder
//                         .responseType(ChatResponseType.AVAILABLE_SLOTS)
//                         .availableTimeSlots(availableSlots)
//                         .message(slotMessage)
//                         .build();
//             }

//             return responseBuilder
//                     .responseType(ChatResponseType.ERROR)
//                     .message("خطأ في البيانات: " + ex.getMessage())
//                     .build();
//         } catch (Exception ex) {
//             log.error("Booking creation failed: {}", ex.getMessage());
//             return responseBuilder
//                     .responseType(ChatResponseType.ERROR)
//                     .message("حدث خطأ أثناء إنشاء الحجز. حاول مرة أخرى.")
//                     .build();
//         }
//     }

//     private ChatResponse handleAvailabilityAction(UnifiedAssistantResponse unified, ChatSession session,
//             ChatResponse.ChatResponseBuilder responseBuilder) {

//         Long providerId = unified.providerId;

//         if (providerId == null && StringUtils.hasText(unified.providerName)) {
//             providerId = resolveProviderIdByName(unified.providerName);
//         }

//         if (providerId == null && session != null && session.getLastSuggestedProviderId() != null) {
//             providerId = session.getLastSuggestedProviderId();
//             if (!StringUtils.hasText(unified.providerName)) {
//                 unified.providerName = session.getLastSuggestedProviderName();
//             }
//             log.debug("Using last suggested provider context for availability check: {}",
//                     session.getLastSuggestedProviderName());
//         }

//         if (providerId == null) {
//             return responseBuilder
//                     .responseType(ChatResponseType.CLARIFICATION)
//                     .message("محتاج اسم المزود عشان أقدر أوريك مواعيده. ممكن تقولي اسمه بالكامل؟")
//                     .build();
//         }

//         LocalDate targetDate = unified.requestedDate;
//         List<ScheduleResponse.TimeSlotResponse> slots;

//         if (targetDate == null) {
//             log.info("No date specified for provider {}, fetching upcoming availability for next 7 days", providerId);
//             slots = providerService.getAvailableTimeSlotsForDateRange(providerId,
//                     LocalDate.now().plusDays(1),
//                     LocalDate.now().plusDays(7));

//             if (slots.isEmpty()) {
//                 return responseBuilder
//                         .responseType(ChatResponseType.AVAILABLE_SLOTS)
//                         .availableTimeSlots(slots)
//                         .message("عذراً، لا توجد مواعيد متاحة حالياً للأيام القادمة. حاول مرة أخرى لاحقاً.")
//                         .build();
//             }

//             return responseBuilder
//                     .responseType(ChatResponseType.AVAILABLE_SLOTS)
//                     .availableTimeSlots(slots)
//                     .message(formatUpcomingAvailabilityMessage(unified.providerName, slots))
//                     .build();
//         } else {
//             slots = providerService.getAvailableTimeSlots(providerId, targetDate);

//             return responseBuilder
//                     .responseType(ChatResponseType.AVAILABLE_SLOTS)
//                     .availableTimeSlots(slots)
//                     .message(formatAvailabilityMessage(providerId, targetDate, slots))
//                     .build();
//         }
//     }

//     private String formatUpcomingAvailabilityMessage(String providerName,
//             List<ScheduleResponse.TimeSlotResponse> slots) {
//         if (slots == null || slots.isEmpty()) {
//             return "لا توجد مواعيد متاحة لـ " + (providerName != null ? providerName : "هذا المزود")
//                     + " في الأيام القادمة.";
//         }

//         Map<LocalDate, List<ScheduleResponse.TimeSlotResponse>> slotsByDate = slots.stream()
//                 .collect(Collectors.groupingBy(slot -> parseDateSafe(slot.getDate())));

//         slotsByDate.remove(null);

//         StringBuilder message = new StringBuilder();
//         message.append("المواعيد المتاحة لـ ").append(providerName != null ? providerName : "المزود")
//                 .append(" خلال الأيام القادمة:\n\n");

//         int dateCount = 0;
//         for (Map.Entry<LocalDate, List<ScheduleResponse.TimeSlotResponse>> entry : slotsByDate.entrySet()) {
//             if (dateCount++ >= 7)
//                 break;
//             message.append("📅 ").append(DATE_FORMAT.format(entry.getKey())).append(":\n");
//             String times = entry.getValue().stream()
//                     .map(slot -> TIME_FORMAT.format(slot.getStartTime()) + " - "
//                             + TIME_FORMAT.format(slot.getEndTime()))
//                     .collect(Collectors.joining(", "));
//             message.append("   🕐 ").append(times).append("\n");
//         }

//         return message.toString();
//     }

//     private LocationDTO resolveSearchLocation(User currentUser, AiChatRequest request) {
//         if (isConsumer(currentUser)) {
//             Optional<Consumer> consumer = consumerRepository.findById(currentUser.getId());
//             if (consumer.isPresent() && consumer.get().getLocation() != null) {
//                 return locationMapper.toDto(consumer.get().getLocation());
//             }
//         }
//         return request.getLocation();
//     }

//     private Long resolveProviderIdByName(String providerName) {
//         if (!StringUtils.hasText(providerName))
//             return null;

//         String targetName = normalize(providerName);
//         List<Provider> providers = providerRepository.findAll();

//         Optional<Provider> exact = providers.stream()
//                 .filter(p -> normalize(p.getName()).equals(targetName))
//                 .findFirst();
//         if (exact.isPresent())
//             return exact.get().getId();

//         return providers.stream()
//                 .filter(p -> normalize(p.getName()).contains(targetName) || targetName.contains(normalize(p.getName())))
//                 .findFirst()
//                 .map(Provider::getId)
//                 .orElse(null);
//     }

//     private int scoreProvider(Provider provider, Set<String> queryTokens) {
//         if (queryTokens == null || queryTokens.isEmpty())
//             return 0;

//         String providerText = normalize(
//                 (provider.getName() != null ? provider.getName() : "") + " " +
//                         (provider.getBio() != null ? provider.getBio() : "") + " " +
//                         (provider.getServiceType() != null ? provider.getServiceType().getName() : "") + " " +
//                         (provider.getServiceType() != null ? provider.getServiceType().getNameAr() : ""));

//         return (int) queryTokens.stream().filter(providerText::contains).count();
//     }

//     private boolean matchesSearchText(Provider provider, ServiceType serviceType, Set<String> queryTokens) {
//         if (queryTokens == null || queryTokens.isEmpty())
//             return true;
//         return scoreProvider(provider, queryTokens) > 0;
//     }

//     private ProviderSummaryResponse toProviderSummary(Provider provider, LocationDTO locationDTO, Integer radiusKm) {
//         ProviderSummaryResponse response = providerMapper.toProviderSummaryResponse(provider);
//         if (locationDTO != null && provider.getLocation() != null) {
//             try {
//                 Location searchLocation = locationMapper.toEntity(locationDTO);
//                 if (searchLocation.getLatitude() != null && searchLocation.getLongitude() != null) {
//                     double distance = provider.getLocation().calculateDistance(searchLocation);
//                     response.setDistance(distance);
//                 }
//             } catch (Exception e) {
//                 log.debug("Could not calculate distance for provider {}: {}", provider.getId(), e.getMessage());
//             }
//         }
//         return response;
//     }

//     private Set<String> tokenize(String text) {
//         String normalized = normalize(text);
//         if (!StringUtils.hasText(normalized))
//             return new HashSet<>();

//         return Arrays.stream(normalized.split("\\s+"))
//                 .filter(token -> token.length() >= 3)
//                 .collect(Collectors.toCollection(HashSet::new));
//     }

//     private String buildProviderSummaryMessage(List<ProviderSummaryResponse> providers, ServiceType serviceType) {
//         String header = serviceType != null
//                 ? "لقد وجدت " + providers.size() + " مقدم خدمة موثوق لـ " + serviceType.getName() + ":"
//                 : "لقد وجدت مقدمي الخدمة الموثوقين:";

//         String details = providers.stream()
//                 .limit(3)
//                 .map(p -> p.getName() + (p.getAverageRating() != null ? " (" + p.getAverageRating() + "/5)" : ""))
//                 .collect(Collectors.joining(", "));

//         return details.isBlank() ? header : header + " " + details;
//     }

//     private String formatAvailabilityMessage(Long providerId, LocalDate date,
//             List<ScheduleResponse.TimeSlotResponse> slots) {
//         if (slots == null || slots.isEmpty()) {
//             return "لم يتم العثور على مواعيد متاحة للمزود " + providerId + " في تاريخ " + DATE_FORMAT.format(date);
//         }

//         String slotSummary = slots.stream()
//                 .limit(5)
//                 .map(slot -> TIME_FORMAT.format(slot.getStartTime()) + " - " + TIME_FORMAT.format(slot.getEndTime()))
//                 .collect(Collectors.joining(", "));

//         return "المواعيد المتاحة للمزود " + providerId + " في تاريخ " + DATE_FORMAT.format(date) + ": " + slotSummary;
//     }

//     private void saveUserMessage(ChatSession session, Long userId, String content) {
//         ChatMessage userMessage = ChatMessage.builder()
//                 .chatSession(session)
//                 .senderId(userId != null ? userId : 0L)
//                 .senderRole(MessageRole.USER)
//                 .content(content)
//                 .type(MessageType.TEXT)
//                 .isRead(true)
//                 .build();
//         chatMessageRepository.save(userMessage);
//     }

//     private void saveAssistantMessage(ChatSession session, ChatResponse response) {
//         String content = response != null ? response.getMessage() : null;

//         ChatMessage assistantMessage = ChatMessage.builder()
//                 .chatSession(session)
//                 .senderId(0L)
//                 .senderRole(MessageRole.ASSISTANT)
//                 .content(content)
//                 .type(MessageType.TEXT)
//                 .responseType(response != null ? response.getResponseType() : ChatResponseType.TEXT)
//                 .providersPayload(serializeProviders(response != null ? response.getProviders() : null))
//                 .availableSlotsPayload(
//                         serializeAvailableSlots(response != null ? response.getAvailableTimeSlots() : null))
//                 .isRead(true)
//                 .build();
//         chatMessageRepository.save(assistantMessage);

//         if (response != null && response.getResponseType() == ChatResponseType.PROVIDER_LIST
//                 && response.getProviders() != null && !response.getProviders().isEmpty()) {
//             ProviderSummaryResponse primary = response.getProviders().get(0);
//             session.setLastSuggestedProviderId(primary.getId());
//             session.setLastSuggestedProviderName(primary.getName());
//             chatSessionRepository.save(session);
//         }
//     }

//     private void applySessionProviderContext(UnifiedAssistantResponse unified, ChatSession session) {
//         if (unified == null || session == null) {
//             return;
//         }

//         if ((unified.action == Action.CHECK_AVAILABILITY || unified.action == Action.CREATE_BOOKING)
//                 && unified.providerId == null
//                 && !StringUtils.hasText(unified.providerName)
//                 && session.getLastSuggestedProviderId() != null) {

//             unified.providerId = session.getLastSuggestedProviderId();
//             unified.providerName = session.getLastSuggestedProviderName();
//             log.info("Applied session provider context for {}: {} (ID: {})",
//                     unified.action, unified.providerName, unified.providerId);
//         }
//     }

//     private String serializeProviders(List<ProviderSummaryResponse> providers) {
//         if (providers == null) {
//             return "[]";
//         }
//         try {
//             return objectMapper.writeValueAsString(providers);
//         } catch (Exception ex) {
//             log.debug("Failed to serialize providers payload: {}", ex.getMessage());
//             return null;
//         }
//     }

//     private String serializeAvailableSlots(List<ScheduleResponse.TimeSlotResponse> slots) {
//         if (slots == null) {
//             return "[]";
//         }
//         try {
//             return objectMapper.writeValueAsString(slots);
//         } catch (Exception ex) {
//             log.debug("Failed to serialize available slots payload: {}", ex.getMessage());
//             return null;
//         }
//     }

//     private List<ProviderSummaryResponse> parseProvidersPayload(String payload) {
//         if (!StringUtils.hasText(payload)) {
//             return List.of();
//         }
//         try {
//             return objectMapper.readValue(payload,
//                     objectMapper.getTypeFactory().constructCollectionType(List.class, ProviderSummaryResponse.class));
//         } catch (Exception ex) {
//             log.debug("Failed to parse providers payload: {}", ex.getMessage());
//             return List.of();
//         }
//     }

//     private List<ScheduleResponse.TimeSlotResponse> parseAvailableSlotsPayload(String payload) {
//         if (!StringUtils.hasText(payload)) {
//             return List.of();
//         }
//         try {
//             return objectMapper.readValue(payload,
//                     objectMapper.getTypeFactory().constructCollectionType(List.class,
//                             ScheduleResponse.TimeSlotResponse.class));
//         } catch (Exception ex) {
//             log.debug("Failed to parse available slots payload: {}", ex.getMessage());
//             return List.of();
//         }
//     }

//     private String extractJson(String text) {
//         int start = text.indexOf('{');
//         int end = text.lastIndexOf('}');
//         if (start < 0 || end <= start)
//             return null;
//         return text.substring(start, end + 1);
//     }

//     private LocalDate parseDateSafe(String dateText) {
//         if (!StringUtils.hasText(dateText))
//             return null;
//         try {
//             return LocalDate.parse(dateText.trim(), DATE_FORMAT);
//         } catch (Exception ex) {
//             return null;
//         }
//     }

//     private LocalTime parseTimeSafe(String timeText) {
//         if (!StringUtils.hasText(timeText))
//             return null;
//         try {
//             return LocalTime.parse(timeText.trim(), TIME_FORMAT);
//         } catch (Exception ex) {
//             return null;
//         }
//     }

//     private String detectLanguage(String text) {
//         if (text == null)
//             return "en";
//         return text.matches(".*[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF].*") ? "ar" : "en";
//     }

//     private boolean isConsumer(User currentUser) {
//         return currentUser != null && currentUser.getRole() == UserType.CONSUMER;
//     }

//     private String normalize(String value) {
//         if (!StringUtils.hasText(value))
//             return "";
//         return NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
//     }

//     private boolean containsAny(String normalizedText, String... keywords) {
//         for (String keyword : keywords) {
//             if (normalizedText.contains(normalize(keyword)))
//                 return true;
//         }
//         return false;
//     }

//     private String buildSolutionSuggestionReply(String normalizedMessage) {
//         if (containsAny(normalizedMessage, "كهرب", "electric", "power", "نور", "لمبة", "فيشة", "breaker", "قاطع")) {
//             return "جرب أولاً: 1) تأكد إن القاطع الرئيسي شغال، 2) راجع الفيشة والريموت، 3) افصل الجهاز 5 دقائق ورجعه تاني، 4) لو في شرر أو سخونة، افصل الكهرباء فوراً. لو المشكلة مستمرة أقدر أدورلك على كهربائي.";
//         }

//         if (containsAny(normalizedMessage, "تكييف", "ac", "air", "cool", "برد")) {
//             return "جرب أولاً: 1) تأكد من وضع التبريد والحرارة، 2) نظف الفلتر، 3) راجع البطاريات والريموت، 4) افصل التكييف 5 دقائق ثم شغله. لو ما اتحلّش أقدر أدورلك على فني تكييف.";
//         }

//         if (containsAny(normalizedMessage, "مياه", "water", "تسريب", "بيسرب", "حنفية", "ماسورة", "صرف")) {
//             return "جرب أولاً: 1) اقفل مصدر المياه، 2) راقب مكان التسريب، 3) تأكد إن الوصلات مش مفكوكة، 4) لو في كسر واضح أو التسريب كبير أقدر أدورلك على سباك.";
//         }

//         if (containsAny(normalizedMessage, "باب", "قفل", "lock", "مفتاح", "handle")) {
//             return "جرب أولاً: 1) تأكد إن الباب مش عالق، 2) استخدم زيت خفيف للمفصلة لو بتحتك، 3) راجع المفتاح/القفل، 4) لو القفل مكسور أقدر أدورلك على فني.";
//         }

//         return "ممكن نبدأ بخطوات بسيطة: 1) تأكد من مصدر المشكلة، 2) افصل/شغّل الجهاز لو ده آمن، 3) راقب إذا كان في جزء مفكوك أو توقف مفاجئ. لو المشكلة مستمرة أقدر أدورلك على مختص مناسب.";
//     }

//     // ===== INNER CLASSES =====

//     @Data
//     private static class UnifiedAssistantResponse {
//         private Action action;
//         private Intent intent;
//         private String reply;
//         private Long providerId;
//         private String providerName;
//         private String serviceTypeName;
//         private Long serviceTypeId;
//         private LocalDate requestedDate;
//         private LocalTime requestedTime;
//         private String problemDescription;
//         private Integer searchRadiusKm;
//         private boolean needsClarification;
//         private List<String> missingFields;

//         boolean isValid() {
//             return action != null;
//         }
//     }

//     private enum Intent {
//         GENERAL, SEARCH_PROVIDERS, SUGGEST_SOLUTIONS, GET_AVAILABILITY, CREATE_BOOKING, GET_PROVIDER_DETAILS,
//         CLARIFICATION
//     }

//     private enum Action {
//         GENERAL, SEARCH_PROVIDERS, SUGGEST_SOLUTIONS, CHECK_AVAILABILITY, CREATE_BOOKING, GET_PROVIDER_DETAILS,
//         ASK_CLARIFICATION
//     }

//     @Data
//     private static class McpToolCallResponse {
//         private String tool;
//         private Map<String, Object> arguments;
//         private boolean needsClarification;
//         private List<String> missingFields;
//         private String reply;
//     }

//     private static class CachedValue<T> {
//         final T value;
//         final Instant expiresAt;

//         CachedValue(T value, Duration ttl) {
//             this.value = value;
//             this.expiresAt = Instant.now().plus(ttl);
//         }

//         boolean isExpired() {
//             return Instant.now().isAfter(expiresAt);
//         }
//     }
// }