package com.aykhedma.mcp.tools.tools;

import com.aykhedma.mcp.tools.McpTool;
import com.aykhedma.mcp.tools.McpToolRegistry;
import com.aykhedma.model.user.Provider;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.dto.response.ProviderResponse;
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
    private final ProviderMapper providerMapper;

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

            ProviderResponse response = providerMapper.toProviderResponse(provider);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("id", response.getId());
            details.put("name", response.getName());
            details.put("email", response.getEmail());
            details.put("phoneNumber", response.getPhoneNumber());
            details.put("profileImage", response.getProfileImage());
            details.put("bio", response.getBio() != null ? response.getBio() : "No bio available");
            details.put("averageRating", response.getAverageRating() != null ? response.getAverageRating() : 0.0);
            details.put("totalReviews",
                    response.getInteractionRatingCount() != null ? response.getInteractionRatingCount() : 0);
            details.put("yearsOfExperience",
                    response.getYearsOfExperience() != null ? response.getYearsOfExperience() : 0);
            details.put("verificationStatus",
                    response.getVerificationStatus() != null ? response.getVerificationStatus().name() : "PENDING");
            details.put("completedJobs", response.getCompletedJobs() != null ? response.getCompletedJobs() : 0);
            details.put("totalBookings", response.getTotalBookings() != null ? response.getTotalBookings() : 0);
            details.put("cancellationRate",
                    response.getCancellationRate() != null ? response.getCancellationRate() : 0.0);
            details.put("acceptanceRate", response.getAcceptanceRate() != null ? response.getAcceptanceRate() : 0);
            details.put("averageTime", response.getAverageTime());
            details.put("worksAt", response.getWorksAt());
            details.put("workLocation", response.getWorkLocation());
            details.put("emergencyEnabled", response.getEmergencyEnabled());
            details.put("serviceAreaRadius", response.getServiceAreaRadius());
            details.put("averageJobs", response.getAverageJobs());

            if (response.getServiceType() != null) {
                Map<String, Object> serviceType = new LinkedHashMap<>();
                serviceType.put("id", response.getServiceTypeId());
                serviceType.put("name", response.getServiceType());
                serviceType.put("nameAr", response.getServiceTypeAr());
                serviceType.put("category", response.getServiceCategory());
                serviceType.put("categoryAr", response.getServiceCategoryAr());
                details.put("serviceType", serviceType);
            }

            if (response.getLocation() != null) {
                Map<String, Object> location = new LinkedHashMap<>();
                location.put("latitude", response.getLocation().getLatitude());
                location.put("longitude", response.getLocation().getLongitude());
                location.put("address", response.getLocation().getAddress());
                location.put("area", response.getArea());
                details.put("location", location);
            }

            if (response.getPrice() != null) {
                Map<String, Object> pricing = new LinkedHashMap<>();
                pricing.put("price", response.getPrice());
                pricing.put("priceType", response.getPriceType() != null ? response.getPriceType().name() : null);
                pricing.put("priceTypeAr", response.getPriceTypeAr());
                details.put("pricing", pricing);
            }

            details.put("hasSchedule", response.getSchedule() != null);

            Map<String, Object> ratings = new LinkedHashMap<>();
            ratings.put("averageRating", response.getAverageRating());
            ratings.put("averagePunctualityRating", response.getAveragePunctualityRating());
            ratings.put("averageCommitmentRating", response.getAverageCommitmentRating());
            ratings.put("averageQualityOfWorkRating", response.getAverageQualityOfWorkRating());
            ratings.put("averageInteractionRating", response.getAverageInteractionRating());
            details.put("ratings", ratings);

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