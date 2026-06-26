package com.aykhedma.mcp.tools.tools;

import com.aykhedma.mcp.tools.McpTool;
import com.aykhedma.mcp.tools.McpToolRegistry;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.service.ServiceTypeResolver;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class SearchProvidersTool implements McpTool {

    private final McpToolRegistry toolRegistry;
    private final ProviderRepository providerRepository;
    private final ServiceTypeResolver serviceTypeResolver;

    @PostConstruct
    public void init() {
        log.info("SearchProvidersTool PostConstruct STARTED");
        try {
            toolRegistry.registerTool(this);
            log.info("SearchProvidersTool registered successfully!");
        } catch (Exception e) {
            log.error("Failed to register SearchProvidersTool: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return "search_providers";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", getName());
        schema.put("description", "Search for service providers by type, location, and radius");

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> serviceTypeProp = new HashMap<>();
        serviceTypeProp.put("type", "string");
        serviceTypeProp.put("description", "The type of service needed (e.g., 'electrician', 'plumber')");
        properties.put("serviceType", serviceTypeProp);

        Map<String, Object> latProp = new HashMap<>();
        latProp.put("type", "number");
        latProp.put("description", "User's location latitude");
        properties.put("latitude", latProp);

        Map<String, Object> lngProp = new HashMap<>();
        lngProp.put("type", "number");
        lngProp.put("description", "User's location longitude");
        properties.put("longitude", lngProp);

        Map<String, Object> radiusProp = new HashMap<>();
        radiusProp.put("type", "integer");
        radiusProp.put("description", "Search radius in kilometers (default: 10)");
        radiusProp.put("default", 10);
        properties.put("radiusKm", radiusProp);


        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("serviceType", "latitude", "longitude"));

        schema.put("inputSchema", inputSchema);
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String serviceType = (String) arguments.get("serviceType");
        Double latitude = ((Number) arguments.get("latitude")).doubleValue();
        Double longitude = ((Number) arguments.get("longitude")).doubleValue();
        Integer radiusKm = arguments.get("radiusKm") != null ? ((Number) arguments.get("radiusKm")).intValue() : 10;

        log.info("search_providers: serviceType={}, location=({},{}), radius={}",
                serviceType, latitude, longitude, radiusKm);

        try {
            ServiceType resolvedService = serviceTypeResolver.resolveByMeaning(serviceType);
            if (resolvedService == null) {
                log.warn("No service type found for: {}", serviceType);
                return List.of();
            }

            List<Provider> providers = providerRepository
                    .findByServiceTypeIdAndVerificationStatus(resolvedService.getId(), VerificationStatus.VERIFIED);

            if (providers.isEmpty()) {
                log.info("No providers found for service: {}", serviceType);
                return List.of();
            }

            int limit = 5;
            providers = providers.stream().limit(limit).toList();

            List<Map<String, Object>> responses = providers.stream()
                    .map(provider -> toProviderMap(provider, latitude, longitude))
                    .toList();

            log.info("Found {} providers", responses.size());
            return responses;

        } catch (Exception e) {
            log.error("Error in search_providers: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private Map<String, Object> toProviderMap(Provider provider, double consumerLat, double consumerLng) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", provider.getId());
        result.put("name", provider.getName());
        result.put("averageRating", provider.getAverageRating() != null ? provider.getAverageRating() : 0.0);

        double distanceKm = 0.0;
        if (provider.getLocation() != null && provider.getLocation().getLatitude() != null
                && provider.getLocation().getLongitude() != null) {
            Location consumerLocation = Location.builder()
                    .latitude(consumerLat)
                    .longitude(consumerLng)
                    .build();
            distanceKm = provider.getLocation().calculateDistance(consumerLocation);
        }
        result.put("distance", distanceKm);

        if (provider.getServiceType() != null) {
            result.put("serviceType", provider.getServiceType().getName());
        }
        return result;
    }
}