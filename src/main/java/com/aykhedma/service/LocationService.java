package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.response.DistanceResponse;
import com.aykhedma.dto.response.LocationResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.LocationMapper;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.ConsumerRepository;
import com.aykhedma.repository.LocationRepository;
import com.aykhedma.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aykhedma.mapper.ProviderMapper;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final ConsumerRepository consumerRepository;
    private final ProviderRepository providerRepository;
    private final LocationMapper locationMapper;
    private final ProviderMapper providerMapper;

    // ====== CONSUMER LOCATION METHODS ======

    @Transactional
    public LocationResponse saveConsumerLocation(Long consumerId, LocationDTO locationDTO) {
        log.info("Saving location for consumer ID: {}", consumerId);

        // Use getReferenceById instead of findById to avoid loading collections
        Consumer consumer = consumerRepository.getReferenceById(consumerId);

        if (!consumerRepository.existsById(consumerId)) {
            throw new ResourceNotFoundException("Consumer not found with id: " + consumerId);
        }

        // Check if consumer already has a location
        if (consumer.getLocation() != null) {
            throw new IllegalStateException("Consumer already has a location. Use update instead.");
        }

        // Create new location
        Location location = locationMapper.toEntity(locationDTO);
        location = locationRepository.save(location);

        consumerRepository.updateConsumerLocation(consumerId, location.getId());

        log.info("Location saved successfully for consumer ID: {}", consumerId);

        return locationMapper.toResponseWithMessage(location, "Location saved successfully",true);
    }
    @Transactional
    public LocationResponse updateConsumerLocation(Long consumerId, LocationDTO locationDTO) {
        log.info("Updating location for consumer ID: {}", consumerId);

        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        if (consumer.getLocation() == null) {
            throw new ResourceNotFoundException("Consumer has no location to update. Please save location first.");
        }

        Location location = consumer.getLocation();

        // Update existing location using mapper
        locationMapper.updateEntity(locationDTO, location);
        location = locationRepository.save(location);

        log.info("Location updated successfully for consumer ID: {}", consumerId);

        return locationMapper.toResponseWithMessage(location, "Location updated successfully", true);
    }

    @Transactional
    public LocationResponse patchConsumerLocation(Long consumerId, LocationDTO locationDTO) {
        log.info("Patching location for consumer ID: {}", consumerId);

        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        if (consumer.getLocation() == null) {
            throw new ResourceNotFoundException("Consumer has no location to patch. Please save location first.");
        }

        Location location = consumer.getLocation();

        // Manual patching (only update non-null fields)
        // Note: You could also create a custom mapper method for patching
        if (locationDTO.getLatitude() != null) {
            location.setLatitude(locationDTO.getLatitude());
        }
        if (locationDTO.getLongitude() != null) {
            location.setLongitude(locationDTO.getLongitude());
        }
        if (locationDTO.getAddress() != null) {
            location.setAddress(locationDTO.getAddress());
        }
        if (locationDTO.getArea() != null) {
            location.setArea(locationDTO.getArea());
        }
        if (locationDTO.getCity() != null) {
            location.setCity(locationDTO.getCity());
        }

        location = locationRepository.save(location);

        log.info("Location patched successfully for consumer ID: {}", consumerId);

        return locationMapper.toResponseWithMessage(location, "Location patched successfully", true);
    }

    @Transactional(readOnly = true)
    public LocationDTO getConsumerLocation(Long consumerId) {
        log.info("Fetching location for consumer ID: {}", consumerId);

        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        if (consumer.getLocation() == null) {
            throw new ResourceNotFoundException("Consumer has no location saved");
        }

        return locationMapper.toDto(consumer.getLocation());
    }

