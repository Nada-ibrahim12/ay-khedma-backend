package com.aykhedma.mcp.tools.tools;

import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.ScheduleResponse;
import com.aykhedma.mcp.tools.McpTool;
import com.aykhedma.mcp.tools.McpToolRegistry;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.TimeSlotRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.service.BookingService;
import com.aykhedma.service.ProviderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
                "Create a booking with a specific provider at a specific date and time. " +
                        "Either providerId (numeric) or providerName (string) must be provided. " +
                        "consumerId is automatically injected by the system and should not be provided.");

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> providerNameProp = new HashMap<>();
        providerNameProp.put("type", "string");
        providerNameProp.put("description", "Provider's name (alternative to providerId)");
        properties.put("providerName", providerNameProp);

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

        
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("date", "time", "problemDescription")); 

        schema.put("inputSchema", inputSchema);
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {

        Long providerId = null;
        if (arguments.containsKey("providerId")) {
            providerId = ((Number) arguments.get("providerId")).longValue();
        } else if (arguments.containsKey("providerName")) {
            String providerName = (String) arguments.get("providerName");
            List<Provider> providers = providerRepository.findByNameContainingIgnoreCase(providerName);
            if (providers.isEmpty()) {
                return Map.of("success", false, "error", "No provider found with name: " + providerName);
            }
            if (providers.size() > 1) {
                return Map.of("success", false, "error",
                        "Multiple providers found with that name. Please specify more details.");
            }
            providerId = providers.get(0).getId();
        } else {
            return Map.of("success", false, "error", "Either providerId or providerName is required.");
        }

        String dateStr = (String) arguments.get("date");
        String timeStr = (String) arguments.get("time");
        String problemDescription = (String) arguments.get("problemDescription");

        Long consumerId = null;

        if (arguments.containsKey("consumerId")) {
            Object consumerIdObj = arguments.get("consumerId");
            if (consumerIdObj instanceof Number) {
                consumerId = ((Number) consumerIdObj).longValue();
            } else if (consumerIdObj instanceof String) {
                try {
                    consumerId = Long.parseLong((String) consumerIdObj);
                } catch (NumberFormatException e) {
                    log.warn("Invalid consumerId string: {}", consumerIdObj);
                }
            }
        }
        
        if (consumerId == null) {
            consumerId = getCurrentConsumerId();
        }

        log.info("create_booking: provider={}, date={}, time={}, consumerId={}",
                providerId, dateStr, timeStr, consumerId);

        if (consumerId == null) {
            return Map.of("success", false, "error", "Consumer ID is missing. Please ensure you are logged in.");
        }

        try {
            Provider provider = providerRepository.findById(providerId).orElse(null);
            if (provider == null) {
                return Map.of(
                        "success", false,
                        "error", "Provider not found. Please provide a valid provider ID.");
            }

            LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
            LocalTime time = LocalTime.parse(timeStr, TIME_FORMAT);

            if (date.isBefore(LocalDate.now())) {
                return Map.of(
                        "success", false,
                        "error", "Cannot book in the past. Please provide a future date.");
            }

            boolean isAvailable = timeSlotRepository.isTimeWithinAvailableSlotByProviderId(
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
                    consumerId,
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
                    "booking", bookingResponse 
            );
            
        } catch (Exception e) {
            log.error("Error in create_booking: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error", "Failed to create booking: " + e.getMessage());
        }
    }

    private Long getCurrentConsumerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            User user = (User) principal;
            if (user.getRole() == UserType.CONSUMER) {
                return user.getId();
            }
        }
        return null;
    }
}