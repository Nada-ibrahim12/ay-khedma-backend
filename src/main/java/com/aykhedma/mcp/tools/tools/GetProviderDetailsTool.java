package com.aykhedma.mcp.tools.tools;

import com.aykhedma.mcp.tools.McpTool;
import com.aykhedma.mcp.tools.McpToolRegistry;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.ProviderRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class GetProviderDetailsTool implements McpTool {

    private final McpToolRegistry toolRegistry;
    private final ProviderRepository providerRepository;

    @PostConstruct
    public void init() {
        toolRegistry.registerTool(this);
        log.info("GetProviderDetailsTool registered");
    }

    @Override
    public String getName() {
        return "get_provider_details";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", getName());
        schema.put("description",
                "Get detailed information about a specific provider including bio, ratings, services, and contact information.");

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

        log.info("get_provider_details: providerId={}, providerName={}",
                providerId, providerName);

        try {
            Provider provider = resolveProvider(providerId, providerName);
            if (provider == null) {
                log.warn("Provider not found: providerId={}, providerName={}", providerId, providerName);
                return Map.of(
                        "success", false,
                        "error", "Provider not found. Please provide a valid provider ID or name.");
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("id", provider.getId());
            details.put("name", provider.getName());
            details.put("email", provider.getEmail());
            details.put("phoneNumber", provider.getPhoneNumber());
            details.put("bio", provider.getBio() != null ? provider.getBio() : "No bio available");
            details.put("averageRating", provider.getAverageRating() != null ? provider.getAverageRating() : 0.0);
            details.put("totalReviews",
                    provider.getInteractionRatingCount() != null ? provider.getInteractionRatingCount() : 0);
            details.put("yearsOfExperience",
                    provider.getYearsOfExperience() != null ? provider.getYearsOfExperience() : 0);
            details.put("verificationStatus", provider.getVerificationStatus().name());

            if (provider.getServiceType() != null) {
                Map<String, Object> serviceType = new LinkedHashMap<>();
                serviceType.put("id", provider.getServiceType().getId());
                serviceType.put("name", provider.getServiceType().getName());
                serviceType.put("nameAr", provider.getServiceType().getNameAr());
                details.put("serviceType", serviceType);
            }

            if (provider.getLocation() != null) {
                Map<String, Object> location = new LinkedHashMap<>();
                location.put("latitude", provider.getLocation().getLatitude());
                location.put("longitude", provider.getLocation().getLongitude());
                location.put("address", provider.getLocation().getAddress());
                details.put("location", location);
            }

            if (provider.getSchedule() != null) {
                details.put("hasSchedule", true);
            }

            details.put("success", true);

            log.info("Returned details for provider: {}", provider.getName());
            return details;

        } catch (Exception e) {
            log.error("Error in get_provider_details: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error", "Failed to get provider details: " + e.getMessage());
        }
    }

    private Provider resolveProvider(Long providerId, String providerName) {
        if (providerId != null) {
            return providerRepository.findById(providerId).orElse(null);
        }

        if (providerName != null && !providerName.isEmpty()) {
            List<Provider> providers = providerRepository
                    .findByNameContainingIgnoreCaseWithDetails(providerName.trim());
            if (!providers.isEmpty()) {
                return providers.get(0);
            }
        }

        return null;
    }
}