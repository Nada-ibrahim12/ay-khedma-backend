package com.aykhedma.service;

import com.aykhedma.dto.request.AcceptBookingRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.request.CancelBookingRequest;
import com.aykhedma.dto.response.AcceptBookingResponse;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ForbiddenException;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingServiceImpl Tests")
class BookingServiceTest {
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
        private ProviderService providerService;
        @Mock
        private BookingMapper bookingMapper;

        @InjectMocks
        private BookingServiceImpl bookingService;

        @Captor
        private ArgumentCaptor<Booking> bookingCaptor;

        private Consumer consumer;
        private Provider provider;
        private Schedule schedule;
        private Booking booking;
        private LocalDate today;
        private LocalTime now;

        @BeforeEach
        void setUp() {
                today = LocalDate.now();
                now = LocalTime.now();

                ServiceType serviceType = ServiceType.builder()
                                .id(1L)
                                .build();

                schedule = Schedule.builder()
                                .id(1L)
                                .build();

                provider = Provider.builder()
                                .id(1L)
                                .role(UserType.PROVIDER)
                                .serviceType(serviceType)
                                .schedule(schedule)
                                .build();

                consumer = Consumer.builder()
                                .id(2L)
                                .role(UserType.CONSUMER)
                                .build();

                booking = Booking.builder()
                                .id(10L)
                                .consumer(consumer)
                                .provider(provider)
                                .serviceType(serviceType)
                                .requestedDate(today)
                                .requestedStartTime(now.plusHours(1))
                                .status(BookingStatus.PENDING)
                                .build();
        }

        @Nested
        @DisplayName("Request Booking Tests")
        class RequestBookingTest {
                @Test
                @DisplayName("Successfully Request Booking")
                void requestBookingSuccessTest() {
                        BookingRequest request = BookingRequest.builder()
                                        .providerId(provider.getId())
                                        .requestedDate(today.plusDays(1))
                                        .requestedTime(LocalTime.of(10, 0))
                                        .problemDescription("Test problem")
                                        .build();

                        when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(timeSlotRepository.isTimeWithinAvailableSlot(schedule.getId(), request.getRequestedDate(),
                                        request.getRequestedTime())).thenReturn(true);
                        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
                        when(bookingMapper.toBookingResponse(any(Booking.class)))
                                        .thenReturn(BookingResponse.builder().id(10L).build());

                        BookingResponse response = bookingService.requestBooking(consumer.getId(), request);

                        assertThat(response).isNotNull();
                        verify(bookingRepository).save(bookingCaptor.capture());
                        Booking saved = bookingCaptor.getValue();
                        assertThat(saved.getConsumer()).isEqualTo(consumer);
                        assertThat(saved.getProvider()).isEqualTo(provider);
                }

                @Test
                @DisplayName("Throw BadRequestException When Time Slot Not Available")
                void requestBookingTimeSlotNotAvailableTest() {
                        BookingRequest request = BookingRequest.builder()
                                        .providerId(provider.getId())
                                        .requestedDate(today.plusDays(1))
                                        .requestedTime(LocalTime.of(10, 0))
                                        .build();

                        when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(timeSlotRepository.isTimeWithinAvailableSlot(schedule.getId(),
                                        request.getRequestedDate(), request.getRequestedTime())).thenReturn(false);

                        assertThatThrownBy(() -> bookingService.requestBooking(consumer.getId(), request))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining("TimeSlot not available");
                }
        }

