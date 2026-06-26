package com.aykhedma.mcp.tools.tools;

import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.ScheduleResponse;
import com.aykhedma.mcp.tools.McpTool;
import com.aykhedma.mcp.tools.McpToolRegistry;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.TimeSlotRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.service.BookingService;
import com.aykhedma.service.ProviderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class CreateBookingTool implements McpTool {

    private final McpToolRegistry toolRegistry;
    private final BookingService bookingService;
    private final ProviderService providerService; 
    private final ProviderRepository providerRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @PostConstruct
    public void init() {
        toolRegistry.registerTool(this);
        log.info("CreateBookingTool registered");
    }

    @Override
    public String getName() {
        return "create_booking";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", getName());
        schema.put("description",
                "Create a booking with a specific provider at a specific date and time. Requires all fields.");

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> providerIdProp = new HashMap<>();
        providerIdProp.put("type", "integer");
        providerIdProp.put("description", "The provider's numeric ID");
        properties.put("providerId", providerIdProp);

        Map<String, Object> dateProp = new HashMap<>();
        dateProp.put("type", "string");
        dateProp.put("description", "Booking date (format: yyyy-MM-dd)");
        dateProp.put("pattern", "^\\d{4}-\\d{2}-\\d{2}$");
        properties.put("date", dateProp);

        Map<String, Object> timeProp = new HashMap<>();
        timeProp.put("type", "string");
        timeProp.put("description", "Booking time (format: HH:mm)");
        timeProp.put("pattern", "^\\d{2}:\\d{2}$");
        properties.put("time", timeProp);

        Map<String, Object> problemProp = new HashMap<>();
        problemProp.put("type", "string");
        problemProp.put("description", "Description of the problem or job");
        properties.put("problemDescription", problemProp);

        Map<String, Object> consumerIdProp = new HashMap<>();
        consumerIdProp.put("type", "integer");
        consumerIdProp.put("description", "The consumer's user ID");
        properties.put("consumerId", consumerIdProp);

        Map<String, Object> consumerEmailProp = new HashMap<>();
        consumerEmailProp.put("type", "string");
        consumerEmailProp.put("description", "The consumer's email (alternative to consumerId)");
        properties.put("consumerEmail", consumerEmailProp);

        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("providerId", "date", "time", "problemDescription"));

        schema.put("inputSchema", inputSchema);
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        Long providerId = ((Number) arguments.get("providerId")).longValue();
        String dateStr = (String) arguments.get("date");
        String timeStr = (String) arguments.get("time");
        String problemDescription = (String) arguments.get("problemDescription");
        Long consumerId = arguments.get("consumerId") != null ? ((Number) arguments.get("consumerId")).longValue()
                : null;
        String consumerEmail = (String) arguments.get("consumerEmail");

        log.info("create_booking: provider={}, date={}, time={}, consumerId={}, consumerEmail={}",
                providerId, dateStr, timeStr, consumerId, consumerEmail);

        try {
            Provider provider = providerRepository.findById(providerId).orElse(null);
            if (provider == null) {
                return Map.of(
                        "success", false,
                        "error", "Provider not found. Please provide a valid provider ID.");
            }

            Long resolvedConsumerId = resolveConsumerId(consumerId, consumerEmail);
            if (resolvedConsumerId == null) {
                return Map.of(
                        "success", false,
                        "error", "Consumer not found. Please provide a valid consumer ID or email.");
            }

            LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
            LocalTime time = LocalTime.parse(timeStr, TIME_FORMAT);

            if (date.isBefore(LocalDate.now())) {
                return Map.of(
                        "success", false,
                        "error", "Cannot book in the past. Please provide a future date.");
            }

            boolean isAvailable = timeSlotRepository.isTimeWithinAvailableSlot(
                    providerId,
                    date,
                    time);

            if (!isAvailable) {
                List<ScheduleResponse.TimeSlotResponse> availableSlots = providerService
                        .getAvailableTimeSlots(providerId, date);

                return Map.of(
                        "success", false,
                        "error", "The requested time slot is not available.",
                        "providerId", providerId,
                        "providerName", provider.getName(),
                        "date", dateStr,
                        "time", timeStr,
                        "availableSlots", availableSlots.stream()
                                .limit(5)
                                .map(slot -> Map.of(
                                        "time", slot.getStartTime().format(TIME_FORMAT),
                                        "endTime", slot.getEndTime().format(TIME_FORMAT)))
                                .toList());
            }

            BookingResponse bookingResponse = bookingService.requestBooking(
                    resolvedConsumerId,
                    BookingRequest.builder()
                            .providerId(providerId)
                            .requestedDate(date)
                            .requestedTime(time)
                            .problemDescription(problemDescription)
                            .build());

            log.info("Booking created successfully: {}", bookingResponse.getId());

            return Map.of(
                    "success", true,
                    "message", "Booking created successfully!",
                    "bookingId", bookingResponse.getId(),
                    "providerId", providerId,
                    "providerName", provider.getName(),
                    "date", dateStr,
                    "time", timeStr,
                    "status", bookingResponse.getStatus() != null ? bookingResponse.getStatus().name() : "PENDING");

        } catch (Exception e) {
            log.error("Error in create_booking: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error", "Failed to create booking: " + e.getMessage());
        }
    }

    private Long resolveConsumerId(Long consumerId, String consumerEmail) {
        if (consumerId != null) {
            return consumerId;
        }

        if (consumerEmail != null && !consumerEmail.isEmpty()) {
            Optional<User> user = userRepository.findByEmail(consumerEmail);
            return user.map(User::getId).orElse(null);
        }

        return null;
    }
}