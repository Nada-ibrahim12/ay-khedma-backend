package com.aykhedma.mcp.tools.tools;

import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.mapper.ProviderMapper;
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
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class SearchProvidersTool implements McpTool {

    private final McpToolRegistry toolRegistry;
    private final ProviderRepository providerRepository;
    private final ServiceTypeResolver serviceTypeResolver;
    private final ProviderMapper providerMapper;

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
        serviceTypeProp.put("type", "array");
        serviceTypeProp.put("items", Map.of("type", "string"));
        serviceTypeProp.put("description",
                "List of service types needed (e.g., ['Plumbing', 'Pipe Installation', 'Drain Cleaning'])");
        properties.put("serviceTypes", serviceTypeProp);

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
        inputSchema.put("required", List.of("serviceTypes", "latitude", "longitude"));

        schema.put("inputSchema", inputSchema);
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        List<String> serviceTypes = (List<String>) arguments.get("serviceTypes");

        if (serviceTypes == null && arguments.get("serviceType") != null) {
            serviceTypes = List.of((String) arguments.get("serviceType"));
        }

        if (serviceTypes == null || serviceTypes.isEmpty()) {
            log.warn("No service types provided");
            Map<String, Object> emptyResult = new LinkedHashMap<>();
            emptyResult.put("providers", List.of());
            emptyResult.put("count", 0);
            emptyResult.put("message", "No service types specified");
            emptyResult.put("serviceTypesMatched", List.of());
            return emptyResult;
        }

        Double latitude = ((Number) arguments.get("latitude")).doubleValue();
        Double longitude = ((Number) arguments.get("longitude")).doubleValue();
        Integer radiusKm = arguments.get("radiusKm") != null ? ((Number) arguments.get("radiusKm")).intValue() : 10;

        log.info("search_providers: serviceTypes={}, location=({},{}), radius={}",
                serviceTypes, latitude, longitude, radiusKm);

        try {
            List<ServiceType> resolvedServices = new ArrayList<>();
            List<String> matchedServiceNames = new ArrayList<>();

            for (String serviceType : serviceTypes) {
                ServiceType resolved = serviceTypeResolver.resolveByMeaning(serviceType);
                if (resolved != null) {
                    resolvedServices.add(resolved);
                    matchedServiceNames.add(resolved.getNameAr() + " (" + resolved.getName() + ")");
                    log.info("Resolved '{}' to service type: {} ({})",
                            serviceType, resolved.getName(), resolved.getNameAr());
                } else {
                    log.warn("No service type found for: {}", serviceType);
                }
            }

            if (resolvedServices.isEmpty()) {
                log.warn("No service types could be resolved for: {}", serviceTypes);
                Map<String, Object> emptyResult = new LinkedHashMap<>();
                emptyResult.put("providers", List.of());
                emptyResult.put("count", 0);
                emptyResult.put("message", "لم نجد خدمات تطابق طلبك");
                emptyResult.put("serviceTypesMatched", List.of());
                return emptyResult;
            }

            Set<Long> providerIds = new HashSet<>();
            List<Provider> allProviders = new ArrayList<>();

            for (ServiceType service : resolvedServices) {
                List<Provider> providers = providerRepository
                        .findByServiceTypeIdAndVerificationStatusWithDetails(service.getId(),
                                VerificationStatus.VERIFIED);
                for (Provider p : providers) {
                    if (providerIds.add(p.getId())) {
                        allProviders.add(p);
                    }
                }
            }

            if (allProviders.isEmpty()) {
                log.info("No providers found for services: {}", serviceTypes);
                Map<String, Object> emptyResult = new LinkedHashMap<>();
                emptyResult.put("providers", List.of());
                emptyResult.put("count", 0);
                emptyResult.put("message", "لم نجد مقدمي خدمة في منطقتك حالياً");
                emptyResult.put("serviceTypesMatched", matchedServiceNames);
                return emptyResult;
            }

            Location consumerLocation = Location.builder()
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();

            int limit = 10;
            List<Map<String, Object>> responses = allProviders.stream()
                    .filter(p -> p.getLocation() != null)
                    .map(provider -> toProviderMap(provider, consumerLocation))
                    .filter(providerMap -> (double) providerMap.get("distance") <= radiusKm)
                    .sorted(
                            Comparator.comparingDouble((Map<String, Object> p) -> (double) p.get("averageRating"))
                                    .reversed()
                                    .thenComparingDouble(p -> (double) p.get("distance")))
                    .limit(limit)
                    .collect(Collectors.toList());

            log.info("Found {} providers within {} km for service types: {}",
                    responses.size(), radiusKm, matchedServiceNames);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("providers", responses);
            result.put("count", responses.size());
            result.put("serviceTypesMatched", matchedServiceNames);
            result.put("message", responses.isEmpty()
                    ? "لم نجد مقدمي خدمة في منطقتك حالياً"
                    : String.format("وجدنا %d مقدم خدمة في منطقتك", responses.size()));

            return result;

        } catch (Exception e) {
            log.error("Error in search_providers: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("providers", List.of());
            errorResult.put("count", 0);
            errorResult.put("message", "حدث خطأ أثناء البحث");
            errorResult.put("serviceTypesMatched", List.of());
            return errorResult;
        }
    }

    private Map<String, Object> toProviderMap(Provider provider, Location consumerLocation) {
        Map<String, Object> result = new LinkedHashMap<>();

        ProviderSummaryResponse response = providerMapper.toProviderSummaryResponse(provider);

        result.put("id", response.getId());
        result.put("name", response.getName());
        result.put("profileImage", response.getProfileImage()); 
        result.put("serviceType", response.getServiceType());
        result.put("serviceTypeAr", response.getServiceTypeAr());
        result.put("averageRating", response.getAverageRating());
        result.put("price", response.getPrice());
        result.put("priceType", response.getPriceType());
        result.put("priceTypeAr", response.getPriceTypeAr());
        result.put("area", response.getArea());
        result.put("cancellationRate", response.getCancellationRate()); 

        double distanceKm = 0.0;
        if (provider.getLocation() != null && consumerLocation != null) {
            distanceKm = provider.getLocation().calculateDistance(consumerLocation);
        }
        result.put("distance", Math.round(distanceKm * 10.0) / 10.0);

        if (distanceKm > 0) {
            int estimatedMinutes = (int) Math.ceil(distanceKm * 5);
            result.put("estimatedArrivalTime", estimatedMinutes);
        } else {
            result.put("estimatedArrivalTime", null);
        }

        return result;
    }
}