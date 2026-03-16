package com.aykhedma.service;

import com.aykhedma.dto.request.AcceptBookingRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.request.CancelBookingRequest;
import com.aykhedma.dto.response.AcceptBookingResponse;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ForbiddenException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.BookingMapper;
import com.aykhedma.model.booking.*;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ConsumerRepository consumerRepository;
    private final ProviderRepository providerRepository;
    private final WorkingDayRepository workingDayRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final BookingMapper bookingMapper;
    private final ProviderService providerService;

    @Override
    @Transactional
    public BookingResponse requestBooking(Long consumerId, BookingRequest bookingRequest) {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        Provider provider = providerRepository.findById(bookingRequest.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        ServiceType serviceType = provider.getServiceType();
        if (serviceType == null)
            throw new ResourceNotFoundException("Provider's service Type not found");

        Schedule schedule = provider.getSchedule();
        Long scheduleId;
        if (schedule == null)
            throw new ResourceNotFoundException("Provider's schedule not found");
        else
            scheduleId = schedule.getId();

        LocalDate requestedDate = bookingRequest.getRequestedDate();
        LocalTime requestedTime = bookingRequest.getRequestedTime();
        providerService.validateHalfHourBoundary(requestedTime);

        if (!timeSlotRepository.isTimeWithinAvailableSlot(scheduleId, requestedDate, requestedTime))
            throw new BadRequestException("TimeSlot not available");

        String problemDescription = bookingRequest.getProblemDescription();

        Booking booking = Booking.builder()
                .consumer(consumer)
                .provider(provider)
                .serviceType(serviceType)
                .requestedDate(requestedDate)
                .requestedStartTime(requestedTime)
                .problemDescription(problemDescription)
                .status(BookingStatus.PENDING)
                .build();
        bookingRepository.save(booking);

        return bookingMapper.toBookingResponse(booking);
    }

    @Override
    @Transactional
    public AcceptBookingResponse acceptBooking(Long providerId, AcceptBookingRequest acceptBookingRequest) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        Booking booking = bookingRepository.findById(acceptBookingRequest.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!providerId.equals(booking.getProvider().getId()))
            throw new ForbiddenException("Booking does not belong to this provider");

        if (!booking.getStatus().equals(BookingStatus.PENDING))
            throw new BadRequestException("Booking cannot be accepted, it has already been " + booking.getStatus());

        LocalDateTime bookingStartTime = LocalDateTime.of(booking.getRequestedDate(), booking.getRequestedStartTime());
        if (bookingStartTime.isBefore(LocalDateTime.now()))
            throw new BadRequestException("Booking cannot be accepted, its starting time has already passed");

        Long bookingId = booking.getId();
        LocalDate date = booking.getRequestedDate();
        LocalTime startTime = booking.getRequestedStartTime();
        Long estimatedDuration = acceptBookingRequest.getEstimatedDuration();
        LocalTime endTime = booking.getRequestedStartTime().plusMinutes(estimatedDuration);

        Schedule schedule = provider.getSchedule();
        Long scheduleId;
        if (schedule == null)
            throw new ResourceNotFoundException("Provider's schedule not found");
        else
            scheduleId = schedule.getId();

        if (!acceptBookingRequest.isOverrideWorkingHours()) {
            WorkingDay workingDay = workingDayRepository.findByScheduleIdAndDate(scheduleId, date)
                    .orElseThrow(() -> new ResourceNotFoundException("Working day by the booking date is not found"));

            if (endTime.isAfter(workingDay.getEndTime())) {
                return AcceptBookingResponse.builder()
                        .status("WARNING")
                        .warningMessage("The booking end time will exceed the end time of the working day")
                        .build();
            }
        }

        List<Booking> conflictingBookings = bookingRepository.findConflictingBookings(providerId, bookingId, date,
                startTime, endTime);
        List<Booking> acceptedConflictingBookings = conflictingBookings.stream()
                .filter(conflictingBooking -> BookingStatus.ACCEPTED.equals(conflictingBooking.getStatus()))
                .toList();
        if (!acceptedConflictingBookings.isEmpty()) {
            return AcceptBookingResponse.builder()
                    .status("CONFLICT")
                    .conflictingBookings(acceptedConflictingBookings.stream()
                            .map(bookingMapper::toBookingResponse)
                            .toList())
                    .build();
        }

        TimeSlot reservedBookedSlot = providerService.reserveTimeSlotWithBuffer(scheduleId, date, startTime, endTime);

        booking.setEstimatedDuration(estimatedDuration);
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setAcceptedAt(LocalDateTime.now());
        booking.setTimeSlot(reservedBookedSlot);
        bookingRepository.save(booking);

        List<Booking> pendingConflictingBookings = conflictingBookings.stream()
                .filter(conflictingBooking -> BookingStatus.PENDING.equals(conflictingBooking.getStatus()))
                .toList();
        if (!pendingConflictingBookings.isEmpty()) {
            LocalDateTime declinedAt = LocalDateTime.now();
            pendingConflictingBookings.forEach(conflictingBooking -> {
                conflictingBooking.setStatus(BookingStatus.DECLINED);
                conflictingBooking.setDeclinedAt(declinedAt);
            });
            bookingRepository.saveAll(pendingConflictingBookings);
        }

        providerRepository.incrementTotalBookings(booking.getProvider().getId());
        consumerRepository.incrementTotalBookings(booking.getConsumer().getId());

        return AcceptBookingResponse.builder()
                .status("ACCEPTED")
                .booking(bookingMapper.toBookingResponse(booking))
                .build();
    }

    @Override
    @Transactional
    public BookingResponse declineBooking(Long providerId, Long bookingId) {
        providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!providerId.equals(booking.getProvider().getId()))
            throw new ForbiddenException("Booking does not belong to this provider");

        if (!booking.getStatus().equals(BookingStatus.PENDING))
            throw new BadRequestException("Booking cannot be declined, it has already been " + booking.getStatus());

        LocalDateTime bookingStartTime = LocalDateTime.of(booking.getRequestedDate(), booking.getRequestedStartTime());
        if (bookingStartTime.isBefore(LocalDateTime.now()))
            throw new BadRequestException("Booking cannot be declined, its starting time has already passed");

        booking.setStatus(BookingStatus.DECLINED);
        booking.setDeclinedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        return bookingMapper.toBookingResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long userId, CancelBookingRequest cancelBookingRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Booking booking = bookingRepository.findById(cancelBookingRequest.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        boolean isConsumer;
        if (user.getRole().equals(UserType.CONSUMER)) {
            if (!userId.equals(booking.getConsumer().getId()))
                throw new ForbiddenException("Booking does not belong to this consumer");

            isConsumer = true;
        } else if (user.getRole().equals(UserType.PROVIDER)) {
            if (!userId.equals(booking.getProvider().getId()))
                throw new ForbiddenException("Booking does not belong to this provider");

            isConsumer = false;
        } else
            throw new ForbiddenException("User is not a provider or a consumer");

        if (!booking.getStatus().equals(BookingStatus.ACCEPTED))
            throw new BadRequestException("Booking cannot be cancelled, it has already been " + booking.getStatus());

        LocalDateTime bookingStartTime = LocalDateTime.of(booking.getRequestedDate(), booking.getRequestedStartTime());
        if (bookingStartTime.isBefore(LocalDateTime.now()))
            throw new BadRequestException("Booking cannot be cancelled, its starting time has already passed");

        providerService.restoreAvailabilityForCancelledBooking(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(cancelBookingRequest.getCancellationReason());
        booking.setCancelledAt(LocalDateTime.now());
        if (isConsumer)
            booking.setCancelledBy("C");
        else
            booking.setCancelledBy("P");
        bookingRepository.save(booking);

        if (isConsumer)
            consumerRepository.incrementCancelledBookings(booking.getConsumer().getId());
        else
            providerRepository.incrementCancelledBookings(booking.getProvider().getId());

        return bookingMapper.toBookingResponse(booking);
    }

    public Page<BookingResponse> getBookingsByStatus(Long userId, BookingStatus status, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Page<Booking> bookings;
        if (user.getRole().equals(UserType.CONSUMER))
            bookings = bookingRepository.findByConsumerIdAndStatus(userId, status, pageable);
        else if (user.getRole().equals(UserType.PROVIDER))
            bookings = bookingRepository.findByProviderIdAndStatus(userId, status, pageable);
        else
            throw new ForbiddenException("User is not a provider or a consumer");

        return bookings.map(bookingMapper::toBookingResponse);
    }

    public List<BookingResponse> getUpcomingBookings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Booking> bookings;
        if (user.getRole().equals(UserType.CONSUMER)) {
            bookings = bookingRepository
                    .findByConsumerIdAndStatusAndRequestedDateAndRequestedStartTimeAfter(userId, BookingStatus.ACCEPTED,
                            LocalDate.now(), LocalTime.now());
        } else if (user.getRole().equals(UserType.PROVIDER)) {
            bookings = bookingRepository
                    .findByProviderIdAndStatusAndRequestedDateAndRequestedStartTimeAfter(userId, BookingStatus.ACCEPTED,
                            LocalDate.now(), LocalTime.now());
        } else
            throw new ForbiddenException("User is not a provider or a consumer");

        return bookings.stream().map(bookingMapper::toBookingResponse).toList();
    }
}
