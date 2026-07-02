package com.aykhedma.service;

import com.aykhedma.dto.request.AcceptBookingRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.request.CancelBookingRequest;
import com.aykhedma.dto.request.ProviderRatingRequest;
import com.aykhedma.dto.response.AcceptBookingResponse;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.MonthlyBookingStatsResponse;
import com.aykhedma.dto.response.WeeklyBookingStatsResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.mapper.BookingMapper;
import com.aykhedma.model.booking.*;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.*;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingServiceImpl Tests")
class BookingServiceTest
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

    @Captor
    private ArgumentCaptor<Booking> bookingCaptor;
    @Captor
    private ArgumentCaptor<Provider> providerCaptor;
    @Captor
    private ArgumentCaptor<Consumer> consumerCaptor;

    private Consumer consumer;
    private Provider provider;
    private Schedule schedule;
    private TimeSlot timeSlot;
    private Booking booking;
    private LocalDate today;
    private LocalTime now;

    @BeforeEach
    void setUp()
    {
        today = LocalDate.now();
        now = LocalTime.now();

        Location location = Location.builder()
                .id(1L)
                .latitude(30.0)
                .longitude(31.0)
                .address("Test Address")
                .area("Test Area")
                .city("Test City")
                .build();

        ServiceType serviceType = ServiceType.builder()
                .id(1L)
                .name("Plumbing")
                .build();

        schedule = Schedule.builder()
                .id(1L)
                .build();

        provider = Provider.builder()
                .id(1L)
                .role(UserType.PROVIDER)
                .name("Provider One")
                .location(location)
                .serviceType(serviceType)
                .schedule(schedule)
                .bookingBufferMinutes(30)
                .totalRequests(0)
                .totalBookings(0)
                .completedJobs(0)
                .cancelledBookings(0)
                .build();

        consumer = Consumer.builder()
                .id(2L)
                .role(UserType.CONSUMER)
                .name("Consumer One")
                .location(location)
                .totalBookings(0)
                .cancelledBookings(0)
                .build();

        timeSlot = TimeSlot.builder()
                .id(1L)
                .status(TimeSlotStatus.BOOKED)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        booking = Booking.builder()
                .id(10L)
                .consumer(consumer)
                .provider(provider)
                .serviceType(serviceType)
                .requestedDate(today.plusDays(1))
                .requestedStartTime(LocalTime.of(10, 0))
                .status(BookingStatus.PENDING)
                .timeSlot(timeSlot)
                .build();
    }

    @Nested
    @DisplayName("Request Booking Tests")
    class RequestBookingTest
    {
        @Test
        @DisplayName("Successfully Request Booking")
        void requestBookingSuccessTest()
        {
            BookingRequest request = BookingRequest.builder()
                    .providerId(provider.getId())
                    .requestedDate(today.plusDays(1))
                    .requestedTime(LocalTime.of(10, 0))
                    .problemDescription("Test problem")
                    .build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            doNothing().when(providerService).validateHalfHourBoundary(any(LocalTime.class));
            when(timeSlotRepository.isTimeWithinAvailableSlot(schedule.getId(), request.getRequestedDate(),
                    request.getRequestedTime())).thenReturn(true);
            when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
            when(providerService.reserveTimeSlotWithBuffer(anyLong(), any(LocalDate.class), any(LocalTime.class),
                    any(LocalTime.class), anyLong(), anyBoolean())).thenReturn(timeSlot);
            when(bookingMapper.toBookingResponse(any(Booking.class)))
                    .thenReturn(BookingResponse.builder().id(10L).build());

            BookingResponse response = bookingService.requestBooking(consumer.getId(), request);

            assertThat(response).isNotNull();
            verify(bookingRepository).save(bookingCaptor.capture());
            Booking saved = bookingCaptor.getValue();
            assertThat(saved.getConsumer()).isEqualTo(consumer);
            assertThat(saved.getProvider()).isEqualTo(provider);
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING);
            assertThat(saved.getTimeSlot()).isEqualTo(timeSlot);

            verify(providerRepository).save(providerCaptor.capture());
            Provider savedProvider = providerCaptor.getValue();
            assertThat(savedProvider.getTotalRequests()).isEqualTo(1);
        }

        @Test
        @DisplayName("Throw BadRequestException When Time Slot Not Available")
        void requestBookingTimeSlotNotAvailableTest()
        {
            BookingRequest request = BookingRequest.builder()
                    .providerId(provider.getId())
                    .requestedDate(today.plusDays(1))
                    .requestedTime(LocalTime.of(10, 0))
                    .build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            doNothing().when(providerService).validateHalfHourBoundary(any(LocalTime.class));
            when(timeSlotRepository.isTimeWithinAvailableSlot(schedule.getId(),
                    request.getRequestedDate(), request.getRequestedTime())).thenReturn(false);

            assertThatThrownBy(() -> bookingService.requestBooking(consumer.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("TimeSlot not available");
        }
    }

    @Nested
    @DisplayName("Delete Booking Tests")
    class DeleteBookingTest
    {
        @Test
        @DisplayName("Successfully Delete Booking")
        void deleteBookingSuccessTest()
        {
            booking.setStatus(BookingStatus.PENDING);

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingMapper.toBookingResponse(any(Booking.class)))
                    .thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.deleteBooking(consumer.getId(), booking.getId());

            assertThat(response).isNotNull();
            verify(providerService).restoreAvailabilityForCancelledBooking(booking);
            verify(bookingRepository).save(bookingCaptor.capture());
            Booking saved = bookingCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.DELETED);

            verify(providerRepository).save(providerCaptor.capture());
            Provider savedProvider = providerCaptor.getValue();
            assertThat(savedProvider.getTotalRequests()).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("Complete Booking Tests")
    class CompleteBookingTest
    {
        @Test
        @DisplayName("Successfully Complete Booking")
        void completeBookingSuccessTest()
        {
            booking.setStatus(BookingStatus.ACCEPTED);

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingMapper.toBookingResponse(any(Booking.class)))
                    .thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.completeBooking(consumer.getId(), booking.getId());

            assertThat(response).isNotNull();
            verify(bookingRepository).save(bookingCaptor.capture());
            Booking saved = bookingCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.COMPLETED);
            assertThat(saved.getCompletedAt()).isNotNull();

            verify(providerRepository).save(providerCaptor.capture());
            Provider savedProvider = providerCaptor.getValue();
            assertThat(savedProvider.getCompletedJobs()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Accept Booking Tests")
    class AcceptBookingTest
    {
        @Test
        @DisplayName("Successfully Accept Booking Without Conflicts")
        void acceptBookingSuccessTest()
        {
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(booking.getId())
                    .estimatedDuration(60L)
                    .overrideWorkingHours(false)
                    .build();
            LocalTime endTime = booking.getRequestedStartTime().plusMinutes(60L);

            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(workingDayRepository.findByScheduleIdAndDate(schedule.getId(), booking.getRequestedDate()))
                    .thenReturn(Optional.of(WorkingDay.builder().endTime(LocalTime.of(18, 0)).build()));
            when(bookingRepository.findConflictingBookings(provider.getId(), booking.getId(), booking.getRequestedDate(),
                    booking.getRequestedStartTime(), endTime.plusMinutes(30L))).thenReturn(List.of());
            when(providerService.reserveTimeSlotWithBuffer(schedule.getId(), booking.getRequestedDate(),
                    booking.getRequestedStartTime(), endTime, 30L, true))
                    .thenReturn(timeSlot);
            when(bookingMapper.toBookingResponse(any(Booking.class)))
                    .thenReturn(BookingResponse.builder().id(booking.getId()).build());

            AcceptBookingResponse response = bookingService.acceptBooking(provider.getId(), request);

            assertThat(response.getStatus()).isEqualTo("ACCEPTED");
            assertThat(response.getBooking()).isNotNull();
            verify(bookingRepository).save(bookingCaptor.capture());
            Booking saved = bookingCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
            assertThat(saved.getEstimatedDuration()).isEqualTo(60L);
            assertThat(saved.getAcceptedAt()).isNotNull();
            assertThat(saved.getTimeSlot()).isEqualTo(timeSlot);

            verify(providerRepository).save(providerCaptor.capture());
            Provider savedProvider = providerCaptor.getValue();
            assertThat(savedProvider.getTotalBookings()).isEqualTo(1);

            verify(consumerRepository).save(consumerCaptor.capture());
            Consumer savedConsumer = consumerCaptor.getValue();
            assertThat(savedConsumer.getTotalBookings()).isEqualTo(1);
        }

        @Test
        @DisplayName("Return Warning When Booking Exceeds Working Hours And Not Overridden")
        void acceptBookingWarningTest()
        {
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(booking.getId())
                    .estimatedDuration(60L)
                    .overrideWorkingHours(false)
                    .build();

            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(workingDayRepository.findByScheduleIdAndDate(schedule.getId(), booking.getRequestedDate()))
                    .thenReturn(Optional.of(WorkingDay.builder().endTime(LocalTime.of(10, 15)).build()));

            AcceptBookingResponse response = bookingService.acceptBooking(provider.getId(), request);

            assertThat(response.getStatus()).isEqualTo("WARNING");
            assertThat(response.getWarningMessage()).contains("exceed the end time");
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Return Conflict When Overlapping Bookings Exist")
        void acceptBookingConflictTest()
        {
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(booking.getId())
                    .estimatedDuration(60L)
                    .overrideWorkingHours(false)
                    .build();
            LocalTime endTime = booking.getRequestedStartTime().plusMinutes(60L);
            Booking conflicting = Booking.builder().id(99L).build();
            BookingResponse conflictingResponse = BookingResponse.builder().id(99L).build();

            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(workingDayRepository.findByScheduleIdAndDate(schedule.getId(), booking.getRequestedDate()))
                    .thenReturn(Optional.of(WorkingDay.builder().endTime(LocalTime.of(18, 0)).build()));
            when(bookingRepository.findConflictingBookings(provider.getId(), booking.getId(), booking.getRequestedDate(),
                    booking.getRequestedStartTime(), endTime.plusMinutes(30L))).thenReturn(List.of(conflicting));
            when(bookingMapper.toBookingResponse(conflicting)).thenReturn(conflictingResponse);

            AcceptBookingResponse response = bookingService.acceptBooking(provider.getId(), request);

            assertThat(response.getStatus()).isEqualTo("CONFLICT");
            assertThat(response.getConflictingBookings()).hasSize(1);
            assertThat(response.getConflictingBookings().get(0).getId()).isEqualTo(99L);
            verify(bookingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Decline Booking Tests")
    class DeclineBookingTest
    {
        @Test
        @DisplayName("Successfully Decline Booking")
        void declineBookingSuccessTest()
        {
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingMapper.toBookingResponse(any(Booking.class)))
                    .thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.declineBooking(provider.getId(), booking.getId());

            assertThat(response).isNotNull();
            verify(providerService).restoreAvailabilityForCancelledBooking(booking);
            verify(bookingRepository).save(bookingCaptor.capture());
            Booking saved = bookingCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.DECLINED);
            assertThat(saved.getDeclinedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Cancel Booking Tests")
    class CancelBookingTest
    {
        private CancelBookingRequest request;

        @BeforeEach
        void setUp()
        {
            request = CancelBookingRequest.builder()
                    .bookingId(booking.getId())
                    .cancellationReason("Personal reasons")
                    .build();
            booking.setStatus(BookingStatus.ACCEPTED);
        }

        @Test
        @DisplayName("Consumer Cancels Booking Successfully")
        void cancelBookingByConsumerTest()
        {
            when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingMapper.toBookingResponse(any(Booking.class)))
                    .thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.cancelBooking(consumer.getId(), request);

            assertThat(response).isNotNull();
            verify(providerService).restoreAvailabilityForCancelledBooking(booking);
            verify(bookingRepository).save(bookingCaptor.capture());
            Booking saved = bookingCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(saved.getCancellationReason()).isEqualTo("Personal reasons");
            assertThat(saved.getCancelledAt()).isNotNull();
            assertThat(saved.getCancelledBy()).isEqualTo("C");

            verify(consumerRepository).save(consumerCaptor.capture());
            Consumer savedConsumer = consumerCaptor.getValue();
            assertThat(savedConsumer.getCancelledBookings()).isEqualTo(1);
            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Provider Cancels Booking Successfully")
        void cancelBookingByProviderTest()
        {
            when(userRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingMapper.toBookingResponse(any(Booking.class)))
                    .thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.cancelBooking(provider.getId(), request);

            assertThat(response).isNotNull();
            verify(bookingRepository).save(bookingCaptor.capture());
            Booking saved = bookingCaptor.getValue();
            assertThat(saved.getCancelledBy()).isEqualTo("P");

            verify(providerRepository).save(providerCaptor.capture());
            Provider savedProvider = providerCaptor.getValue();
            assertThat(savedProvider.getCancelledBookings()).isEqualTo(1);
            verify(consumerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Apply Penalty When Cancelling Less Than 2 Hours Before Start")
        void cancelBookingWithPenaltyTest()
        {
            booking.setRequestedDate(today);
            booking.setRequestedStartTime(now.plusMinutes(30));
            when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
            when(bookingMapper.toBookingResponse(any(Booking.class)))
                    .thenReturn(BookingResponse.builder().id(booking.getId()).build());

            bookingService.cancelBooking(consumer.getId(), request);

            verify(consumerRepository).save(consumerCaptor.capture());
            Consumer savedConsumer = consumerCaptor.getValue();
            assertThat(savedConsumer.getAverageRating()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Weekly Booking Stats Tests")
    class WeeklyBookingStatsTest
    {
        @Test
        @DisplayName("Get Weekly Stats Successfully")
        void getWeeklyBookingStatsSuccessTest()
        {
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(bookingRepository.findBookingStatsCurrentWeek(provider.getId(), LocalDate.now()))
                    .thenReturn(new Object[]{5, 2});

            WeeklyBookingStatsResponse response = bookingService.getWeeklyBookingStats(provider.getId());

            assertThat(response.getAcceptedAndCompletedBookings()).isEqualTo(5);
            assertThat(response.getCancelledBookings()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Monthly Booking Stats Tests")
    class MonthlyBookingStatsTest
    {
        @Test
        @DisplayName("Get Monthly Stats Successfully")
        void getMonthlyBookingStatsSuccessTest()
        {
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            List<Object[]> results = List.of(
                    new Object[]{"2025-01", 10, 2},
                    new Object[]{"2025-02", 8, 1}
            );
            when(bookingRepository.findBookingStatsLastSixMonths(provider.getId(), LocalDate.now()))
                    .thenReturn(results);

            MonthlyBookingStatsResponse response = bookingService.getMonthlyBookingStats(provider.getId());

            assertThat(response.getMonths()).containsExactly("2025-01", "2025-02");
            assertThat(response.getCompletedBookings()).containsExactly(10, 8);
            assertThat(response.getCancelledBookings()).containsExactly(2, 1);
        }
    }

    @Nested
    @DisplayName("Get Bookings By Status Tests")
    class GetBookingsByStatusTest
    {
        private Pageable pageable;
        private Page<Booking> bookingPage;

        @BeforeEach
        void setUp()
        {
            pageable = PageRequest.of(0, 10);
            bookingPage = new PageImpl<>(List.of(booking));
        }

        @Test
        @DisplayName("Get Bookings By Status As Consumer")
        void getBookingsByStatusAsConsumerTest()
        {
            when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(bookingRepository.findByConsumerIdAndStatus(consumer.getId(), BookingStatus.PENDING, pageable))
                    .thenReturn(bookingPage);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            Page<BookingResponse> result = bookingService.getBookingsByStatus(consumer.getId(), BookingStatus.PENDING, pageable);

            assertThat(result).hasSize(1);
            verify(bookingRepository).findByConsumerIdAndStatus(consumer.getId(), BookingStatus.PENDING, pageable);
        }

        @Test
        @DisplayName("Get Bookings By Status As Provider")
        void getBookingsByStatusAsProviderTest()
        {
            when(userRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(bookingRepository.findByProviderIdAndStatus(provider.getId(), BookingStatus.ACCEPTED, pageable))
                    .thenReturn(bookingPage);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            Page<BookingResponse> result = bookingService.getBookingsByStatus(provider.getId(), BookingStatus.ACCEPTED, pageable);

            assertThat(result).hasSize(1);
            verify(bookingRepository).findByProviderIdAndStatus(provider.getId(), BookingStatus.ACCEPTED, pageable);
        }

        @Test
        @DisplayName("Get All Bookings When Status Is Null")
        void getBookingsByStatusNullTest()
        {
            when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(bookingRepository.findByConsumerId(consumer.getId(), pageable)).thenReturn(bookingPage);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            Page<BookingResponse> result = bookingService.getBookingsByStatus(consumer.getId(), null, pageable);

            assertThat(result).hasSize(1);
            verify(bookingRepository).findByConsumerId(consumer.getId(), pageable);
        }
    }

    @Nested
    @DisplayName("Get Upcoming Bookings Tests")
    class GetUpcomingBookingsTest
    {
        @Test
        @DisplayName("Get Upcoming Bookings As Consumer")
        void getUpcomingBookingsAsConsumerTest()
        {
            List<Booking> bookings = List.of(booking);
            when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(bookingRepository.findUpcomingBookings(eq(consumer.getId()), any(LocalDate.class), any(LocalTime.class)))
                    .thenReturn(bookings);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            List<BookingResponse> result = bookingService.getUpcomingBookings(consumer.getId());

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Get Upcoming Bookings As Provider")
        void getUpcomingBookingsAsProviderTest()
        {
            List<Booking> bookings = List.of(booking);
            when(userRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(bookingRepository.findUpcomingBookings(eq(provider.getId()), any(LocalDate.class), any(LocalTime.class)))
                    .thenReturn(bookings);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            List<BookingResponse> result = bookingService.getUpcomingBookings(provider.getId());

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Submit Rating Tests")
    class SubmitRatingTest
    {
        private com.aykhedma.dto.request.RatingRequest ratingRequest;

        @BeforeEach
        void setUp()
        {
            ratingRequest = com.aykhedma.dto.request.RatingRequest.builder()
                    .bookingId(1L)
                    .punctualityRating(5)
                    .commitmentRating(5)
                    .qualityOfWorkRating(5)
                    .review("Great service!")
                    .build();

            ProviderRatingRequest providerRatingRequest = ProviderRatingRequest.builder()
                    .bookingId(1L)
                    .rating(4)
                    .review("Good client!")
                    .build();

            booking.setId(1L);
            booking.setRequestedDate(today.minusDays(1));
            booking.setRequestedStartTime(LocalTime.of(12, 0));
        }

        @Test
        @DisplayName("Submit Rating Success for Completed Booking")
        void submitRatingCompletedBookingSuccessTest()
        {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setAcceptedAt(LocalDateTime.now().minusDays(1));

            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.submitRating(consumer.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(booking.getConsumerRating()).isEqualTo(5.0);
            assertThat(booking.getConsumerReview()).isEqualTo("Great service!");
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
            verify(bookingRepository).save(booking);
        }

        @Test
        @DisplayName("Submit Rating Success for Expired Accepted Booking")
        void submitRatingExpiredAcceptedBookingSuccessTest()
        {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setAcceptedAt(LocalDateTime.now().minusDays(1));

            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
            when(bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId())).thenReturn(1L);
            when(bookingMapper.toBookingResponse(booking)).thenReturn(BookingResponse.builder().id(booking.getId()).build());

            BookingResponse response = bookingService.submitRating(consumer.getId(), ratingRequest);

            assertThat(response).isNotNull();
            assertThat(booking.getConsumerRating()).isEqualTo(5.0);
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
            verify(bookingRepository).save(booking);
        }

        @Test
        @DisplayName("Throw BadRequestException for Expired Booking without Acceptance")
        void submitRatingExpiredNotAcceptedBookingTest()
        {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setAcceptedAt(null);

            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed bookings or expired bookings that were accepted.");
        }

        @Test
        @DisplayName("Throw BadRequestException for Pending Booking")
        void submitRatingPendingBookingTest()
        {
            booking.setStatus(BookingStatus.PENDING);

            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.submitRating(consumer.getId(), ratingRequest))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Rating and reviews are only allowed for completed bookings or expired bookings that were accepted.");
        }
    }
}
