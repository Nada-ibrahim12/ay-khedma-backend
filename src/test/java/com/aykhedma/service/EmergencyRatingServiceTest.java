package com.aykhedma.service;

import com.aykhedma.dto.request.EmergencyRatingRequest;
import com.aykhedma.dto.request.ProviderEmergencyRatingRequest;
import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ForbiddenException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.EmergencyRequestMapper;
import com.aykhedma.mapper.ProviderResponseMapper;
import com.aykhedma.model.emergency.EmergencyRequest;
import com.aykhedma.model.emergency.EmergencyRequestStatus;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Emergency Request Rating Tests")
class EmergencyRatingServiceTest
{
    @Mock
    private EmergencyRequestRepository emergencyRequestRepository;
    @Mock
    private ProviderResponseRepository providerResponseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ConsumerRepository consumerRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private ServiceTypeRepository serviceTypeRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private EmergencyRequestMapper emergencyRequestMapper;
    @Mock
    private ProviderResponseMapper providerResponseMapper;
    @Mock
    private Scheduler scheduler;
    @Mock
    private GoogleMapsService googleMapsService;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;
    @Mock
    private NotificationFactory notificationFactory;
    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private EmergencyRequestServiceImpl emergencyRequestService;

    private Consumer consumer;
    private Provider provider;
    private EmergencyRequest emergencyRequest;

    @BeforeEach
    void setUp()
    {
        provider = Provider.builder()
                .id(1L)
                .name("Provider One")
                .role(UserType.PROVIDER)
                .averageRating(0.0)
                .averagePunctualityRating(0.0)
                .averageCommitmentRating(0.0)
                .averageQualityOfWorkRating(0.0)
                .totalBookings(0)
                .completedJobs(0)
                .cancelledBookings(0)
                .totalRequests(0)
                .build();

        consumer = Consumer.builder()
                .id(2L)
                .name("Consumer One")
                .role(UserType.CONSUMER)
                .totalBookings(0)
                .cancelledBookings(0)
                .build();

        emergencyRequest = EmergencyRequest.builder()
                .id(100L)
                .consumer(consumer)
                .selectedProvider(provider)
                .status(EmergencyRequestStatus.COMPLETED)
                .build();
    }

    // ========================================================================
    // Consumer rates provider for emergency request (submitEmergencyRequestRating)
    // ========================================================================
    @Nested
    @DisplayName("Submit Emergency Rating (Consumer → Provider) Tests")
    class SubmitEmergencyRatingTest
    {
        private EmergencyRatingRequest ratingRequest;

        @BeforeEach
        void setUp()
        {
            ratingRequest = EmergencyRatingRequest.builder()
                    .emergencyRequestId(emergencyRequest.getId())
                    .punctualityRating(5)
                    .commitmentRating(4)
                    .qualityOfWorkRating(3)
                    .review("Fast emergency response!")
                    .build();
        }