        @Nested
        @DisplayName("Accept Booking Tests")
        class AcceptBookingTest {
                @Test
                @DisplayName("Successfully Accept Booking Without Conflicts")
                void acceptBookingSuccessTest() {
                        AcceptBookingRequest request = AcceptBookingRequest.builder()
                                        .bookingId(booking.getId())
                                        .estimatedDuration(60L)
                                        .overrideWorkingHours(false)
                                        .build();
                        LocalTime endTime = booking.getRequestedStartTime().plusMinutes(request.getEstimatedDuration());

                        TimeSlot bookedSlot = TimeSlot.builder()
                                        .id(101L)
                                        .date(booking.getRequestedDate())
                                        .startTime(booking.getRequestedStartTime())
                                        .endTime(endTime)
                                        .status(TimeSlotStatus.BOOKED)
                                        .schedule(schedule)
                                        .build();

                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
                        when(workingDayRepository.findByScheduleIdAndDate(schedule.getId(), booking.getRequestedDate()))
                                        .thenReturn(Optional
                                                        .of(WorkingDay.builder().endTime(LocalTime.of(18, 0)).build()));
                        when(bookingRepository.findConflictingBookings(provider.getId(), booking.getId(),
                                        booking.getRequestedDate(),
                                        booking.getRequestedStartTime(), endTime)).thenReturn(List.of());
                        when(providerService.reserveTimeSlotWithBuffer(schedule.getId(), booking.getRequestedDate(),
                                        booking.getRequestedStartTime(), endTime))
                                        .thenReturn(bookedSlot);
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
                        verify(providerRepository).incrementTotalBookings(provider.getId());
                        verify(consumerRepository).incrementTotalBookings(consumer.getId());
                }

                @Test
                @DisplayName("Return Warning When Booking Exceeds Working Hours And Not Overridden")
                void acceptBookingWarningTest() {
                        AcceptBookingRequest request = AcceptBookingRequest.builder()
                                        .bookingId(booking.getId())
                                        .estimatedDuration(60L)
                                        .overrideWorkingHours(false)
                                        .build();

                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
                        when(workingDayRepository.findByScheduleIdAndDate(schedule.getId(), booking.getRequestedDate()))
                                        .thenReturn(Optional.of(
                                                        WorkingDay.builder().endTime(LocalTime.of(10, 30)).build()));

                        AcceptBookingResponse response = bookingService.acceptBooking(provider.getId(), request);

                        assertThat(response.getStatus()).isEqualTo("WARNING");
                        assertThat(response.getWarningMessage()).contains("exceed the end time");
                        verify(bookingRepository, never()).save(any());
                }

                @Test
                @DisplayName("Return Conflict When Overlapping Bookings Exist")
                void acceptBookingConflictTest() {
                        AcceptBookingRequest request = AcceptBookingRequest.builder()
                                        .bookingId(booking.getId())
                                        .estimatedDuration(60L)
                                        .overrideWorkingHours(false)
                                        .build();
                        LocalTime endTime = booking.getRequestedStartTime().plusMinutes(request.getEstimatedDuration());
                        Booking conflicting = Booking.builder().id(99L).status(BookingStatus.ACCEPTED).build();
                        BookingResponse conflictingResponse = BookingResponse.builder().id(99L).build();

                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
                        when(workingDayRepository.findByScheduleIdAndDate(schedule.getId(), booking.getRequestedDate()))
                                        .thenReturn(Optional
                                                        .of(WorkingDay.builder().endTime(LocalTime.of(18, 0)).build()));
                        when(bookingRepository.findConflictingBookings(provider.getId(), booking.getId(),
                                        booking.getRequestedDate(),
                                        booking.getRequestedStartTime(), endTime)).thenReturn(List.of(conflicting));
                        when(bookingMapper.toBookingResponse(conflicting)).thenReturn(conflictingResponse);

                        AcceptBookingResponse response = bookingService.acceptBooking(provider.getId(), request);

                        assertThat(response.getStatus()).isEqualTo("CONFLICT");
                        assertThat(response.getConflictingBookings()).hasSize(1);
                        assertThat(response.getConflictingBookings().get(0).getId()).isEqualTo(99L);
                        verify(bookingRepository, never()).save(any());
                        verify(bookingRepository, never()).saveAll(anyList());
                }

