package com.aykhedma.service;

import com.aykhedma.dto.request.ProviderRatingRequest;
import com.aykhedma.dto.request.RatingRequest;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.ConsumerReviewResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ForbiddenException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.BookingMapper;
import com.aykhedma.model.booking.*;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.*;
import com.aykhedma.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Rating & Review Service Tests")
class RatingServiceTest
{
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ConsumerRepository consumerRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private WorkingDayRepository workingDayRepository;
    @Mock
    private TimeSlotRepository timeSlotRepository;
    @Mock
    private BookingMapper bookingMapper;
    @Mock
    private ProviderService providerService;
    @Mock
    private NotificationFactory notificationFactory;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private Consumer consumer;
    private Provider provider;
    private Booking booking;
    private ServiceType serviceType;

    @BeforeEach
    void setUp()
    {
        serviceType = ServiceType.builder()
                .id(1L)
                .name("Plumbing")
                .build();

        Schedule schedule = Schedule.builder()
                .id(1L)
                .build();

        provider = Provider.builder()
                .id(1L)
                .name("Provider One")
                .role(UserType.PROVIDER)
                .serviceType(serviceType)
                .schedule(schedule)
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

        // Default booking: completed, service started more than 30 minutes ago
        booking = Booking.builder()
                .id(10L)
                .consumer(consumer)
                .provider(provider)
                .serviceType(serviceType)
                .requestedDate(LocalDate.now().minusDays(1))
                .requestedStartTime(LocalTime.of(10, 0))
                .status(BookingStatus.COMPLETED)
                .acceptedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    // ========================================================================
    // Consumer submits rating for provider (submitRating)
    // ========================================================================
    @Nested
    @DisplayName("Submit Rating (Consumer → Provider) Tests")
    class SubmitRatingTest
    {
        private RatingRequest ratingRequest;

        @BeforeEach
        void setUp()
        {
            ratingRequest = RatingRequest.builder()
                    .bookingId(booking.getId())
                    .punctualityRating(5)
                    .commitmentRating(4)
                    .qualityOfWorkRating(3)
                    .review("Great service!")
                    .build();
        }

        @Test
        @DisplayName("Successfully Submit Rating For Completed Booking")
        void submitRatingSuccessCompletedTest()
        {
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.submitRating(consumer.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(booking.getPunctualityRating()).isEqualTo(5.0);
            assertThat(booking.getCommitmentRating()).isEqualTo(4.0);
            assertThat(booking.getQualityOfWorkRating()).isEqualTo(3.0);

            // Overall = (5 + 4 + 3) / 3 = 4.0
            assertThat(booking.getConsumerRating()).isEqualTo(4.0);
            assertThat(booking.getConsumerReview()).isEqualTo("Great service!");
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
            verify(bookingRepository).save(booking);
            verify(providerRepository).save(provider);
        }

        @Test
        @DisplayName("Successfully Submit Rating For Expired Accepted Booking")
        void submitRatingSuccessExpiredAcceptedTest()
        {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setAcceptedAt(LocalDateTime.now().minusDays(1));

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.submitRating(consumer.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(booking.getConsumerRating()).isNotNull();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
            verify(bookingRepository).save(booking);
        }

        @Test
        @DisplayName("Calculate Correct Overall Rating Rounded To 1 Decimal")
        void submitRatingOverallCalculationTest()
        {
            ratingRequest.setPunctualityRating(4);
            ratingRequest.setCommitmentRating(3);
            ratingRequest.setQualityOfWorkRating(5);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            bookingService.submitRating(consumer.getId(), ratingRequest);

            // Overall = (4 + 3 + 5) / 3 = 4.0
            assertThat(booking.getConsumerRating()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("Update Provider Averages For First Rating")
        void submitRatingFirstRatingUpdatesProviderTest()
        {
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            bookingService.submitRating(consumer.getId(), ratingRequest);

            // First rating – should set directly
            assertThat(provider.getAveragePunctualityRating()).isEqualTo(5.0);
            assertThat(provider.getAverageCommitmentRating()).isEqualTo(4.0);
            assertThat(provider.getAverageQualityOfWorkRating()).isEqualTo(3.0);
            verify(providerRepository).save(provider);
        }

        @Test
        @DisplayName("Update Provider Averages With Rolling Average For Subsequent Rating")
        void submitRatingSubsequentRatingUpdatesProviderTest()
        {
            provider.setAverageRating(4.0);
            provider.setAveragePunctualityRating(4.0);
            provider.setAverageCommitmentRating(4.0);
            provider.setAverageQualityOfWorkRating(4.0);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(2L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            bookingService.submitRating(consumer.getId(), ratingRequest);

            // Rolling average: (4.0 * 1 + 5.0) / 2 = 4.5
            assertThat(provider.getAveragePunctualityRating()).isEqualTo(4.5);
            verify(providerRepository).save(provider);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException When Booking Not Found")
        void submitRatingBookingNotFoundTest()
        {
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.submitRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Booking not found");
        }

        @Test
        @DisplayName("Throw ForbiddenException When Booking Does Not Belong To Consumer")
        void submitRatingWrongConsumerTest()
        {
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitRating(99L, ratingRequest))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Booking does not belong to this consumer");
        }

        @Test
        @DisplayName("Throw BadRequestException When Rating Before 30 Minutes After Service Start")
        void submitRatingTooEarlyTest()
        {
            booking.setRequestedDate(LocalDate.now());
            booking.setRequestedStartTime(LocalTime.now().plusMinutes(10));

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating is allowed only 30 minutes after service start");
        }

        @Test
        @DisplayName("Throw BadRequestException For Pending Booking")
        void submitRatingPendingBookingTest()
        {
            booking.setStatus(BookingStatus.PENDING);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed bookings or expired bookings that were accepted.");
        }

        @Test
        @DisplayName("Throw BadRequestException For Declined Booking")
        void submitRatingDeclinedBookingTest()
        {
            booking.setStatus(BookingStatus.DECLINED);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed bookings");
        }

        @Test
        @DisplayName("Throw BadRequestException For Cancelled Booking")
        void submitRatingCancelledBookingTest()
        {
            booking.setStatus(BookingStatus.CANCELLED);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed bookings");
        }

        @Test
        @DisplayName("Throw BadRequestException For Expired Booking Without Acceptance")
        void submitRatingExpiredNotAcceptedTest()
        {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setAcceptedAt(null);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed bookings or expired bookings that were accepted.");
        }

        @Test
        @DisplayName("Throw BadRequestException When Consumer Already Rated Booking")
        void submitRatingAlreadyRatedTest()
        {
            booking.setConsumerRating(4.5);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("You have already rated this booking");
        }

        @Test
        @DisplayName("Submit Rating With Null Review Succeeds")
        void submitRatingWithNullReviewTest()
        {
            ratingRequest.setReview(null);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.submitRating(consumer.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(booking.getConsumerReview()).isNull();
            verify(bookingRepository).save(booking);
        }

        @Test
        @DisplayName("Submit Rating With All 1s Calculates Correct Average")
        void submitRatingAllOnesTest()
        {
            ratingRequest.setPunctualityRating(1);
            ratingRequest.setCommitmentRating(1);
            ratingRequest.setQualityOfWorkRating(1);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            bookingService.submitRating(consumer.getId(), ratingRequest);

            assertThat(booking.getConsumerRating()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Submit Rating With All 5s Calculates Correct Average")
        void submitRatingAllFivesTest()
        {
            ratingRequest.setPunctualityRating(5);
            ratingRequest.setCommitmentRating(5);
            ratingRequest.setQualityOfWorkRating(5);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            bookingService.submitRating(consumer.getId(), ratingRequest);

            assertThat(booking.getConsumerRating()).isEqualTo(5.0);
        }
    }

    // ========================================================================
    // Provider submits rating for consumer (submitConsumerRating)
    // ========================================================================
    @Nested
    @DisplayName("Submit Consumer Rating (Provider → Consumer) Tests")
    class SubmitConsumerRatingTest
    {
        private ProviderRatingRequest providerRatingRequest;

        @BeforeEach
        void setUp()
        {
            providerRatingRequest = ProviderRatingRequest.builder()
                    .bookingId(booking.getId())
                    .rating(4)
                    .review("Good client!")
                    .build();
        }

        @Test
        @DisplayName("Successfully Submit Consumer Rating For Completed Booking")
        void submitConsumerRatingSuccessTest()
        {
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.submitConsumerRating(provider.getId(), providerRatingRequest);

            assertThat(response).isNotNull();
            assertThat(booking.getProviderRating()).isEqualTo(4.0);
            assertThat(booking.getProviderReview()).isEqualTo("Good client!");
            verify(bookingRepository).save(booking);
            verify(consumerRepository).save(consumer);
        }

        @Test
        @DisplayName("Successfully Submit Consumer Rating For Expired Accepted Booking")
        void submitConsumerRatingExpiredAcceptedTest()
        {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setAcceptedAt(LocalDateTime.now().minusDays(1));

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.submitConsumerRating(provider.getId(), providerRatingRequest);

            assertThat(response).isNotNull();
            assertThat(booking.getProviderRating()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("Update Consumer Average Rating For First Rating")
        void submitConsumerRatingFirstRatingTest()
        {
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            bookingService.submitConsumerRating(provider.getId(), providerRatingRequest);

            assertThat(consumer.getAverageRating()).isEqualTo(4.0);
            verify(consumerRepository).save(consumer);
        }

        @Test
        @DisplayName("Update Consumer Average Rating With Rolling Average For Subsequent Rating")
        void submitConsumerRatingSubsequentRatingTest()
        {
            consumer.setAverageRating(3.0);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(2L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            bookingService.submitConsumerRating(provider.getId(), providerRatingRequest);

            // Rolling average: (3.0 * 1 + 4.0) / 2 = 3.5
            assertThat(consumer.getAverageRating()).isEqualTo(3.5);
        }

        @Test
        @DisplayName("Send Notification To Consumer After Provider Rating")
        void submitConsumerRatingSendsNotificationTest()
        {
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            bookingService.submitConsumerRating(provider.getId(), providerRatingRequest);

            verify(notificationFactory).send(eq(consumer.getId()), any(), any());
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException When Booking Not Found")
        void submitConsumerRatingBookingNotFoundTest()
        {
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.submitConsumerRating(provider.getId(), providerRatingRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Booking not found");
        }

        @Test
        @DisplayName("Throw ForbiddenException When Booking Does Not Belong To Provider")
        void submitConsumerRatingWrongProviderTest()
        {
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitConsumerRating(99L, providerRatingRequest))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Booking does not belong to this provider");
        }

        @Test
        @DisplayName("Throw BadRequestException When Rating Before 30 Minutes After Service Start")
        void submitConsumerRatingTooEarlyTest()
        {
            booking.setRequestedDate(LocalDate.now());
            booking.setRequestedStartTime(LocalTime.now().plusMinutes(10));

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitConsumerRating(provider.getId(), providerRatingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating is allowed only 30 minutes after service start");
        }

        @Test
        @DisplayName("Throw BadRequestException For Pending Booking")
        void submitConsumerRatingPendingBookingTest()
        {
            booking.setStatus(BookingStatus.PENDING);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitConsumerRating(provider.getId(), providerRatingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed bookings or expired bookings that were accepted.");
        }

        @Test
        @DisplayName("Throw BadRequestException For Expired Booking Without Acceptance")
        void submitConsumerRatingExpiredNotAcceptedTest()
        {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setAcceptedAt(null);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitConsumerRating(provider.getId(), providerRatingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed bookings or expired bookings that were accepted.");
        }

        @Test
        @DisplayName("Throw BadRequestException When Provider Already Rated Booking")
        void submitConsumerRatingAlreadyRatedTest()
        {
            booking.setProviderRating(3.5);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitConsumerRating(provider.getId(), providerRatingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("You have already rated this booking");
        }

        @Test
        @DisplayName("Submit Consumer Rating With Null Review Succeeds")
        void submitConsumerRatingNullReviewTest()
        {
            providerRatingRequest.setReview(null);

            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.submitConsumerRating(provider.getId(), providerRatingRequest);

            assertThat(response).isNotNull();
            assertThat(booking.getProviderReview()).isNull();
        }

        @Test
        @DisplayName("Notification Failure Does Not Fail Rating Submission")
        void submitConsumerRatingNotificationFailureTest()
        {
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());
            doThrow(new RuntimeException("Push failed")).when(notificationFactory).send(any(), any(), any());

            BookingResponse response = bookingService.submitConsumerRating(provider.getId(), providerRatingRequest);

            assertThat(response).isNotNull();
            assertThat(booking.getProviderRating()).isEqualTo(4.0);
        }
    }

    // ========================================================================
    // Get Consumer Reviews
    // ========================================================================
    @Nested
    @DisplayName("Get Consumer Reviews Tests")
    class GetConsumerReviewsTest
    {
        @Test
        @DisplayName("Successfully Get Consumer Reviews")
        void getConsumerReviewsSuccessTest()
        {
            Booking ratedBooking = Booking.builder()
                    .id(20L)
                    .consumer(consumer)
                    .provider(provider)
                    .providerRating(4.5)
                    .providerReview("Excellent client")
                    .completedAt(LocalDateTime.now().minusDays(2))
                    .build();

            when(consumerRepository.existsById(consumer.getId())).thenReturn(true);
            when(bookingRepository.findByConsumerIdAndProviderRatingIsNotNull(consumer.getId()))
                    .thenReturn(List.of(ratedBooking));

            List<ConsumerReviewResponse> reviews = bookingService.getConsumerReviews(consumer.getId());

            assertThat(reviews).hasSize(1);
            ConsumerReviewResponse review = reviews.get(0);
            assertThat(review.getId()).isEqualTo(20L);
            assertThat(review.getProviderId()).isEqualTo(provider.getId());
            assertThat(review.getProviderName()).isEqualTo(provider.getName());
            assertThat(review.getRating()).isEqualTo(4.5);
            assertThat(review.getReview()).isEqualTo("Excellent client");
        }

        @Test
        @DisplayName("Return Empty List When No Reviews Exist")
        void getConsumerReviewsEmptyTest()
        {
            when(consumerRepository.existsById(consumer.getId())).thenReturn(true);
            when(bookingRepository.findByConsumerIdAndProviderRatingIsNotNull(consumer.getId()))
                    .thenReturn(List.of());

            List<ConsumerReviewResponse> reviews = bookingService.getConsumerReviews(consumer.getId());

            assertThat(reviews).isEmpty();
        }

        @Test
        @DisplayName("Return Multiple Reviews Correctly")
        void getConsumerReviewsMultipleTest()
        {
            Provider provider2 = Provider.builder().id(5L).name("Provider Two").build();
            Booking booking1 = Booking.builder()
                    .id(20L).consumer(consumer).provider(provider)
                    .providerRating(5.0).providerReview("Great").completedAt(LocalDateTime.now().minusDays(3))
                    .build();
            Booking booking2 = Booking.builder()
                    .id(21L).consumer(consumer).provider(provider2)
                    .providerRating(3.0).providerReview("Okay").completedAt(LocalDateTime.now().minusDays(1))
                    .build();

            when(consumerRepository.existsById(consumer.getId())).thenReturn(true);
            when(bookingRepository.findByConsumerIdAndProviderRatingIsNotNull(consumer.getId()))
                    .thenReturn(List.of(booking1, booking2));

            List<ConsumerReviewResponse> reviews = bookingService.getConsumerReviews(consumer.getId());

            assertThat(reviews).hasSize(2);
            assertThat(reviews.get(0).getRating()).isEqualTo(5.0);
            assertThat(reviews.get(1).getRating()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException When Consumer Not Found")
        void getConsumerReviewsConsumerNotFoundTest()
        {
            when(consumerRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> bookingService.getConsumerReviews(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Consumer not found");
        }
    }
}