        @Test
        @DisplayName("Successfully Submit Emergency Rating For Completed Request")
        void submitEmergencyRatingSuccessTest()
        {
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(0L);
            when(emergencyRequestRepository.countBySelectedProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            EmergencyRequestResponse response = emergencyRequestService.submitEmergencyRequestRating(consumer.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(emergencyRequest.getPunctualityRating()).isEqualTo(5.0);
            assertThat(emergencyRequest.getCommitmentRating()).isEqualTo(4.0);
            assertThat(emergencyRequest.getQualityOfWorkRating()).isEqualTo(3.0);

            // Overall = (5 + 4 + 3) / 3 = 4.0
            assertThat(emergencyRequest.getConsumerRating()).isEqualTo(4.0);
            assertThat(emergencyRequest.getConsumerReview()).isEqualTo("Fast emergency response!");
            verify(emergencyRequestRepository).save(emergencyRequest);
            verify(providerRepository).save(provider);
        }

        @Test
        @DisplayName("Successfully Submit Emergency Rating For Expired Accepted Request")
        void submitEmergencyRatingExpiredAcceptedTest()
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.EXPIRED);
            // selectedProvider is not null → treated as "accepted"

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(0L);
            when(emergencyRequestRepository.countBySelectedProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            EmergencyRequestResponse response = emergencyRequestService.submitEmergencyRequestRating(consumer.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(emergencyRequest.getConsumerRating()).isNotNull();
        }

        @Test
        @DisplayName("Update Provider Averages For First Emergency Rating")
        void submitEmergencyRatingFirstRatingUpdatesProviderTest()
        {
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(0L);
            when(emergencyRequestRepository.countBySelectedProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().build());

            emergencyRequestService.submitEmergencyRequestRating(consumer.getId(), ratingRequest);

            // First rating – should set directly
            assertThat(provider.getAveragePunctualityRating()).isEqualTo(5.0);
            assertThat(provider.getAverageCommitmentRating()).isEqualTo(4.0);
            assertThat(provider.getAverageQualityOfWorkRating()).isEqualTo(3.0);
            assertThat(provider.getAverageRating()).isEqualTo(4.0);
            verify(providerRepository).save(provider);
        }

        @Test
        @DisplayName("Update Provider Averages With Rolling Average For Subsequent Emergency Rating")
        void submitEmergencyRatingSubsequentRatingUpdatesProviderTest()
        {
            provider.setAverageRating(3.0);
            provider.setAveragePunctualityRating(3.0);
            provider.setAverageCommitmentRating(3.0);
            provider.setAverageQualityOfWorkRating(3.0);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            // 1 rated booking + 1 rated emergency (this one) = totalRatedCount = 2
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(emergencyRequestRepository.countBySelectedProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().build());

            emergencyRequestService.submitEmergencyRequestRating(consumer.getId(), ratingRequest);

            // Rolling average: punctuality: (3.0*1 + 5.0)/2 = 4.0
            assertThat(provider.getAveragePunctualityRating()).isEqualTo(4.0);
            verify(providerRepository).save(provider);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException When Emergency Request Not Found")
        void submitEmergencyRatingNotFoundTest()
        {
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emergencyRequestService.submitEmergencyRequestRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Emergency request not found");
        }

        @Test
        @DisplayName("Throw ForbiddenException When Emergency Request Does Not Belong To Consumer")
        void submitEmergencyRatingWrongConsumerTest()
        {
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            assertThatThrownBy(() -> emergencyRequestService.submitEmergencyRequestRating(99L, ratingRequest))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Emergency request does not belong to this consumer");
        }

        @Test
        @DisplayName("Throw BadRequestException For Broadcasting Emergency Request")
        void submitEmergencyRatingBroadcastingTest()
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.BROADCASTING);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            assertThatThrownBy(() -> emergencyRequestService.submitEmergencyRequestRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed emergency requests");
        }

        @Test
        @DisplayName("Throw BadRequestException For Cancelled Emergency Request")
        void submitEmergencyRatingCancelledTest()
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.CANCELLED);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            assertThatThrownBy(() -> emergencyRequestService.submitEmergencyRequestRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed emergency requests");
        }

        @Test
        @DisplayName("Throw BadRequestException For Expired Emergency Without Selected Provider")
        void submitEmergencyRatingExpiredNoProviderTest()
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.EXPIRED);
            emergencyRequest.setSelectedProvider(null);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            assertThatThrownBy(() -> emergencyRequestService.submitEmergencyRequestRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed emergency requests or expired emergency requests that were accepted.");
        }

