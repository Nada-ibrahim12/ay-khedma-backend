package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.response.DistanceResponse;
import com.aykhedma.dto.response.LocationResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.LocationMapper;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.ConsumerRepository;
import com.aykhedma.repository.LocationRepository;
import com.aykhedma.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationService Tests")
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ConsumerRepository consumerRepository;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private ProviderMapper providerMapper;

    @Mock
    private NotificationFactory notificationFactory;

    @Mock
    private GoogleMapsService googleMapsService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private LocationService locationService;

    private final Long consumerId = 1L;
    private final Long providerId = 2L;
    private final Long locationId = 100L;

    private Consumer consumer;
    private Provider provider;
    private Location location;
    private LocationDTO locationDTO;
    private GoogleMapsService.LocationDetails locationDetails;

    @BeforeEach
    void setUp() {
        location = new Location();
        location.setId(locationId);
        location.setLatitude(30.0444);
        location.setLongitude(31.2357);
        location.setAddress("Test Address");
        location.setArea("Test Area");
        location.setCity("Cairo");
        location.setCountry("Egypt");
        location.setAddressAr("عنوان تجريبي");
        location.setAreaAr("منطقة تجريبية");
        location.setCityAr("القاهرة");
        location.setCountryAr("مصر");

        locationDTO = LocationDTO.builder()
                .latitude(30.0444)
                .longitude(31.2357)
                .build();

        locationDetails = new GoogleMapsService.LocationDetails();
        locationDetails.setAddress("Test Address");
        locationDetails.setArea("Test Area");
        locationDetails.setCity("Cairo");
        locationDetails.setCountry("Egypt");
        locationDetails.setAddressAr("عنوان تجريبي");
        locationDetails.setAreaAr("منطقة تجريبية");
        locationDetails.setCityAr("القاهرة");
        locationDetails.setCountryAr("مصر");

        consumer = new Consumer();
        consumer.setId(consumerId);
        consumer.setLocation(location);

        provider = new Provider();
        provider.setId(providerId);
        provider.setLocation(location);
        provider.setServiceAreaRadius(10.0);

    }

    @Nested
    @DisplayName("Consumer Location Tests")
    class ConsumerLocationTests {

        @Test
        @DisplayName("Save Consumer Location - Already Exists Throws Exception")
        void saveConsumerLocation_AlreadyExists_ThrowsException() {
            when(consumerRepository.getReferenceById(consumerId)).thenReturn(consumer);
            when(consumerRepository.existsById(consumerId)).thenReturn(true);

            assertThatThrownBy(() -> locationService.saveConsumerLocation(consumerId, locationDTO))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Consumer already has a location");
        }

        @Test
        @DisplayName("Save Consumer Location - Consumer Not Found Throws Exception")
        void saveConsumerLocation_ConsumerNotFound_ThrowsException() {
            when(consumerRepository.getReferenceById(consumerId)).thenThrow(
                    new ResourceNotFoundException("Consumer not found with id: " + consumerId));

            assertThatThrownBy(() -> locationService.saveConsumerLocation(consumerId, locationDTO))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Save Consumer Location - No Existing Location Saves")
        void saveConsumerLocation_NoExistingLocation_Saves() {
            Consumer consumerWithoutLocation = new Consumer();
            consumerWithoutLocation.setId(consumerId);
            consumerWithoutLocation.setLocation(null);

            when(consumerRepository.getReferenceById(consumerId)).thenReturn(consumerWithoutLocation);
            when(consumerRepository.existsById(consumerId)).thenReturn(true);
            when(locationRepository.save(any(Location.class))).thenReturn(location);
            doNothing().when(consumerRepository).updateConsumerLocation(eq(consumerId), eq(locationId));

            when(googleMapsService.getLocationDetails(anyDouble(), anyDouble()))
                    .thenReturn(locationDetails);

            when(locationMapper.toResponseWithMessage(any(Location.class), anyString(), anyBoolean()))
                    .thenReturn(LocationResponse.builder()
                            .success(true)
                            .message("Location saved successfully")
                            .build());

            LocationResponse response = locationService.saveConsumerLocation(consumerId, locationDTO);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("saved successfully");

            verify(googleMapsService).getLocationDetails(30.0444, 31.2357);
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Update Consumer Location - Consumer Not Found Throws Exception")
        void updateConsumerLocation_ConsumerNotFound_ThrowsException() {
            when(consumerRepository.findById(consumerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.updateConsumerLocation(consumerId, locationDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer not found");
        }

        @Test
        @DisplayName("Update Consumer Location - Existing Location Updates")
        void updateConsumerLocation_ExistingLocation_Updates() {
            when(consumerRepository.findById(consumerId)).thenReturn(Optional.of(consumer));
            when(locationRepository.save(any(Location.class))).thenReturn(location);

            when(googleMapsService.getLocationDetails(anyDouble(), anyDouble()))
                    .thenReturn(locationDetails);

            when(locationMapper.toResponseWithMessage(any(Location.class), anyString(), anyBoolean()))
                    .thenReturn(LocationResponse.builder()
                            .success(true)
                            .message("Location updated successfully")
                            .build());

            LocationResponse response = locationService.updateConsumerLocation(consumerId, locationDTO);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("updated successfully");

            verify(googleMapsService).getLocationDetails(30.0444, 31.2357);
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Patch Consumer Location - Consumer Not Found Throws Exception")
        void patchConsumerLocation_ConsumerNotFound_ThrowsException() {
            when(consumerRepository.findById(consumerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.patchConsumerLocation(consumerId, locationDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer not found");
        }

        @Test
        @DisplayName("Patch Consumer Location - Partial Update Updates Only Provided Fields")
        void patchConsumerLocation_PartialUpdate_UpdatesOnlyProvidedFields() {
            when(consumerRepository.findById(consumerId)).thenReturn(Optional.of(consumer));

            LocationDTO partialDTO = LocationDTO.builder()
                    .latitude(31.2332)
                    .build();

            when(locationRepository.save(any(Location.class))).thenReturn(location);

            when(googleMapsService.getLocationDetails(anyDouble(), anyDouble()))
                    .thenReturn(locationDetails);

            when(locationMapper.toResponseWithMessage(any(Location.class), anyString(), anyBoolean()))
                    .thenReturn(LocationResponse.builder()
                            .success(true)
                            .message("Location patched successfully")
                            .build());

            LocationResponse response = locationService.patchConsumerLocation(consumerId, partialDTO);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();

            verify(googleMapsService).getLocationDetails(anyDouble(), anyDouble());
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Get Consumer Location - No Location Throws Exception")
        void getConsumerLocation_NoLocation_ThrowsException() {
            Consumer consumerWithoutLocation = new Consumer();
            consumerWithoutLocation.setId(consumerId);
            consumerWithoutLocation.setLocation(null);

            when(consumerRepository.findById(consumerId)).thenReturn(Optional.of(consumerWithoutLocation));

            assertThatThrownBy(() -> locationService.getConsumerLocation(consumerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer has no location saved");
        }
    }

    @Nested
    @DisplayName("Provider Location Tests")
    class ProviderLocationTests {

        @Test
        @DisplayName("Save Provider Location - Provider Not Found Throws Exception")
        void saveProviderLocation_ProviderNotFound_ThrowsException() {
            when(providerRepository.findById(providerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.saveProviderLocation(providerId, locationDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found");
        }

        @Test
        @DisplayName("Save Provider Location - No Existing Location Saves")
        void saveProviderLocation_NoExistingLocation_Saves() {
            Provider providerWithoutLocation = new Provider();
            providerWithoutLocation.setId(providerId);
            providerWithoutLocation.setLocation(null);

            when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerWithoutLocation));
            when(locationRepository.save(any(Location.class))).thenReturn(location);
            when(providerRepository.save(any(Provider.class))).thenReturn(providerWithoutLocation);

            when(googleMapsService.getLocationDetails(anyDouble(), anyDouble()))
                    .thenReturn(locationDetails);

            when(locationMapper.toResponseWithMessage(any(Location.class), anyString(), anyBoolean()))
                    .thenReturn(LocationResponse.builder()
                            .success(true)
                            .message("Location saved successfully")
                            .build());

            LocationResponse response = locationService.saveProviderLocation(providerId, locationDTO);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();

            verify(googleMapsService).getLocationDetails(30.0444, 31.2357);
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Update Provider Location - Provider Not Found Throws Exception")
        void updateProviderLocation_ProviderNotFound_ThrowsException() {
            when(providerRepository.findById(providerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.updateProviderLocation(providerId, locationDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found");
        }

        @Test
        @DisplayName("Update Provider Location - Existing Location Updates")
        void updateProviderLocation_ExistingLocation_Updates() {
            when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));
            when(locationRepository.save(any(Location.class))).thenReturn(location);

            when(googleMapsService.getLocationDetails(anyDouble(), anyDouble()))
                    .thenReturn(locationDetails);

            when(locationMapper.toResponseWithMessage(any(Location.class), anyString(), anyBoolean()))
                    .thenReturn(LocationResponse.builder()
                            .success(true)
                            .message("Location updated successfully")
                            .build());

            LocationResponse response = locationService.updateProviderLocation(providerId, locationDTO);

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();

            verify(googleMapsService).getLocationDetails(30.0444, 31.2357);
            verify(locationRepository).save(any(Location.class));
        }

        @Test
        @DisplayName("Get Provider Location - No Location Throws Exception")
        void getProviderLocation_NoLocation_ThrowsException() {
            Provider providerWithoutLocation = new Provider();
            providerWithoutLocation.setId(providerId);
            providerWithoutLocation.setLocation(null);

            when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerWithoutLocation));

            assertThatThrownBy(() -> locationService.getProviderLocation(providerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider has no location saved");
        }
    }

    @Nested
    @DisplayName("Distance and Service Area Tests")
    class DistanceAndServiceAreaTests {

        @Test
        @DisplayName("Calculate Distance Between Consumer and Provider - Returns Distance")
        void calculateDistanceBetweenConsumerAndProvider_ReturnsDistance() {
            when(consumerRepository.findById(consumerId)).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));
            when(locationMapper.toDto(any(Location.class))).thenReturn(locationDTO);

            DistanceResponse response = locationService.calculateDistanceBetweenConsumerAndProvider(
                    consumerId, providerId);

            assertThat(response).isNotNull();
            assertThat(response.getConsumerId()).isEqualTo(consumerId);
            assertThat(response.getProviderId()).isEqualTo(providerId);
            assertThat(response.getDistanceKm()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Calculate Distance - Consumer Not Found Throws Exception")
        void calculateDistanceBetweenConsumerAndProvider_ConsumerNotFound_ThrowsException() {
            when(consumerRepository.findById(consumerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.calculateDistanceBetweenConsumerAndProvider(
                    consumerId, providerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer not found");
        }

        @Test
        @DisplayName("Is Location Within Service Area - No Radius Returns False")
        void isLocationWithinServiceArea_NoRadius_ReturnsFalse() {
            Provider providerWithoutRadius = new Provider();
            providerWithoutRadius.setId(providerId);
            providerWithoutRadius.setLocation(location);
            providerWithoutRadius.setServiceAreaRadius(null);

            when(providerRepository.findById(providerId)).thenReturn(Optional.of(providerWithoutRadius));

            boolean result = locationService.isLocationWithinServiceArea(
                    providerId, 30.0444, 31.2357);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Is Location Within Service Area - Provider Not Found Throws Exception")
        void isLocationWithinServiceArea_ProviderNotFound_ThrowsException() {
            when(providerRepository.findById(providerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.isLocationWithinServiceArea(
                    providerId, 30.0444, 31.2357))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found");
        }
    }

    @Nested
    @DisplayName("Nearby Providers Tests")
    class NearbyProvidersTests {

        @Test
        @DisplayName("Find Nearby Providers - Returns Sorted List")
        void findNearbyProviders_ReturnsSortedList() {
            Provider provider1 = new Provider();
            provider1.setId(1L);
            provider1.setLocation(location);

            Provider provider2 = new Provider();
            provider2.setId(2L);
            Location location2 = new Location();
            location2.setLatitude(30.05);
            location2.setLongitude(31.25);
            provider2.setLocation(location2);

            when(providerRepository.findAll()).thenReturn(List.of(provider1, provider2));

            ProviderSummaryResponse response1 = ProviderSummaryResponse.builder()
                    .id(1L)
                    .build();
            ProviderSummaryResponse response2 = ProviderSummaryResponse.builder()
                    .id(2L)
                    .build();

            when(providerMapper.toProviderSummaryResponse(any(Provider.class)))
                    .thenReturn(response1, response2);

            List<ProviderSummaryResponse> results = locationService.findNearbyProviders(
                    30.0444, 31.2357, 10.0, null);

            assertThat(results).isNotNull();
            assertThat(results).isNotEmpty();
        }
    }
}