//    @Transactional
//    public LocationResponse deleteConsumerLocation(Long consumerId) {
//        log.info("Deleting location for consumer ID: {}", consumerId);
//
//        Consumer consumer = consumerRepository.findById(consumerId)
//                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));
//
//        if (consumer.getLocation() == null) {
//            return LocationResponse.builder()
//                    .success(true)
//                    .message("No location to delete")
//                    .build();
//        }
//
//        Long locationId = consumer.getLocation().getId();
//        consumer.setLocation(null);
//        consumerRepository.save(consumer);
//        locationRepository.deleteById(locationId);
//
//        log.info("Location deleted successfully for consumer ID: {}", consumerId);
//
//        return LocationResponse.builder()
//                .success(true)
//                .message("Location deleted successfully")
//                .build();
//    }

    // ====== PROVIDER LOCATION METHODS ======

    @Transactional
    public LocationResponse saveProviderLocation(Long providerId, LocationDTO locationDTO) {
        log.info("Saving location for provider ID: {}", providerId);

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        if (provider.getLocation() != null) {
            throw new IllegalStateException("Provider already has a location. Use update instead.");
        }

        // Create new location using mapper
        Location location = locationMapper.toEntity(locationDTO);
        location = locationRepository.save(location);

        provider.setLocation(location);
        providerRepository.save(provider);

        log.info("Location saved successfully for provider ID: {}", providerId);

        return locationMapper.toResponseWithMessage(location, "Location saved successfully", true);
    }

    @Transactional
    public LocationResponse updateProviderLocation(Long providerId, LocationDTO locationDTO) {
        log.info("Updating location for provider ID: {}", providerId);

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        if (provider.getLocation() == null) {
            throw new ResourceNotFoundException("Provider has no location to update. Please save location first.");
        }

        Location location = provider.getLocation();

        // Update existing location using mapper
        locationMapper.updateEntity(locationDTO, location);
        location = locationRepository.save(location);

        log.info("Location updated successfully for provider ID: {}", providerId);

        return locationMapper.toResponseWithMessage(location, "Location updated successfully", true);
    }

    @Transactional
    public LocationResponse patchProviderLocation(Long providerId, LocationDTO locationDTO) {
        log.info("Patching location for provider ID: {}", providerId);

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        if (provider.getLocation() == null) {
            throw new ResourceNotFoundException("Provider has no location to patch. Please save location first.");
        }

        Location location = provider.getLocation();

        // Manual patching
        if (locationDTO.getLatitude() != null) {
            location.setLatitude(locationDTO.getLatitude());
        }
        if (locationDTO.getLongitude() != null) {
            location.setLongitude(locationDTO.getLongitude());
        }
        if (locationDTO.getAddress() != null) {
            location.setAddress(locationDTO.getAddress());
        }
        if (locationDTO.getArea() != null) {
            location.setArea(locationDTO.getArea());
        }
        if (locationDTO.getCity() != null) {
            location.setCity(locationDTO.getCity());
        }

        location = locationRepository.save(location);

        log.info("Location patched successfully for provider ID: {}", providerId);

        return locationMapper.toResponseWithMessage(location, "Location patched successfully", true);
    }

    @Transactional(readOnly = true)
    public LocationDTO getProviderLocation(Long providerId) {
        log.info("Fetching location for provider ID: {}", providerId);

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        if (provider.getLocation() == null) {
            throw new ResourceNotFoundException("Provider has no location saved");
        }

        return locationMapper.toDto(provider.getLocation());
    }