        @Test
        @DisplayName("Throw BadRequestException When Already Rated Emergency Request")
        void submitEmergencyRatingAlreadyRatedTest()
        {
            emergencyRequest.setConsumerRating(4.0);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            assertThatThrownBy(() -> emergencyRequestService.submitEmergencyRequestRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("You have already rated this emergency request");
        }

        @Test
        @DisplayName("Submit Emergency Rating With Null Review Succeeds")
        void submitEmergencyRatingNullReviewTest()
        {
            ratingRequest.setReview(null);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(0L);
            when(emergencyRequestRepository.countBySelectedProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().build());

            EmergencyRequestResponse response = emergencyRequestService.submitEmergencyRequestRating(consumer.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(emergencyRequest.getConsumerReview()).isNull();
        }
    }

    // ========================================================================
    // Provider rates consumer for emergency request (submitConsumerEmergencyRequestRating)
    // ========================================================================
    @Nested
    @DisplayName("Submit Consumer Emergency Rating (Provider → Consumer) Tests")
    class SubmitConsumerEmergencyRatingTest
    {
        private ProviderEmergencyRatingRequest ratingRequest;

        @BeforeEach
        void setUp()
        {
            ratingRequest = ProviderEmergencyRatingRequest.builder()
                    .emergencyRequestId(emergencyRequest.getId())
                    .rating(4)
                    .review("Cooperative client!")
                    .build();
        }

        @Test
        @DisplayName("Successfully Submit Consumer Emergency Rating")
        void submitConsumerEmergencyRatingSuccessTest()
        {
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(0L);
            when(emergencyRequestRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            EmergencyRequestResponse response = emergencyRequestService.submitConsumerEmergencyRequestRating(provider.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(emergencyRequest.getProviderRating()).isEqualTo(4.0);
            assertThat(emergencyRequest.getProviderReview()).isEqualTo("Cooperative client!");
            verify(emergencyRequestRepository).save(emergencyRequest);
            verify(consumerRepository).save(consumer);
        }

        @Test
        @DisplayName("Successfully Submit Consumer Emergency Rating For Expired Accepted")
        void submitConsumerEmergencyRatingExpiredAcceptedTest()
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.EXPIRED);
            // selectedProvider is not null → treated as accepted

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(0L);
            when(emergencyRequestRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().build());

            EmergencyRequestResponse response = emergencyRequestService.submitConsumerEmergencyRequestRating(provider.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(emergencyRequest.getProviderRating()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("Update Consumer Average Rating For First Emergency Rating")
        void submitConsumerEmergencyRatingFirstRatingTest()
        {
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(0L);
            when(emergencyRequestRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().build());

            emergencyRequestService.submitConsumerEmergencyRequestRating(provider.getId(), ratingRequest);

            assertThat(consumer.getAverageRating()).isEqualTo(4.0);
            verify(consumerRepository).save(consumer);
        }

        @Test
        @DisplayName("Update Consumer Average Rating With Rolling Average")
        void submitConsumerEmergencyRatingSubsequentTest()
        {
            consumer.setAverageRating(3.0);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            // 1 booking + 1 emergency = totalRatedCount = 2
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(emergencyRequestRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().build());

            emergencyRequestService.submitConsumerEmergencyRequestRating(provider.getId(), ratingRequest);

            // Rolling average: (3.0*1 + 4.0)/2 = 3.5
            assertThat(consumer.getAverageRating()).isEqualTo(3.5);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException When Emergency Request Not Found")
        void submitConsumerEmergencyRatingNotFoundTest()
        {
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emergencyRequestService.submitConsumerEmergencyRequestRating(provider.getId(), ratingRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Emergency request not found");
        }

        @Test
        @DisplayName("Throw ForbiddenException When Emergency Request Does Not Belong To Provider")
        void submitConsumerEmergencyRatingWrongProviderTest()
        {
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            assertThatThrownBy(() -> emergencyRequestService.submitConsumerEmergencyRequestRating(99L, ratingRequest))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Emergency request does not belong to this provider");
        }

        @Test
        @DisplayName("Throw ForbiddenException When No Selected Provider On Emergency Request")
        void submitConsumerEmergencyRatingNoSelectedProviderTest()
        {
            emergencyRequest.setSelectedProvider(null);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            assertThatThrownBy(() -> emergencyRequestService.submitConsumerEmergencyRequestRating(provider.getId(), ratingRequest))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Emergency request does not belong to this provider");
        }

        @Test
        @DisplayName("Throw BadRequestException For Broadcasting Emergency Request")
        void submitConsumerEmergencyRatingBroadcastingTest()
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.BROADCASTING);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            assertThatThrownBy(() -> emergencyRequestService.submitConsumerEmergencyRequestRating(provider.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed emergency requests");
        }

        @Test
        @DisplayName("Throw BadRequestException For Cancelled Emergency Request")
        void submitConsumerEmergencyRatingCancelledTest()
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.CANCELLED);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            assertThatThrownBy(() -> emergencyRequestService.submitConsumerEmergencyRequestRating(provider.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed emergency requests");
        }

        @Test
        @DisplayName("Throw BadRequestException When Already Rated Emergency Request")
        void submitConsumerEmergencyRatingAlreadyRatedTest()
        {
            emergencyRequest.setProviderRating(3.5);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            assertThatThrownBy(() -> emergencyRequestService.submitConsumerEmergencyRequestRating(provider.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("You have already rated this emergency request");
        }

        @Test
        @DisplayName("Submit Consumer Emergency Rating With Null Review Succeeds")
        void submitConsumerEmergencyRatingNullReviewTest()
        {
            ratingRequest.setReview(null);

            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(0L);
            when(emergencyRequestRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().build());

            EmergencyRequestResponse response = emergencyRequestService.submitConsumerEmergencyRequestRating(provider.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(emergencyRequest.getProviderReview()).isNull();
        }
    }
}
