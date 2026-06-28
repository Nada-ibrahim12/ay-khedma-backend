package com.aykhedma.service;

import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.model.chat.ChatMessage;
import com.aykhedma.model.chat.ChatSession;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.mcp.server.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.aykhedma.repository.*;
import com.aykhedma.model.chat.ChatResponseType;
import com.aykhedma.model.booking.BookingStatus;
import com.fasterxml.jackson.databind.JsonNode;
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
public class AiAssistantMcpService {

    private final AiAssistantServiceImpl baseService;
    private final GeminiClient geminiClient;
    private final SpeechToTextService speechToTextService;
    private final McpServer mcpServer;
    private final ObjectMapper objectMapper;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    // ===== MAIN MCP CHAT =====
    
    public ChatResponse chatWithMcp(AiChatRequest request, User currentUser) {
        String userMessage = request.getMessage();
        boolean isVoiceNote = request.getVoiceNote() != null && !request.getVoiceNote().isEmpty();

        if (isVoiceNote) {
            try {
                String transcribedText = speechToTextService.transcribeAudio(request.getVoiceNote());
                if (StringUtils.hasText(transcribedText)) {
                    userMessage = transcribedText;
                    request.setMessage(userMessage);
                    log.info("Voice transcribed for MCP: {}", userMessage);
                } else {
                    Long userId = currentUser != null ? currentUser.getId() : null;
                    ChatSession session = baseService.resolveSession(request, userId);
                    baseService.saveUserMessage(session, userId != null ? userId : 0L, "[Voice note - transcription failed]");
                    
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
                log.error("Voice transcription error in MCP", e);
                if (!StringUtils.hasText(userMessage)) {
                    ChatSession session = baseService.resolveSession(request, currentUser != null ? currentUser.getId() : null);
                    return ChatResponse.builder()
                            .sessionId(session.getSessionId())
                            .timestamp(LocalDateTime.now())
                            .message("عذراً، حدث خطأ أثناء معالجة الصوت. حاول مرة أخرى.")
                            .responseType(ChatResponseType.ERROR)
                            .detectedLanguage("ar")
                            .build();
                }
            }
        }

        if (!StringUtils.hasText(userMessage)) {
            throw new BadRequestException("Message or voice note is required");
        }

        log.info("Processing chat with MCP - message: {}", request.getMessage());

        try {
            Long userId = currentUser != null ? currentUser.getId() : null;
            ChatSession session = baseService.resolveSession(request, userId);

            List<ChatMessage> recentHistory = baseService.getRecentHistory(
                    chatMessageRepository.findByChatSessionSessionIdOrderByTimestampAsc(request.getSessionId()),
                    AiAssistantServiceImpl.MAX_HISTORY_TURNS);

            List<Map<String, Object>> toolSchemas = mcpServer.getToolSchemas();
            log.info("Available MCP tools: {}", toolSchemas.size());

            McpToolCallResponse toolResponse = getMcpToolCallResponse(request, currentUser, recentHistory, toolSchemas);

            if (toolResponse == null) {
                log.warn("No tool response from Gemini, falling back to existing implementation");
                return baseService.oldService.chatWithExisting(request, currentUser);
            }

            log.info("Gemini selected tool: {} with args: {}", toolResponse.tool, toolResponse.arguments);

            if (toolResponse.needsClarification) {
                String reply = toolResponse.reply != null ? toolResponse.reply
                        : "محتاج هذه المعلومات لإتمام طلبك: " + String.join(", ", toolResponse.missingFields);

                baseService.saveUserMessage(session, userId != null ? userId : 0L, request.getMessage());

                ChatResponse clarificationResponse = ChatResponse.builder()
                        .sessionId(request.getSessionId())
                        .message(reply)
                        .timestamp(LocalDateTime.now())
                        .detectedLanguage(baseService.detectLanguage(request.getMessage()))
                        .responseType(ChatResponseType.CLARIFICATION)
                        .build();
                baseService.saveAssistantMessage(session, clarificationResponse);
                return clarificationResponse;
            }

            String toolName = toolResponse.tool;
            if (!StringUtils.hasText(toolName)) {
                log.warn("No tool name in response");
                return baseService.oldService.chatWithExisting(request, currentUser);
            }

            Map<String, Object> arguments = toolResponse.arguments != null ? toolResponse.arguments : new HashMap<>();

            if ("search_providers".equals(toolName)) {
                double userLatitude = 0.0;
                double userLongitude = 0.0;

                if (currentUser != null && currentUser.getRole() == UserType.CONSUMER) {
                    if (currentUser instanceof Consumer) {
                        Consumer consumer = (Consumer) currentUser;
                        if (consumer.getLocation() != null) {
                            userLatitude = consumer.getLocation().getLatitude();
                            userLongitude = consumer.getLocation().getLongitude();
                            log.info("Using REAL consumer location: {}, {}", userLatitude, userLongitude);
                        }
                    }
                }

                arguments.put("latitude", userLatitude);
                arguments.put("longitude", userLongitude);
                log.info("Overrode location to REAL user coordinates: {}, {}", userLatitude, userLongitude);
            }

            if ("create_booking".equals(toolName)) {
                Long consumerId = null;
                if (currentUser != null && currentUser.getRole() == UserType.CONSUMER) {
                    consumerId = currentUser.getId();
                    log.info("Injecting consumer ID for booking: {}", consumerId);
                }
                if (consumerId != null) {
                    arguments.put("consumerId", consumerId);
                } else {
                    log.warn("No consumer ID available for booking");
                    ChatResponse errorResponse = ChatResponse.builder()
                            .sessionId(request.getSessionId())
                            .message("يجب تسجيل الدخول كمستهلك لحجز خدمة. يرجى تسجيل الدخول والمحاولة مرة أخرى.")
                            .timestamp(LocalDateTime.now())
                            .detectedLanguage("ar")
                            .responseType(ChatResponseType.ERROR)
                            .build();
                    baseService.saveUserMessage(session, userId != null ? userId : 0L, request.getMessage());
                    baseService.saveAssistantMessage(session, errorResponse);
                    return errorResponse;
                }
            }
            
            Map<String, Object> mcpRequest = buildMcpRequest(toolName, arguments);
            log.info("MCP Request: {}", mcpRequest);

            Map<String, Object> mcpResponse = mcpServer.handleRequest(mcpRequest);
            log.info("MCP Response: {}", mcpResponse);

            ChatResponse chatResponse = buildResponseFromMcpResult(toolName, mcpResponse, request, toolResponse.reply);

            baseService.saveUserMessage(session, userId != null ? userId : 0L, request.getMessage());
            baseService.saveAssistantMessage(session, chatResponse);

            return chatResponse;
        } catch (Exception e) {
            log.error("MCP chat failed: {}", e.getMessage(), e);
            return baseService.oldService.chatWithExisting(request, currentUser);
        }
    }

    // ===== TOOL CALLING =====
    
    private McpToolCallResponse getMcpToolCallResponse(AiChatRequest request, User currentUser,
            List<ChatMessage> history, List<Map<String, Object>> toolSchemas) {
        if (!geminiClient.isEnabled()) {
            return null;
        }

        String systemPrompt = buildMcpSystemPrompt(currentUser, toolSchemas);
        String conversationContext = baseService.buildConversationContext(history);

        String userMessage = conversationContext + "\n\nCurrent User Request: " + request.getMessage();

        List<GeminiClient.ConversationTurn> turns = baseService.toConversationTurns(history);
        turns.add(new GeminiClient.ConversationTurn("user", userMessage));

        String modelResponse = geminiClient.generateJson(turns, systemPrompt);
        return parseMcpToolCallResponse(modelResponse);
    }

    private String buildMcpSystemPrompt(User currentUser, List<Map<String, Object>> tools) {
        String userRole = (currentUser != null && currentUser.getRole() != null)
                ? currentUser.getRole().name()
                : "anonymous";

        double userLatitude = 0.0;
        double userLongitude = 0.0;
        String userLocationInfo = "";

        if (currentUser != null) {
            if (currentUser.getRole() == UserType.CONSUMER) {
                if (currentUser instanceof Consumer) {
                    Consumer consumer = (Consumer) currentUser;
                    if (consumer.getLocation() != null) {
                        userLatitude = consumer.getLocation().getLatitude();
                        userLongitude = consumer.getLocation().getLongitude();
                        userLocationInfo = String.format("User location (from consumer): (%.4f, %.4f)", userLatitude,
                                userLongitude);
                        log.info("Using consumer location: {}, {}", userLatitude, userLongitude);
                    }
                }
            } else {
                log.info("User is not a consumer");
            }
        }

        StringBuilder toolsDesc = new StringBuilder();
        toolsDesc.append("## AVAILABLE TOOLS:\n\n");
        for (Map<String, Object> tool : tools) {
            String name = (String) tool.get("name");
            String description = (String) tool.get("description");
            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) tool.get("inputSchema");
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");

            toolsDesc.append("### Tool: ").append(name).append("\n");
            toolsDesc.append("Description: ").append(description).append("\n");
            toolsDesc.append("Parameters:\n");
            if (properties != null) {
                List<String> required = (List<String>) inputSchema.get("required");
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                    boolean isRequired = required != null && required.contains(entry.getKey());
                    toolsDesc.append("  - ").append(entry.getKey())
                            .append(" (").append(prop.get("type")).append(")")
                            .append(isRequired ? " [REQUIRED]" : " [OPTIONAL]")
                            .append(": ").append(prop.get("description")).append("\n");
                }
            }
            toolsDesc.append("\n");
        }