                @Test
                @DisplayName("Accept Booking And Auto Decline Overlapping Pending Requests")
                void acceptBookingDeclinesPendingConflictsTest() {
                        AcceptBookingRequest request = AcceptBookingRequest.builder()
                                        .bookingId(booking.getId())
                                        .estimatedDuration(60L)
                                        .overrideWorkingHours(false)
                                        .build();
                        LocalTime endTime = booking.getRequestedStartTime().plusMinutes(request.getEstimatedDuration());

                        Booking pendingConflict = Booking.builder()
                                        .id(99L)
                                        .status(BookingStatus.PENDING)
                                        .build();

                        TimeSlot bookedSlot = TimeSlot.builder()
                                        .id(101L)
                                        .date(booking.getRequestedDate())
                                        .startTime(booking.getRequestedStartTime())
                                        .endTime(endTime)
                                        .status(TimeSlotStatus.BOOKED)
                                        .schedule(schedule)
                                        .build();

                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
                        when(workingDayRepository.findByScheduleIdAndDate(schedule.getId(), booking.getRequestedDate()))
                                        .thenReturn(Optional
                                                        .of(WorkingDay.builder().endTime(LocalTime.of(18, 0)).build()));
                        when(bookingRepository.findConflictingBookings(provider.getId(), booking.getId(),
                                        booking.getRequestedDate(),
                                        booking.getRequestedStartTime(), endTime)).thenReturn(List.of(pendingConflict));
                        when(providerService.reserveTimeSlotWithBuffer(schedule.getId(), booking.getRequestedDate(),
                                        booking.getRequestedStartTime(), endTime))
                                        .thenReturn(bookedSlot);
                        when(bookingMapper.toBookingResponse(any(Booking.class)))
                                        .thenReturn(BookingResponse.builder().id(booking.getId()).build());

                        AcceptBookingResponse response = bookingService.acceptBooking(provider.getId(), request);

                        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
                        verify(bookingRepository).saveAll(argThat(bookings -> {
                                List<Booking> list = (List<Booking>) bookings;
                                return list.size() == 1
                                                && list.get(0).getId().equals(99L)
                                                && list.get(0).getStatus().equals(BookingStatus.DECLINED)
                                                && list.get(0).getDeclinedAt() != null;
                        }));
                }

                @Test
                @DisplayName("Throw ForbiddenException When Booking Does Not Belong To Provider")
                void acceptBookingWrongProviderTest() {
                        Provider anotherProvider = Provider.builder().id(99L).build();
                        booking.setProvider(anotherProvider);
                        AcceptBookingRequest request = AcceptBookingRequest.builder().bookingId(booking.getId())
                                        .build();

                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.acceptBooking(provider.getId(), request))
                                        .isInstanceOf(ForbiddenException.class)
                                        .hasMessageContaining("Booking does not belong to this provider");
                }

