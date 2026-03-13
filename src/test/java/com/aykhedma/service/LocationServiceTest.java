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
import com.aykhedma.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Location Service Unit Tests")
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

    @InjectMocks
    private LocationService locationService;

    private final Long CONSUMER_ID = 1L;
    private final Long PROVIDER_ID = 2L;

    private Consumer consumer;
    private Provider provider;
    private LocationDTO locationDTO;
    private Location location;
    private LocationResponse locationResponse;

    @BeforeEach
    void setUp() {
        consumer = TestDataFactory.createConsumer(CONSUMER_ID);
        provider = TestDataFactory.createProvider(PROVIDER_ID);
        locationDTO = TestDataFactory.createLocationDTO();
        location = TestDataFactory.createLocation(10L);
        locationResponse = LocationResponse.builder()
                .id(10L)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .success(true)
                .message("OK")
                .build();
    }

    @Nested
    @DisplayName("Consumer Location Tests")
    class ConsumerLocationTests {

        @Test
        @DisplayName("Should save consumer location when none exists")
        void saveConsumerLocation_NoExistingLocation_Saves() {
            consumer.setLocation(null);

            when(consumerRepository.getReferenceById(CONSUMER_ID)).thenReturn(consumer);
            when(consumerRepository.existsById(CONSUMER_ID)).thenReturn(true);
            when(locationMapper.toEntity(locationDTO)).thenReturn(location);
            when(locationRepository.save(location)).thenReturn(location);
            when(locationMapper.toResponseWithMessage(location, "Location saved successfully", true))
                    .thenReturn(locationResponse);

            LocationResponse result = locationService.saveConsumerLocation(CONSUMER_ID, locationDTO);

            assertThat(result.getId()).isEqualTo(locationResponse.getId());
            verify(consumerRepository).updateConsumerLocation(CONSUMER_ID, location.getId());
        }

        @Test
        @DisplayName("Should throw when consumer already has location")
        void saveConsumerLocation_AlreadyExists_ThrowsException() {
            consumer.setLocation(location);

            when(consumerRepository.getReferenceById(CONSUMER_ID)).thenReturn(consumer);
            when(consumerRepository.existsById(CONSUMER_ID)).thenReturn(true);

            assertThatThrownBy(() -> locationService.saveConsumerLocation(CONSUMER_ID, locationDTO))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already has a location");

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when consumer not found")
        void saveConsumerLocation_ConsumerNotFound_ThrowsException() {
            when(consumerRepository.getReferenceById(CONSUMER_ID)).thenReturn(consumer);
            when(consumerRepository.existsById(CONSUMER_ID)).thenReturn(false);

            assertThatThrownBy(() -> locationService.saveConsumerLocation(CONSUMER_ID, locationDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer not found");
        }

        @Test
        @DisplayName("Should update consumer location")
        void updateConsumerLocation_ExistingLocation_Updates() {
            consumer.setLocation(location);

            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(locationRepository.save(location)).thenReturn(location);
            when(locationMapper.toResponseWithMessage(location, "Location updated successfully", true))
                    .thenReturn(locationResponse);

            LocationResponse result = locationService.updateConsumerLocation(CONSUMER_ID, locationDTO);

            assertThat(result.isSuccess()).isTrue();
            verify(locationMapper).updateEntity(locationDTO, location);
        }

        @Test
        @DisplayName("Should throw when updating consumer location and consumer not found")
        void updateConsumerLocation_ConsumerNotFound_ThrowsException() {
            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.updateConsumerLocation(CONSUMER_ID, locationDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer not found with id: " + CONSUMER_ID);

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should patch consumer location with partial data")
        void patchConsumerLocation_PartialUpdate_UpdatesOnlyProvidedFields() {
            consumer.setLocation(location);
            LocationDTO patchDto = LocationDTO.builder().city("Giza").build();

            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(locationMapper.toResponseWithMessage(any(Location.class), eq("Location patched successfully"), eq(true)))
                    .thenReturn(locationResponse);

            locationService.patchConsumerLocation(CONSUMER_ID, patchDto);

            assertThat(location.getCity()).isEqualTo("Giza");
            assertThat(location.getAddress()).isEqualTo(TestDataFactory.createLocation(10L).getAddress());
        }

        @Test
        @DisplayName("Should throw when patching consumer location and consumer not found")
        void patchConsumerLocation_ConsumerNotFound_ThrowsException() {
            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.patchConsumerLocation(CONSUMER_ID, locationDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer not found with id: " + CONSUMER_ID);

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when fetching consumer location and none exists")
        void getConsumerLocation_NoLocation_ThrowsException() {
            consumer.setLocation(null);

            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));

            assertThatThrownBy(() -> locationService.getConsumerLocation(CONSUMER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer has no location saved");
        }
    }

    @Nested
    @DisplayName("Provider Location Tests")
    class ProviderLocationTests {

        @Test
        @DisplayName("Should save provider location when none exists")
        void saveProviderLocation_NoExistingLocation_Saves() {
            provider.setLocation(null);

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(locationMapper.toEntity(locationDTO)).thenReturn(location);
            when(locationRepository.save(location)).thenReturn(location);
            when(providerRepository.save(provider)).thenReturn(provider);
            when(locationMapper.toResponseWithMessage(location, "Location saved successfully", true))
                    .thenReturn(locationResponse);

            LocationResponse result = locationService.saveProviderLocation(PROVIDER_ID, locationDTO);

            assertThat(result.isSuccess()).isTrue();
            verify(providerRepository).save(provider);
        }

        @Test
        @DisplayName("Should throw when saving provider location and provider not found")
        void saveProviderLocation_ProviderNotFound_ThrowsException() {
            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.saveProviderLocation(PROVIDER_ID, locationDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found with id: " + PROVIDER_ID);

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should update provider location")
        void updateProviderLocation_ExistingLocation_Updates() {
            provider.setLocation(location);

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(locationRepository.save(location)).thenReturn(location);
            when(locationMapper.toResponseWithMessage(location, "Location updated successfully", true))
                    .thenReturn(locationResponse);

            LocationResponse result = locationService.updateProviderLocation(PROVIDER_ID, locationDTO);

            assertThat(result.isSuccess()).isTrue();
            verify(locationMapper).updateEntity(locationDTO, location);
        }

        @Test
        @DisplayName("Should throw when updating provider location and provider not found")
        void updateProviderLocation_ProviderNotFound_ThrowsException() {
            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.updateProviderLocation(PROVIDER_ID, locationDTO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found with id: " + PROVIDER_ID);

            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when fetching provider location and none exists")
        void getProviderLocation_NoLocation_ThrowsException() {
            provider.setLocation(null);

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));

            assertThatThrownBy(() -> locationService.getProviderLocation(PROVIDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider has no location saved");
        }
    }

    @Nested
    @DisplayName("Distance and Service Area Tests")
    class DistanceAndServiceAreaTests {

        @Test
        @DisplayName("Should calculate distance between consumer and provider")
        void calculateDistanceBetweenConsumerAndProvider_ReturnsDistance() {
            consumer.setLocation(TestDataFactory.createLocation(1L));
            provider.setLocation(TestDataFactory.createLocation(2L));
            provider.setServiceAreaRadius(20.0);

            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(locationMapper.toDto(consumer.getLocation())).thenReturn(locationDTO);
            when(locationMapper.toDto(provider.getLocation())).thenReturn(locationDTO);

            DistanceResponse response = locationService.calculateDistanceBetweenConsumerAndProvider(CONSUMER_ID, PROVIDER_ID);

            assertThat(response.getConsumerId()).isEqualTo(CONSUMER_ID);
            assertThat(response.getProviderId()).isEqualTo(PROVIDER_ID);
            assertThat(response.getDistanceKm()).isNotNull();
            assertThat(response.getWithinServiceArea()).isTrue();
        }

        @Test
        @DisplayName("Should throw when consumer not found for distance calculation")
        void calculateDistanceBetweenConsumerAndProvider_ConsumerNotFound_ThrowsException() {
            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.calculateDistanceBetweenConsumerAndProvider(CONSUMER_ID, PROVIDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer not found with id: " + CONSUMER_ID);
        }

        @Test
        @DisplayName("Should return false when provider has no service area radius")
        void isLocationWithinServiceArea_NoRadius_ReturnsFalse() {
            provider.setLocation(location);
            provider.setServiceAreaRadius(null);

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));

            Boolean result = locationService.isLocationWithinServiceArea(PROVIDER_ID, 30.0, 31.0);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should throw when provider not found for service area check")
        void isLocationWithinServiceArea_ProviderNotFound_ThrowsException() {
            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> locationService.isLocationWithinServiceArea(PROVIDER_ID, 30.0, 31.0))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found with id: " + PROVIDER_ID);
        }
    }

    @Nested
    @DisplayName("Nearby Providers Tests")
    class NearbyProvidersTests {

        @Test
        @DisplayName("Should return providers sorted by distance")
        void findNearbyProviders_ReturnsSortedList() {
            Provider p1 = TestDataFactory.createProvider(1L);
            Provider p2 = TestDataFactory.createProvider(2L);
            p1.setLocation(TestDataFactory.createLocation(1L));
            p2.setLocation(TestDataFactory.createLocation(2L));

            when(providerRepository.findAll()).thenReturn(List.of(p2, p1));
            when(providerMapper.toProviderSummaryResponse(p1)).thenReturn(ProviderSummaryResponse.builder().id(1L).build());
            when(providerMapper.toProviderSummaryResponse(p2)).thenReturn(ProviderSummaryResponse.builder().id(2L).build());

            List<ProviderSummaryResponse> result = locationService.findNearbyProviders(30.0, 31.0, 100.0, null);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getDistance()).isLessThanOrEqualTo(result.get(1).getDistance());
        }
    }
}