        List<ServiceType> allServices = baseService.getCachedServiceTypes();
        List<ServiceCategory> allCategories = baseService.getCachedCategories();

        String categoriesStr = allCategories.stream()
                .map(cat -> "- Category: " + cat.getName() + " (ID: " + cat.getId() + " description: "
                        + cat.getDescription() + ")")
                .collect(Collectors.joining("\n"));

        String servicesStr = allServices.stream()
                .map(st -> "- Service Type: " + st.getName() + " (ID: " + st.getId() + ", Category ID: "
                        + (st.getCategory() != null ? st.getCategory().getId() : "null") + ", description: "
                        + st.getDescription() + ")")
                .collect(Collectors.joining("\n"));

        return """
                You are Ay Khedma AI Assistant - a comprehensive service marketplace assistant.

                 ## USER LOCATION INFORMATION:
                %s
                - Latitude: %.4f
                - Longitude: %.4f
                - ALWAYS use these coordinates when calling search_providers
                - Do NOT ask the user for their location - you already have it!

                ## YOUR TASK:
                Analyze the user's message and decide which tool to call.

                """
                + toolsDesc.toString()
                + """

                        - Today Date is %s

                        ## DATE AND TIME HANDLING

                        ### Input Formats
                        - **Dates**: "30/6" or "30/6/2026" → June 30, 2026 | "15-6" → June 15 | "يوم 30" → 30th of current month
                        - **Days**: "الجمعة", "السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس" → Next [day]
                        - **Relative**: "بعد اسبوع" → +7 days | "بعد 3 ايام" → +3 days
                        - **Times**: "7" or "7 صباحاً" → 07:00 | "7 مساءً" → 19:00 | "الظهر" → 12:00 | "العصر" → 16:00 | "المغرب" → 18:00 | "العشاء" → 20:00

                        ### Day Rules (CRITICAL)
                        - **"الجمعة"/Friday** → FIRST Friday AFTER today (if today IS Friday → next Friday, +7 days)
                        - **Other days** → NEXT occurrence of that day
                        - **"الجمعة الجاي"** → Friday of next week (not this coming Friday)

                        ### Day Mapping
                        - الأحد → Sunday | الاثنين → Monday | الثلاثاء → Tuesday | الأربعاء → Wednesday | الخميس → Thursday | الجمعة → Friday | السبت → Saturday

                        ### Date Parsing Rules
                        1. **Day+Month only** (e.g., "30/6"): Use current year (2026); if date passed → ADD 1 YEAR
                        2. **Day only** (e.g., "يوم 30"): Use current month; if day passed → ADD 1 MONTH
                        3. **Relative**: "بعد X يوم/اسبوع" → Today + X days
                        4. **Output**: Always `"yyyy-MM-dd"` (e.g., "2026-06-30")

                        ### Time Parsing Rules
                        1. **Number 1-12** → AM (morning) | **13-24** → 24-hour format
                        2. **Arabic references**: "صباحاً/صبح" → AM | "مساءً/مغرب" → PM (+12) | "ظهراً" → 12:00 | "العصر" → 16:00 | "المغرب" → 18:00 | "العشاء" → 20:00
                        3. **Output**: Always `"HH:mm"` (e.g., "07:00", "19:00")

                        ### Examples
                        1. User: "الجمعة الساعة 10" (Today Monday) → date="2026-07-03", time="10:00"
                        2. User: "السبت الجاي الساعة 4 العصر" (Today Monday) → date="2026-07-04", time="16:00"
                        3. User: "يوم 30/6 الساعة 7" (Today June 28) → date="2026-06-30", time="07:00"
                        4. User: "يوم 15/6 الساعة 8" (Today June 28, past) → date="2027-06-15", time="08:00"

                                you need to analyze service categories and service types from the database
                                """
                + categoriesStr
                + "\n\n"
                + servicesStr
                + """

                        ## INSTRUCTIONS:
                        1. Analyze user's problem, there is no unauthenticated user can access the chatbot
                        2. Choose the most appropriate tool based on the user's request
                        3. Extract all required parameters from the user's message
                        4. Choose the most appropriate services can solve the user's issue
                        5. If required parameters are missing, set needsClarification=true and list missing fields
                        6. For dates, always use format "yyyy-MM-dd"
                        7. For times, always use format "HH:mm" (24-hour format)
                        8. For search_providers, ALWAYS include the user's latitude and longitude from above

                        ## OUTPUT FORMAT:
                        Return ONLY valid JSON. No extra text, no explanation, no markdown.

                        {
                          "tool": "tool_name",
                          "arguments": {
                            "param1": "value1",
                            "param2": "value2"
                          },
                          "needsClarification": false,
                          "missingFields": ["field1", "field2"] or null,
                          "reply": "natural language response to user"
                        }

                        ## TOOL USAGE RULES:

                        ### search_providers
                        - Use when user wants to FIND or SEARCH for providers
                        - Extract serviceTypes from understanding of user message (e.g., "electrician", "plumbing", "AC Repair")
                        - Use consumer location to calculate distance between them
                        - IMPORTANT: serviceTypes must be a JSON ARRAY (list), not a string!
                        - Example: "محتاج فني تكييف" → tool="search_providers", serviceTypes=["AC Repair", "HVAC", "Air Conditioner Maintenance"], latitude=%.4f, longitude=%.4f

                        ### check_availability
                        - Use when user wants to SEE available time slots for a SPECIFIC provider
                        - Must have providerId or providerName
                        - Date is optional - if not provided, show next 7 days
                        - Example: "وريني مواعيده / وريني مواعيد احمد ابراهيم" → tool="check_availability", providerId=20

                        ### create_booking
                        - Use when user wants to BOOK or RESERVE an appointment
                        - Required: providerId, date, time, problemDescription
                        - If any required field is missing, set needsClarification=true
                        - Apply the date/time/day-of-week parsing rules above
                        - Example: "احجز معاه/ مع احمد الساعة 4 يوم السبت" → tool="create_booking"

                        ### get_provider_details
                        - Use when user wants DETAILED information about a provider
                        - Must have providerId or providerName
                        - Example: "عايز معلومات عنه/عن ياسر عبده" → tool="get_provider_details"

                        ## CONTEXT:
                        Current user role: """
                + userRole
                + """

                        - If role = "anonymous" or role = "null" or role = "ANONYMOUS" , user is NOT logged in
                        - The user must be authenticated to access any tool

                        ## REMEMBER:
                        - Return ONLY valid JSON
                        - Dates: ALWAYS use "yyyy-MM-dd" format
                        - Times: ALWAYS use "HH:mm" 24-hour format
                        - If a date is in the past, add 1 year
                        - Days of the week = next occurrence of that day
                        - No explanation outside JSON
                        - No markdown formatting around JSON
                        - Always use double quotes for JSON properties
                        - For Arabic user messages, respond in Arabic in the reply field
                        - For English user messages, respond in English
                        """.formatted(userLocationInfo, userLatitude, userLongitude,
                        LocalDate.now().toString(), userLatitude, userLongitude);
    }

    // ===== PARSING =====
    
    private McpToolCallResponse parseMcpToolCallResponse(String modelResponse) {
        if (!StringUtils.hasText(modelResponse)) {
            return null;
        }

        try {
            String json = baseService.extractJson(modelResponse);
            if (!StringUtils.hasText(json)) {
                return null;
            }

            JsonNode node = objectMapper.readTree(json);

            McpToolCallResponse response = new McpToolCallResponse();
            response.tool = node.path("tool").asText(null);
            response.needsClarification = node.path("needsClarification").asBoolean(false);
            response.reply = node.path("reply").asText(null);

            JsonNode argsNode = node.path("arguments");
            if (argsNode.isObject()) {
                response.arguments = new HashMap<>();
                argsNode.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    String key = entry.getKey();
                    if ("serviceTypes".equals(key)) {
                        try {
                            if (value.isArray()) {
                                List<String> serviceList = new ArrayList<>();
                                for (JsonNode item : value) {
                                    serviceList.add(item.asText());
                                }
                                response.arguments.put(key, serviceList);
                            } else if (value.isTextual()) {
                                String strValue = value.asText().trim();
                                if (strValue.startsWith("[") && strValue.endsWith("]")) {
                                    try {
                                        JsonNode parsedArray = objectMapper.readTree(strValue);
                                        if (parsedArray.isArray()) {
                                            List<String> serviceList = new ArrayList<>();
                                            for (JsonNode item : parsedArray) {
                                                serviceList.add(item.asText());
                                            }
                                            response.arguments.put(key, serviceList);
                                            log.info("Parsed serviceTypes from string to list: {}", serviceList);
                                            return;
                                        }
                                    } catch (Exception e) {
                                        log.warn("Failed to parse serviceTypes as JSON array: {}", e.getMessage());
                                    }
                                }
                                response.arguments.put(key, List.of(strValue));
                            } else {
                                response.arguments.put(key, value.toString());
                            }
                        } catch (Exception e) {
                            log.warn("Error processing serviceTypes: {}", e.getMessage());
                            response.arguments.put(key, value.toString());
                        }
                    } else {
                        if (value.isTextual()) {
                            response.arguments.put(key, value.asText());
                        } else if (value.isNumber()) {
                            response.arguments.put(key, value.asDouble());
                        } else if (value.isBoolean()) {
                            response.arguments.put(key, value.asBoolean());
                        } else {
                            response.arguments.put(key, value.toString());
                        }
                    }
                });
            }

            if (node.has("missingFields") && node.path("missingFields").isArray()) {
                response.missingFields = new ArrayList<>();
                for (JsonNode field : node.path("missingFields")) {
                    response.missingFields.add(field.asText());
                }
            }

            return response;

        } catch (Exception ex) {
            log.debug("Failed to parse MCP tool call response: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildMcpRequest(String toolName, Map<String, Object> arguments) {
        Map<String, Object> mcpRequest = new HashMap<>();
        mcpRequest.put("jsonrpc", "2.0");
        mcpRequest.put("method", "tools/call");
        mcpRequest.put("id", UUID.randomUUID().toString());

        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments);
        mcpRequest.put("params", params);

        return mcpRequest;
    }

    // ===== RESPONSE BUILDING =====
    
    private ChatResponse buildResponseFromMcpResult(String toolName, Map<String, Object> mcpResponse,
            AiChatRequest request, String defaultReply) {

        String reply = defaultReply != null ? defaultReply : "تم تنفيذ طلبك بنجاح.";

        return switch (toolName) {
            case "search_providers" -> handleSearchProvidersResponse(mcpResponse, request, reply);
            case "check_availability" -> handleCheckAvailabilityResponse(mcpResponse, request);
            case "create_booking" -> handleCreateBookingResponse(mcpResponse, request);
            case "get_provider_details" -> handleGetProviderDetailsResponse(mcpResponse, request);
            default -> ChatResponse.builder()
                    .sessionId(request.getSessionId())
                    .message(reply)
                    .timestamp(LocalDateTime.now())
                    .detectedLanguage(baseService.detectLanguage(request.getMessage()))
                    .responseType(ChatResponseType.TEXT)
                    .build();
        };
    }

    private ChatResponse handleSearchProvidersResponse(Map<String, Object> mcpResponse,
            AiChatRequest request, String defaultReply) {
        List<ProviderSummaryResponse> providers = parseMcpResponse(mcpResponse);
        String reply = defaultReply;

        if (providers.isEmpty()) {
            reply = "عذراً، لم أجد أي مقدمي خدمة متاحين في منطقتك حالياً. جرب توسيع نطاق البحث أو حاول مرة أخرى لاحقاً.";
        } else {
            ServiceType serviceType = null;
            if (providers.get(0).getServiceType() != null) {
                serviceType = baseService.getCachedServiceTypes().stream()
                        .filter(st -> st.getName().equalsIgnoreCase(providers.get(0).getServiceType()))
                        .findFirst()
                        .orElse(null);
            }
            if (serviceType == null && providers.get(0).getServiceTypeAr() != null) {
                serviceType = baseService.getCachedServiceTypes().stream()
                        .filter(st -> st.getNameAr().equalsIgnoreCase(providers.get(0).getServiceTypeAr()))
                        .findFirst()
                        .orElse(null);
            }
            reply = baseService.buildSmartReply(providers, serviceType, request.getMessage());
        }

        return ChatResponse.builder()
                .sessionId(request.getSessionId())
                .message(reply)
                .timestamp(LocalDateTime.now())
                .detectedLanguage(baseService.detectLanguage(request.getMessage()))
                .responseType(ChatResponseType.PROVIDER_LIST)
                .providers(providers)
                .build();
    }

    private ChatResponse handleCheckAvailabilityResponse(Map<String, Object> mcpResponse,
            AiChatRequest request) {
        Map<String, Object> content = parseMcpContent(mcpResponse);
        if (content == null || !Boolean.TRUE.equals(content.get("success"))) {
            String error = content != null ? (String) content.get("error") : "Unknown error";
            return ChatResponse.builder()
                    .sessionId(request.getSessionId())
                    .message("حدث خطأ أثناء التحقق من المواعيد: " + error)
                    .timestamp(LocalDateTime.now())
                    .detectedLanguage(baseService.detectLanguage(request.getMessage()))
                    .responseType(ChatResponseType.ERROR)
                    .build();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) content.get("slots");
        String providerName = (String) content.get("providerName");

        List<ScheduleResponse.TimeSlotResponse> timeSlots = new ArrayList<>();
        if (slots != null) {
            for (Map<String, Object> slot : slots) {
                ScheduleResponse.TimeSlotResponse timeSlot = new ScheduleResponse.TimeSlotResponse();
                timeSlot.setDate((String) slot.get("date"));
                try {
                    timeSlot.setStartTime(LocalTime.parse((String) slot.get("startTime")));
                    timeSlot.setEndTime(LocalTime.parse((String) slot.get("endTime")));
                } catch (Exception e) {
                    log.warn("Failed to parse time: {}", e.getMessage());
                }
                timeSlots.add(timeSlot);
            }
        }

        String reply = timeSlots.isEmpty() ? "لا توجد مواعيد متاحة لـ " + providerName + " في الفترة المطلوبة."
                : formatAvailabilityMessageForMCP(providerName, timeSlots);

        return ChatResponse.builder()
                .sessionId(request.getSessionId())
                .message(reply)
                .timestamp(LocalDateTime.now())
                .detectedLanguage(baseService.detectLanguage(request.getMessage()))
                .responseType(ChatResponseType.AVAILABLE_SLOTS)
                .availableTimeSlots(timeSlots)
                .build();
    }

    private ChatResponse handleCreateBookingResponse(Map<String, Object> mcpResponse,
            AiChatRequest request) {
        Map<String, Object> content = parseMcpContent(mcpResponse);

        if (content == null || !Boolean.TRUE.equals(content.get("success"))) {
            String error = content != null ? (String) content.get("error") : "Unknown error";

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> availableSlots = (List<Map<String, Object>>) content.get("availableSlots");
            if (availableSlots != null && !availableSlots.isEmpty()) {
                List<ScheduleResponse.TimeSlotResponse> slots = new ArrayList<>();
                for (Map<String, Object> slot : availableSlots) {
                    try {
                        ScheduleResponse.TimeSlotResponse timeSlot = new ScheduleResponse.TimeSlotResponse();
                        timeSlot.setStartTime(LocalTime.parse((String) slot.get("time")));
                        timeSlot.setEndTime(LocalTime.parse((String) slot.get("endTime")));
                        slots.add(timeSlot);
                    } catch (Exception e) {
                        log.warn("Failed to parse time: {}", e.getMessage());
                    }
                }
                return ChatResponse.builder()
                        .sessionId(request.getSessionId())
                        .message("الموعد المطلوب غير متاح. المواعيد المتاحة:")
                        .timestamp(LocalDateTime.now())
                        .detectedLanguage(baseService.detectLanguage(request.getMessage()))
                        .responseType(ChatResponseType.AVAILABLE_SLOTS)
                        .availableTimeSlots(slots)
                        .build();
            }

            return ChatResponse.builder()
                    .sessionId(request.getSessionId())
                    .message("فشل إنشاء الحجز: " + error)
                    .timestamp(LocalDateTime.now())
                    .detectedLanguage(baseService.detectLanguage(request.getMessage()))
                    .responseType(ChatResponseType.ERROR)
                    .build();
        }

        Long bookingId = content.get("bookingId") != null ? Long.parseLong(content.get("bookingId").toString()) : null;
        String status = (String) content.get("status");

        return ChatResponse.builder()
                .sessionId(request.getSessionId())
                .message("تم إنشاء طلب الحجز بنجاح رقم #" + bookingId)
                .timestamp(LocalDateTime.now())
                .detectedLanguage(baseService.detectLanguage(request.getMessage()))
                .responseType(ChatResponseType.BOOKING_CREATED)
                .booking(BookingResponse.builder()
                        .id(bookingId)
                        .status(BookingStatus.valueOf(status != null ? status : "PENDING"))
                        .build())
                .build();
    }

    private ChatResponse handleGetProviderDetailsResponse(Map<String, Object> mcpResponse,
            AiChatRequest request) {
        Map<String, Object> content = parseMcpContent(mcpResponse);

        if (content == null || !Boolean.TRUE.equals(content.get("success"))) {
            String error = content != null ? (String) content.get("error") : "Unknown error";
            return ChatResponse.builder()
                    .sessionId(request.getSessionId())
                    .message("فشل جلب معلومات المزود: " + error)
                    .timestamp(LocalDateTime.now())
                    .detectedLanguage(baseService.detectLanguage(request.getMessage()))
                    .responseType(ChatResponseType.ERROR)
                    .build();
        }

        String name = (String) content.get("name");
        String email = (String) content.get("email");
        String phoneNumber = (String) content.get("phoneNumber");
        String bio = (String) content.get("bio");
        Double avgRating = content.get("averageRating") != null ? ((Number) content.get("averageRating")).doubleValue()
                : 0.0;
        Integer totalReviews = (Integer) content.get("totalReviews");
        Integer yearsExp = (Integer) content.get("yearsOfExperience");
        String verificationStatus = (String) content.get("verificationStatus");
        Boolean hasSchedule = (Boolean) content.get("hasSchedule");

        @SuppressWarnings("unchecked")
        Map<String, Object> serviceType = (Map<String, Object>) content.get("serviceType");
        @SuppressWarnings("unchecked")
        Map<String, Object> location = (Map<String, Object>) content.get("location");

        StringBuilder message = new StringBuilder();
        message.append("**معلومات المزود**\n\n");
        message.append("**الاسم**: ").append(name).append("\n");

        if (verificationStatus != null) {
            String statusEmoji = "VERIFIED".equals(verificationStatus) ? "✅" : "⏳";
            message.append("**الحالة**: ").append(statusEmoji).append(" ").append(verificationStatus).append("\n");
        }

        if (avgRating > 0) {
            message.append("⭐ **التقييم**: ").append(String.format("%.1f", avgRating)).append("/5");
            if (totalReviews != null && totalReviews > 0) {
                message.append(" (").append(totalReviews).append(" تقييم)");
            }
            message.append("\n");
        }

        if (yearsExp != null && yearsExp > 0) {
            message.append("**سنوات الخبرة**: ").append(yearsExp).append("\n");
        }

        if (phoneNumber != null) {
            message.append("**الهاتف**: ").append(phoneNumber).append("\n");
        }

        if (email != null) {
            message.append("**البريد الإلكتروني**: ").append(email).append("\n");
        }

        if (bio != null && !bio.isEmpty()) {
            message.append("\n**عن المزود**:\n").append(bio).append("\n");
        }

        if (serviceType != null) {
            String serviceName = (String) serviceType.get("name");
            String serviceNameAr = (String) serviceType.get("nameAr");
            message.append("\n **الخدمة**: ").append(serviceNameAr != null ? serviceNameAr : serviceName)
                    .append("\n");
        }

        if (location != null) {
            String address = (String) location.get("address");
            if (address != null && !address.isEmpty()) {
                message.append("**العنوان**: ").append(address).append("\n");
            }
        }

        if (hasSchedule != null && hasSchedule) {
            message.append("\n✅ متاح للحجز");
        }

        ProviderSummaryResponse providerSummary = ProviderSummaryResponse.builder()
                .id(content.get("id") != null ? ((Number) content.get("id")).longValue() : null)
                .name(name)
                .averageRating(avgRating)
                .distance(0.0)
                .build();

        return ChatResponse.builder()
                .sessionId(request.getSessionId())
                .message(message.toString())
                .timestamp(LocalDateTime.now())
                .detectedLanguage(baseService.detectLanguage(request.getMessage()))
                .responseType(ChatResponseType.PROVIDER_DETAILS)
                .providers(List.of(providerSummary))
                .build();
    }

    // ===== PARSING HELPERS =====
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMcpContent(Map<String, Object> mcpResponse) {
        try {
            Map<String, Object> result = (Map<String, Object>) mcpResponse.get("result");
            if (result == null) {
                return null;
            }

            Boolean isError = (Boolean) result.get("isError");
            if (isError != null && isError) {
                return null;
            }

            Object contentObj = result.get("content");
            if (contentObj == null) {
                return null;
            }

            String text = null;
            if (contentObj instanceof List) {
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
                if (!contentList.isEmpty()) {
                    text = (String) contentList.get(0).get("text");
                }
            } else if (contentObj instanceof Object[]) {
                Object[] contentArray = (Object[]) contentObj;
                if (contentArray.length > 0) {
                    Map<String, Object> contentMap = (Map<String, Object>) contentArray[0];
                    text = (String) contentMap.get("text");
                }
            }

            if (text == null || text.isEmpty()) {
                return null;
            }

            return objectMapper.readValue(text, Map.class);

        } catch (Exception e) {
            log.error("Failed to parse MCP content: {}", e.getMessage(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<ProviderSummaryResponse> parseMcpResponse(Map<String, Object> mcpResponse) {
        try {
            Map<String, Object> result = (Map<String, Object>) mcpResponse.get("result");
            if (result == null) {
                log.warn("No 'result' field in MCP response");
                return List.of();
            }

            Boolean isError = (Boolean) result.get("isError");
            if (isError != null && isError) {
                Object contentObj = result.get("content");
                String errorText = null;
                if (contentObj instanceof List) {
                    List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
                    if (!contentList.isEmpty()) {
                        errorText = (String) contentList.get(0).get("text");
                    }
                } else if (contentObj instanceof Object[]) {
                    Object[] contentArray = (Object[]) contentObj;
                    if (contentArray.length > 0) {
                        Map<String, Object> contentMap = (Map<String, Object>) contentArray[0];
                        errorText = (String) contentMap.get("text");
                    }
                }
                if (errorText != null) {
                    log.warn("MCP tool returned error: {}", errorText);
                }
                return List.of();
            }

            Object contentObj = result.get("content");
            if (contentObj == null) {
                log.warn("No content in MCP response");
                return List.of();
            }

            String text = null;

            if (contentObj instanceof List) {
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
                if (!contentList.isEmpty()) {
                    Map<String, Object> firstContent = contentList.get(0);
                    text = (String) firstContent.get("text");
                }
            } else if (contentObj instanceof Object[]) {
                Object[] contentArray = (Object[]) contentObj;
                if (contentArray.length > 0) {
                    Map<String, Object> firstContent = (Map<String, Object>) contentArray[0];
                    text = (String) firstContent.get("text");
                }
            }

            if (text == null || text.isEmpty()) {
                log.warn("No text in content");
                return List.of();
            }

            log.info("MCP response text: {}", text);

            if (text.equals("[]") || text.isEmpty()) {
                return List.of();
            }

            Map<String, Object> responseMap = objectMapper.readValue(text, Map.class);

            Object providersObj = responseMap.get("providers");
            if (providersObj == null) {
                log.warn("No providers array in response");
                return List.of();
            }

            String providersJson = objectMapper.writeValueAsString(providersObj);
            List<ProviderSummaryResponse> providers = objectMapper.readValue(
                    providersJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ProviderSummaryResponse.class));

            return providers != null ? providers : List.of();

        } catch (Exception e) {
            log.error("Failed to parse MCP response: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private String formatAvailabilityMessageForMCP(String providerName,
            List<ScheduleResponse.TimeSlotResponse> slots) {
        if (slots == null || slots.isEmpty()) {
            return "لا توجد مواعيد متاحة لـ " + providerName;
        }

        Map<LocalDate, List<ScheduleResponse.TimeSlotResponse>> slotsByDate = slots.stream()
                .collect(Collectors.groupingBy(slot -> baseService.parseDateSafe(slot.getDate())));

        StringBuilder message = new StringBuilder();
        message.append("المواعيد المتاحة لـ ").append(providerName).append(":\n\n");

        int dateCount = 0;
        for (Map.Entry<LocalDate, List<ScheduleResponse.TimeSlotResponse>> entry : slotsByDate.entrySet()) {
            if (dateCount++ >= 7)
                break;
            message.append("📅 ").append(AiAssistantServiceImpl.DATE_FORMAT.format(entry.getKey())).append(":\n");
            String times = entry.getValue().stream()
                    .map(slot -> AiAssistantServiceImpl.TIME_FORMAT.format(slot.getStartTime()) + " - " +
                            AiAssistantServiceImpl.TIME_FORMAT.format(slot.getEndTime()))
                    .collect(Collectors.joining(", "));
            message.append("   🕐 ").append(times).append("\n");
        }

        return message.toString();
    }

    // ===== INNER CLASS =====
    
    @Data
    private static class McpToolCallResponse {
        private String tool;
        private Map<String, Object> arguments;
        private boolean needsClarification;
        private List<String> missingFields;
        private String reply;
    }
}