                @Test
                @DisplayName("Throw BadRequestException When Booking Is Not PENDING")
                void acceptBookingNotPendingTest() {
                        booking.setStatus(BookingStatus.EXPIRED);
                        AcceptBookingRequest request = AcceptBookingRequest.builder().bookingId(booking.getId())
                                        .build();

                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.acceptBooking(provider.getId(), request))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining(
                                                        "Booking cannot be accepted, it has already been EXPIRED");
                }

                @Test
                @DisplayName("Throw BadRequestException When Booking Start Time Has Passed")
                void acceptBookingPastStartTimeTest() {
                        booking.setRequestedDate(today);
                        booking.setRequestedStartTime(now.minusHours(1));
                        AcceptBookingRequest request = AcceptBookingRequest.builder().bookingId(booking.getId())
                                        .build();

                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.acceptBooking(provider.getId(), request))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining("its starting time has already passed");
                }
        }

        @Nested
        @DisplayName("Decline Booking Tests")
        class DeclineBookingTest {
                @Test
                @DisplayName("Successfully Decline Booking")
                void declineBookingSuccessTest() {
                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
                        when(bookingMapper.toBookingResponse(any(Booking.class)))
                                        .thenReturn(BookingResponse.builder().id(booking.getId()).build());

                        BookingResponse response = bookingService.declineBooking(provider.getId(), booking.getId());

                        assertThat(response).isNotNull();
                        verify(bookingRepository).save(bookingCaptor.capture());
                        Booking saved = bookingCaptor.getValue();
                        assertThat(saved.getStatus()).isEqualTo(BookingStatus.DECLINED);
                        assertThat(saved.getDeclinedAt()).isNotNull();
                }

                @Test
                @DisplayName("Throw ForbiddenException When Booking Does Not Belong To Provider")
                void declineBookingWrongProviderTest() {
                        Provider anotherProvider = Provider.builder().id(99L).build();
                        booking.setProvider(anotherProvider);
                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.declineBooking(provider.getId(), booking.getId()))
                                        .isInstanceOf(ForbiddenException.class)
                                        .hasMessageContaining("Booking does not belong to this provider");
                }

                @Test
                @DisplayName("Throw BadRequestException When Booking Is Not PENDING")
                void declineBookingNotPendingTest() {
                        booking.setStatus(BookingStatus.ACCEPTED);
                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.declineBooking(provider.getId(), booking.getId()))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining(
                                                        "Booking cannot be declined, it has already been ACCEPTED");
                }

                @Test
                @DisplayName("Throw BadRequestException When Booking Start Time Has Passed")
                void declineBookingPastStartTimeTest() {
                        booking.setRequestedDate(today);
                        booking.setRequestedStartTime(now.minusHours(1));
                        when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.declineBooking(provider.getId(), booking.getId()))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining("its starting time has already passed");
                }
        }

        @Nested
        @DisplayName("Cancel Booking Tests")
        class CancelBookingTest {
                private CancelBookingRequest request;

                @BeforeEach
                void setUp() {
                        request = CancelBookingRequest.builder()
                                        .bookingId(booking.getId())
                                        .cancellationReason("Personal reasons")
                                        .build();
                        booking.setStatus(BookingStatus.ACCEPTED);
                }

                @Test
                @DisplayName("Consumer Cancels Booking Successfully")
                void cancelBookingByConsumerTest() {
                        when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
                        when(bookingMapper.toBookingResponse(any(Booking.class)))
                                        .thenReturn(BookingResponse.builder().id(booking.getId()).build());

                        BookingResponse response = bookingService.cancelBooking(consumer.getId(), request);

                        assertThat(response).isNotNull();
                        verify(bookingRepository).save(bookingCaptor.capture());
                        Booking saved = bookingCaptor.getValue();
                        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELLED);
                        assertThat(saved.getCancellationReason()).isEqualTo("Personal reasons");
                        assertThat(saved.getCancelledAt()).isNotNull();
                        assertThat(saved.getCancelledBy()).isEqualTo("C");
                        verify(consumerRepository).incrementCancelledBookings(consumer.getId());
                        verify(providerRepository, never()).incrementCancelledBookings(anyLong());
                }

                @Test
                @DisplayName("Provider Cancels Booking Successfully")
                void cancelBookingByProviderTest() {
                        when(userRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
                        when(bookingMapper.toBookingResponse(any(Booking.class)))
                                        .thenReturn(BookingResponse.builder().id(booking.getId()).build());

                        BookingResponse response = bookingService.cancelBooking(provider.getId(), request);

                        assertThat(response).isNotNull();
                        verify(bookingRepository).save(bookingCaptor.capture());
                        Booking saved = bookingCaptor.getValue();
                        assertThat(saved.getCancelledBy()).isEqualTo("P");
                        verify(providerRepository).incrementCancelledBookings(provider.getId());
                        verify(consumerRepository, never()).incrementCancelledBookings(anyLong());
                }

                @Test
                @DisplayName("Throw ForbiddenException When Consumer Tries To Cancel Others Booking")
                void cancelBookingWrongConsumerTest() {
                        Consumer anotherConsumer = Consumer.builder().id(99L).role(UserType.CONSUMER).build();
                        when(userRepository.findById(99L)).thenReturn(Optional.of(anotherConsumer));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.cancelBooking(99L, request))
                                        .isInstanceOf(ForbiddenException.class)
                                        .hasMessageContaining("Booking does not belong to this consumer");
                }

                @Test
                @DisplayName("Throw BadRequestException When Booking Is Not ACCEPTED")
                void cancelBookingNotAcceptedTest() {
                        booking.setStatus(BookingStatus.DECLINED);
                        when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.cancelBooking(consumer.getId(), request))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining(
                                                        "Booking cannot be cancelled, it has already been DECLINED");
                }

                @Test
                @DisplayName("Throw BadRequestException When Booking Start Time Has Passed")
                void cancelBookingPastStartTimeTest() {
                        booking.setRequestedDate(today);
                        booking.setRequestedStartTime(now.minusHours(1));
                        when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
                        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

                        assertThatThrownBy(() -> bookingService.cancelBooking(consumer.getId(), request))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining("its starting time has already passed");
                }
        }

        @Nested
        @DisplayName("Get Bookings By Status Tests")
        class GetBookingsByStatusTest {
                private Pageable pageable;
                private Page<Booking> bookingPage;

                @BeforeEach
                void setUp() {
                        pageable = PageRequest.of(0, 10);
                        bookingPage = new PageImpl<>(List.of(booking));
                }

                @Test
                @DisplayName("Get Bookings By Status As Consumer")
                void getBookingsByStatusAsConsumerTest() {
                        when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
                        when(bookingRepository.findByConsumerIdAndStatus(consumer.getId(), BookingStatus.PENDING,
                                        pageable))
                                        .thenReturn(bookingPage);
                        when(bookingMapper.toBookingResponse(booking))
                                        .thenReturn(BookingResponse.builder().id(booking.getId()).build());

                        Page<BookingResponse> result = bookingService.getBookingsByStatus(consumer.getId(),
                                        BookingStatus.PENDING, pageable);

                        assertThat(result).hasSize(1);
                        verify(bookingRepository).findByConsumerIdAndStatus(consumer.getId(), BookingStatus.PENDING,
                                        pageable);
                }

                @Test
                @DisplayName("Get Bookings By Status As Provider")
                void getBookingsByStatusAsProviderTest() {
                        when(userRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findByProviderIdAndStatus(provider.getId(), BookingStatus.ACCEPTED,
                                        pageable))
                                        .thenReturn(bookingPage);
                        when(bookingMapper.toBookingResponse(booking))
                                        .thenReturn(BookingResponse.builder().id(booking.getId()).build());

                        Page<BookingResponse> result = bookingService.getBookingsByStatus(provider.getId(),
                                        BookingStatus.ACCEPTED, pageable);

                        assertThat(result).hasSize(1);
                        verify(bookingRepository).findByProviderIdAndStatus(provider.getId(), BookingStatus.ACCEPTED,
                                        pageable);
                }
        }

        @Nested
        @DisplayName("Get Today's Upcoming Bookings Tests")
        class GetUpcomingBookingsTest {
                @Test
                @DisplayName("Get Today's Upcoming Bookings As Consumer")
                void getUpcomingBookingsAsConsumerTest() {
                        List<Booking> bookings = List.of(booking);
                        when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
                        when(bookingRepository.findByConsumerIdAndStatusAndRequestedDateAndRequestedStartTimeAfter(
                                        eq(consumer.getId()), eq(BookingStatus.ACCEPTED), any(LocalDate.class),
                                        any(LocalTime.class)))
                                        .thenReturn(bookings);
                        when(bookingMapper.toBookingResponse(booking))
                                        .thenReturn(BookingResponse.builder().id(booking.getId()).build());

                        List<BookingResponse> result = bookingService.getUpcomingBookings(consumer.getId());

                        assertThat(result).hasSize(1);
                }

                @Test
                @DisplayName("Get Today's Upcoming Bookings As Provider")
                void getUpcomingBookingsAsProviderTest() {
                        List<Booking> bookings = List.of(booking);
                        when(userRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
                        when(bookingRepository.findByProviderIdAndStatusAndRequestedDateAndRequestedStartTimeAfter(
                                        eq(provider.getId()), eq(BookingStatus.ACCEPTED), any(LocalDate.class),
                                        any(LocalTime.class)))
                                        .thenReturn(bookings);
                        when(bookingMapper.toBookingResponse(booking))
                                        .thenReturn(BookingResponse.builder().id(booking.getId()).build());

                        List<BookingResponse> result = bookingService.getUpcomingBookings(provider.getId());

                        assertThat(result).hasSize(1);
                }
        }
}