//    @Transactional
//    public LocationResponse deleteProviderLocation(Long providerId) {
//        log.info("Deleting location for provider ID: {}", providerId);
//
//        Provider provider = providerRepository.findById(providerId)
//                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));
//
//        if (provider.getLocation() == null) {
//            return LocationResponse.builder()
//                    .success(true)
//                    .message("No location to delete")
//                    .build();
//        }
//
//        Long locationId = provider.getLocation().getId();
//        provider.setLocation(null);
//        providerRepository.save(provider);
//        locationRepository.deleteById(locationId);
//
//        log.info("Location deleted successfully for provider ID: {}", providerId);
//
//        return LocationResponse.builder()
//                .success(true)
//                .message("Location deleted successfully")
//                .build();
//    }

    // ==== CALCULATE DISTANCE BETWEEN TWO USERS (CONSUMER, PROVIDER)====

    /**
     * Calculate distance between a consumer and a provider
     * @param consumerId ID of the consumer
     * @param providerId ID of the provider
     * @return DistanceResponse with distance in kilometers
     */
    @Transactional(readOnly = true)
    public DistanceResponse calculateDistanceBetweenConsumerAndProvider(Long consumerId, Long providerId) {
        log.info("Calculating distance between consumer ID: {} and provider ID: {}", consumerId, providerId);

        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        if (consumer.getLocation() == null) {
            throw new ResourceNotFoundException("Consumer has no location saved");
        }

        if (provider.getLocation() == null) {
            throw new ResourceNotFoundException("Provider has no location saved");
        }

        Location consumerLocation = consumer.getLocation();
        Location providerLocation = provider.getLocation();

        double distance = calculateHaversineDistance(
                consumerLocation.getLatitude(),
                consumerLocation.getLongitude(),
                providerLocation.getLatitude(),
                providerLocation.getLongitude()
        );

        boolean isWithinServiceArea = false;
        if (provider.getServiceAreaRadius() != null) {
            isWithinServiceArea = distance <= provider.getServiceAreaRadius();
        }

        log.info("Distance calculated: {} km between consumer {} and provider {}", distance, consumerId, providerId);

        return DistanceResponse.builder()
                .consumerId(consumerId)
                .providerId(providerId)
                .distanceKm(distance)
                .consumerLocation(locationMapper.toDto(consumerLocation))
                .providerLocation(locationMapper.toDto(providerLocation))
                .withinServiceArea(isWithinServiceArea)
                .providerServiceArea(provider.getServiceAreaRadius())
                .build();
    }

    /**
     * Check if a provider serves a specific location
     * @param providerId ID of the provider
     * @param latitude Latitude of the location
     * @param longitude Longitude of the location
     * @return true if location is within provider's service area
     */
    @Transactional(readOnly = true)
    public Boolean isLocationWithinServiceArea(Long providerId, Double latitude, Double longitude) {
        log.info("Checking if location ({}, {}) is within service area of provider ID: {}", latitude, longitude, providerId);

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        if (provider.getLocation() == null) {
            throw new ResourceNotFoundException("Provider has no location saved");
        }

        if (provider.getServiceAreaRadius() == null) {
            return false;
        }

        double distance = calculateHaversineDistance(
                provider.getLocation().getLatitude(),
                provider.getLocation().getLongitude(),
                latitude,
                longitude
        );

        return distance <= provider.getServiceAreaRadius();
    }

    /**
     * Haversine formula implementation for distance calculation
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in kilometers, rounded to 2 decimal places
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth's radius in kilometers

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = R * c;

        // Round to 2 decimal places
        return Math.round(distance * 100.0) / 100.0;
    }

    // Add this method to your LocationService
    public List<ProviderSummaryResponse> findNearbyProviders(Double latitude, Double longitude,
                                                             Double radius, Long serviceTypeId) {
        log.info("Finding providers within {}km of ({}, {})", radius, latitude, longitude);

        List<Provider> allProviders;
        if (serviceTypeId != null) {
            allProviders = providerRepository.findByServiceTypeId(serviceTypeId);
        } else {
            allProviders = providerRepository.findAll();
        }

        return allProviders.stream()
                .filter(provider -> provider.getLocation() != null)
                .filter(provider -> {
                    double distance = calculateHaversineDistance(
                            latitude, longitude,
                            provider.getLocation().getLatitude(),
                            provider.getLocation().getLongitude()
                    );
                    return distance <= radius;
                })
                .map(provider -> {
                    double distance = calculateHaversineDistance(
                            latitude, longitude,
                            provider.getLocation().getLatitude(),
                            provider.getLocation().getLongitude()
                    );
                    ProviderSummaryResponse response = providerMapper.toProviderSummaryResponse(provider);
                    response.setDistance(distance);
                    return response;
                })
                .sorted(Comparator.comparing(ProviderSummaryResponse::getDistance))
                .collect(Collectors.toList());
    }
}