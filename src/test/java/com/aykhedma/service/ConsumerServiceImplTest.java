package com.aykhedma.service;

import com.aykhedma.dto.request.ConsumerProfileRequest;
import com.aykhedma.dto.response.ConsumerResponse;
import com.aykhedma.dto.response.ProfileResponse;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ConsumerMapper;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.ConsumerRepository;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Consumer Service Unit Tests")
class ConsumerServiceImplTest {

    @Mock
    private ConsumerRepository consumerRepository;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private ConsumerMapper consumerMapper;

    @Mock
    private ProviderMapper providerMapper;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private LocationService locationService;

    @InjectMocks
    private ConsumerServiceImpl consumerService;

    @Captor
    private ArgumentCaptor<Consumer> consumerCaptor;

    private Consumer consumer;
    private ConsumerResponse consumerResponse;
    private Provider provider;
    private final Long CONSUMER_ID = 1L;
    private final Long PROVIDER_ID = 2L;
    private final Long NON_EXISTENT_ID = 999L;

    @BeforeEach
    void setUp() {
        consumer = TestDataFactory.createConsumer(CONSUMER_ID);
        consumerResponse = TestDataFactory.createConsumerResponse(CONSUMER_ID);
        provider = TestDataFactory.createProvider(PROVIDER_ID);
    }

    @Nested
    @DisplayName("Get Consumer Profile Tests")
    class GetConsumerProfileTests {

        @Test
        @DisplayName("Should return consumer profile when consumer exists")
        void getConsumerProfile_ExistingId_ReturnsConsumerResponse() {

            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(consumerMapper.toConsumerResponse(consumer)).thenReturn(consumerResponse);

            ConsumerResponse result = consumerService.getConsumerProfile(CONSUMER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(CONSUMER_ID);

            verify(consumerRepository).findById(CONSUMER_ID);
            verify(consumerMapper).toConsumerResponse(consumer);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when consumer does not exist")
        void getConsumerProfile_NonExistingId_ThrowsException() {
            when(consumerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> consumerService.getConsumerProfile(NON_EXISTENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer not found with id: " + NON_EXISTENT_ID);

            verify(consumerRepository).findById(NON_EXISTENT_ID);
            verify(consumerMapper, never()).toConsumerResponse(any());
        }
    }

    @Nested
    @DisplayName("Update Consumer Profile Tests")
    class UpdateConsumerProfileTests {

        @Test
        @DisplayName("Should update all fields when request has all fields")
        void updateConsumerProfile_AllFields_UpdatesEverything() {
            ConsumerProfileRequest request = TestDataFactory.createConsumerProfileRequest();

            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(consumerRepository.save(any(Consumer.class))).thenReturn(consumer);
            when(consumerMapper.toConsumerResponse(any(Consumer.class))).thenReturn(consumerResponse);

            ConsumerResponse result = consumerService.updateConsumerProfile(CONSUMER_ID, request);

            assertThat(result).isNotNull();

            verify(locationService).updateConsumerLocation(eq(CONSUMER_ID), eq(request.getLocation()));
            verify(consumerRepository).save(consumer);
        }

        @Test
        @DisplayName("Should only update name when only name is provided")
        void updateConsumerProfile_OnlyName_UpdatesOnlyName() {
            ConsumerProfileRequest request = TestDataFactory.createPartialProfileRequest();

            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(consumerRepository.save(any(Consumer.class))).thenReturn(consumer);
            when(consumerMapper.toConsumerResponse(any(Consumer.class))).thenReturn(consumerResponse);

            consumerService.updateConsumerProfile(CONSUMER_ID, request);

            assertThat(consumer.getName()).isEqualTo(request.getName());

            verify(locationService, never()).updateConsumerLocation(any(), any());
            verify(consumerRepository).save(consumer);
        }
    }

    @Nested
    @DisplayName("Save Provider Tests")
    class SaveProviderTests {

        @Test
        @DisplayName("Should save provider successfully when not already saved")
        void saveProvider_NotAlreadySaved_ReturnsSuccess() {
            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(consumerRepository.isProviderSavedNative(CONSUMER_ID, PROVIDER_ID)).thenReturn(false);

            ProfileResponse response = consumerService.saveProvider(CONSUMER_ID, PROVIDER_ID);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Provider saved successfully");

            verify(consumerRepository).insertSavedProvider(CONSUMER_ID, PROVIDER_ID);
        }

        @Test
        @DisplayName("Should return false when provider already saved")
        void saveProvider_AlreadySaved_ReturnsFailure() {
            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(consumerRepository.isProviderSavedNative(CONSUMER_ID, PROVIDER_ID)).thenReturn(true);

            ProfileResponse response = consumerService.saveProvider(CONSUMER_ID, PROVIDER_ID);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("Provider already saved");

            verify(consumerRepository, never()).insertSavedProvider(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("Remove Saved Provider Tests")
    class RemoveSavedProviderTests {

        @Test
        @DisplayName("Should remove saved provider successfully")
        void removeSavedProvider_ValidIds_ReturnsSuccess() {
            // Arrange
            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(consumerRepository.deleteSavedProvider(CONSUMER_ID, PROVIDER_ID)).thenReturn(1);

            ProfileResponse response = consumerService.removeSavedProvider(CONSUMER_ID, PROVIDER_ID);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Provider removed successfully");
        }

        @Test
        @DisplayName("Should return false when provider not in saved list")
        void removeSavedProvider_NotSaved_ReturnsFailure() {
            when(consumerRepository.findById(CONSUMER_ID)).thenReturn(Optional.of(consumer));
            when(consumerRepository.deleteSavedProvider(CONSUMER_ID, PROVIDER_ID)).thenReturn(0);

            ProfileResponse response = consumerService.removeSavedProvider(CONSUMER_ID, PROVIDER_ID);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("Provider was not in saved list");
        }
    }
}