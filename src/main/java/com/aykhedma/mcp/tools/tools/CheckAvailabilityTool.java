package com.aykhedma.mcp.tools.tools;

import com.aykhedma.dto.response.ScheduleResponse;
import com.aykhedma.mcp.tools.McpTool;
import com.aykhedma.mcp.tools.McpToolRegistry;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.service.ProviderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class CheckAvailabilityTool implements McpTool {

    private final McpToolRegistry toolRegistry;
    private final ProviderService providerService;
    private final ProviderRepository providerRepository;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @PostConstruct
    public void init() {
        toolRegistry.registerTool(this);
        log.info("CheckAvailabilityTool registered");
    }

    @Override
    public String getName() {
        return "check_availability";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", getName());
        schema.put("description",
                "Check available time slots for a specific provider on a given date. If no date is provided, returns availability for the next 7 days.");

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> providerIdProp = new HashMap<>();
        providerIdProp.put("type", "integer");
        providerIdProp.put("description", "The provider's numeric ID");
        properties.put("providerId", providerIdProp);

        Map<String, Object> providerNameProp = new HashMap<>();
        providerNameProp.put("type", "string");
        providerNameProp.put("description", "The provider's full name (alternative to providerId)");
        properties.put("providerName", providerNameProp);

        Map<String, Object> dateProp = new HashMap<>();
        dateProp.put("type", "string");
        dateProp.put("description", "Date to check (format: yyyy-MM-dd). If null, returns next 7 days");
        dateProp.put("pattern", "^\\d{4}-\\d{2}-\\d{2}$");
        properties.put("date", dateProp);

        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of());

        schema.put("inputSchema", inputSchema);
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        Long providerId = arguments.get("providerId") != null ? ((Number) arguments.get("providerId")).longValue()
                : null;
        String providerName = (String) arguments.get("providerName");
        String dateStr = (String) arguments.get("date");

        log.info("check_availability: providerId={}, providerName={}, date={}",
                providerId, providerName, dateStr);

        try {
            Long resolvedProviderId = resolveProviderId(providerId, providerName);
            if (resolvedProviderId == null) {
                log.warn("Provider not found: providerId={}, providerName={}", providerId, providerName);
                return Map.of(
                        "success", false,
                        "error", "Provider not found. Please provide a valid provider ID or name.");
            }

            Provider provider = providerRepository.findById(resolvedProviderId).orElse(null);
            if (provider == null) {
                return Map.of(
                        "success", false,
                        "error", "Provider not found in database.");
            }

            List<ScheduleResponse.TimeSlotResponse> slots;
            if (dateStr == null || dateStr.isEmpty()) {
                slots = providerService.getAvailableTimeSlotsForDateRange(
                        resolvedProviderId,
                        LocalDate.now().plusDays(1),
                        LocalDate.now().plusDays(7));
                log.info("Found {} slots for next 7 days", slots.size());
            } else {
                LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
                slots = providerService.getAvailableTimeSlots(resolvedProviderId, date);
                log.info("Found {} slots for {}", slots.size(), dateStr);
            }

            List<Map<String, Object>> result = slots.stream()
                    .map(slot -> {
                        Map<String, Object> slotMap = new LinkedHashMap<>();
                        slotMap.put("date", slot.getDate());
                        slotMap.put("startTime",
                                slot.getStartTime() != null ? slot.getStartTime().format(TIME_FORMAT) : null);
                        slotMap.put("endTime",
                                slot.getEndTime() != null ? slot.getEndTime().format(TIME_FORMAT) : null);
                        return slotMap;
                    })
                    .toList();

            return Map.of(
                    "success", true,
                    "providerId", resolvedProviderId,
                    "providerName", provider.getName(),
                    "slots", result,
                    "count", result.size());

        } catch (Exception e) {
            log.error("Error in check_availability: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error", "Failed to check availability: " + e.getMessage());
        }
    }

    private Long resolveProviderId(Long providerId, String providerName) {
        if (providerId != null) {
            return providerId;
        }

        if (providerName != null && !providerName.isEmpty()) {
            List<Provider> providers = providerRepository.findAll();
            String normalized = providerName.toLowerCase().trim();
            return providers.stream()
                    .filter(p -> p.getName() != null &&
                            p.getName().toLowerCase().contains(normalized))
                    .findFirst()
                    .map(Provider::getId)
                    .orElse(null);
        }

        return null;
    }
}