package com.aykhedma.service;

import com.aykhedma.dto.request.AcceptBookingRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.request.CancelBookingRequest;
import com.aykhedma.dto.response.AcceptBookingResponse;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.MonthlyBookingStatsResponse;
import com.aykhedma.dto.response.WeeklyBookingStatsResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ForbiddenException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.BookingMapper;
import com.aykhedma.model.booking.*;
import com.aykhedma.model.notification.NotificationType;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final NotificationFactory notificationFactory;

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

        try {
            long buffer = (provider.getBookingBufferMinutes() != null) ? provider.getBookingBufferMinutes() : 30L;
            TimeSlot reservedForRequest = providerService.reserveTimeSlotWithBuffer(scheduleId,
                    requestedDate,
                    requestedTime,
                    requestedTime.plusMinutes(30),
                    buffer,
                    false);
            booking.setTimeSlot(reservedForRequest);
            bookingRepository.save(booking);
        } catch (Exception ignored) {
        }

        // Update provider stats in memory
        provider.setTotalRequests((provider.getTotalRequests() != null ? provider.getTotalRequests() : 0) + 1);
        updateProviderRates(provider);
        providerRepository.save(provider);

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("title", "New Booking Request");
        notificationData.put("content",
                "You have a new booking request from " + consumer.getName() + " for " + serviceType.getName() + ".");
        notificationData.put("message",
                "You have a new booking request from " + consumer.getName() + " for " + serviceType.getName() + ".");
        notificationData.put("bookingId", booking.getId());
        notificationData.put("providerName", provider.getName());
        notificationData.put("consumerName", consumer.getName());
        notificationData.put("serviceType", serviceType.getName());
        notificationData.put("requestedDate", requestedDate.toString());
        notificationData.put("requestedTime", requestedTime.toString());
        notificationData.put("problemDescription", problemDescription);
        notificationData.put("location", consumer.getLocation().getAddress());

        notificationFactory.send(
                provider.getId(),
                NotificationType.BOOKING_REQUEST,
                notificationData);

        return bookingMapper.toBookingResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse deleteBooking (Long consumerId, Long bookingId)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!consumerId.equals(booking.getConsumer().getId()))
            throw new ForbiddenException("Booking does not belong to this consumer");

        if (booking.getStatus() != BookingStatus.PENDING)
            throw new BadRequestException("Booking cannot be deleted, it has already been " + booking.getStatus());

        booking.setStatus(BookingStatus.DELETED);
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

        if (booking.getStatus() != BookingStatus.PENDING)
            throw new BadRequestException("Booking cannot be accepted, it has already been " + booking.getStatus());

        LocalDateTime bookingStartTime = LocalDateTime.of(booking.getRequestedDate(), booking.getRequestedStartTime());
        if (bookingStartTime.isBefore(LocalDateTime.now()))
            throw new BadRequestException("Booking cannot be accepted, its starting time has already passed");

        Long bookingId = booking.getId();
        LocalDate date = booking.getRequestedDate();
        LocalTime startTime = booking.getRequestedStartTime();
        Long estimatedDuration = acceptBookingRequest.getEstimatedDuration();
        LocalTime endTime = booking.getRequestedStartTime().plusMinutes(estimatedDuration);

        TimeSlot existingReservedSlot = null;
        boolean canReuseExistingReservedSlot = false;
        if (booking.getTimeSlot() != null) {
            existingReservedSlot = timeSlotRepository.findById(booking.getTimeSlot().getId()).orElse(null);
            if (existingReservedSlot != null) {
                long existingMinutes = Duration.between(existingReservedSlot.getStartTime(),
                        existingReservedSlot.getEndTime()).toMinutes();
                canReuseExistingReservedSlot = existingReservedSlot.getStartTime().equals(startTime)
                        && existingMinutes == estimatedDuration;

                if (!canReuseExistingReservedSlot) {
                    // Free request-time reservation before extending day/availability or reserving
                    // a larger duration.
                    providerService.restoreAvailabilityForCancelledBooking(booking);
                    existingReservedSlot = null;
                }
            }
        }

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
        } else {
            WorkingDay workingDay = workingDayRepository.findByScheduleIdAndDate(scheduleId, date)
                    .orElseThrow(() -> new ResourceNotFoundException("Working day by the booking date is not found"));

            if (endTime.isAfter(workingDay.getEndTime())) {
                extendWorkingDayAndAvailability(scheduleId, date, startTime, workingDay, endTime);
            }
        }

        List<Booking> conflictingBookings = bookingRepository.findConflictingBookings(providerId, bookingId, date,
                startTime, endTime);
        if (!conflictingBookings.isEmpty()) {
            return AcceptBookingResponse.builder()
                    .status("CONFLICT")
                    .conflictingBookings(conflictingBookings.stream()
                            .map(bookingMapper::toBookingResponse)
                            .toList())
                    .build();
        }

        long buffer = (provider.getBookingBufferMinutes() != null) ? provider.getBookingBufferMinutes() : 30L;
        TimeSlot reservedBookedSlot = canReuseExistingReservedSlot
                ? existingReservedSlot
                : providerService.reserveTimeSlotWithBuffer(scheduleId, date, startTime, endTime, buffer, true);

        booking.setEstimatedDuration(estimatedDuration);
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setAcceptedAt(LocalDateTime.now());
        booking.setTimeSlot(reservedBookedSlot);
        bookingRepository.save(booking);

        provider.setTotalBookings((provider.getTotalBookings() != null ? provider.getTotalBookings() : 0) + 1);
        updateProviderRates(provider);
        providerRepository.save(provider);

        Consumer consumer = booking.getConsumer();
        consumer.setTotalBookings((consumer.getTotalBookings() != null ? consumer.getTotalBookings() : 0) + 1);
        updateConsumerRates(consumer);
        consumerRepository.save(consumer);

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("title", "Booking Confirmed");
        notificationData.put("content",
                "Your booking for " + booking.getServiceType().getName() + " on " + booking.getRequestedDate() + " at "
                        + booking.getRequestedStartTime() + " has been accepted by " + provider.getName() + ".");
        notificationData.put("message",
                "Your booking for " + booking.getServiceType().getName() + " on " + booking.getRequestedDate() + " at "
                        + booking.getRequestedStartTime() + " has been accepted by " + provider.getName() + ".");
        notificationData.put("bookingId", booking.getId());
        notificationData.put("serviceName", booking.getServiceType().getName());
        notificationData.put("bookingDate", booking.getRequestedDate().toString());
        notificationData.put("bookingTime", booking.getRequestedStartTime().toString());
        notificationData.put("providerName", provider.getName());

        notificationFactory.send(
                booking.getConsumer().getId(),
                NotificationType.BOOKING_CONFIRMATION,
                notificationData);

        return AcceptBookingResponse.builder()
                .status("ACCEPTED")
                .booking(bookingMapper.toBookingResponse(booking))
                .build();
    }

    private void extendWorkingDayAndAvailability(Long scheduleId,
            LocalDate date,
            LocalTime bookingStart,
            WorkingDay workingDay,
            LocalTime bookingEnd) {
        LocalTime originalEndTime = workingDay.getEndTime();
        workingDay.setEndTime(bookingEnd);
        workingDayRepository.save(workingDay);

        List<TimeSlot> availableSlots = timeSlotRepository.findByScheduleIdAndDateAndStatus(
                scheduleId,
                date,
                TimeSlotStatus.AVAILABLE);

        TimeSlot slotToExtend = availableSlots.stream()
                .filter(slot -> slot.getStartTime().compareTo(bookingStart) <= 0)
                .filter(slot -> slot.getEndTime().equals(originalEndTime))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Selected start time with duration is not available"));

        slotToExtend.setEndTime(bookingEnd);
        timeSlotRepository.save(slotToExtend);
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

        if (booking.getStatus() != BookingStatus.PENDING)
            throw new BadRequestException("Booking cannot be declined, it has already been " + booking.getStatus());

        LocalDateTime bookingStartTime = LocalDateTime.of(booking.getRequestedDate(), booking.getRequestedStartTime());
        if (bookingStartTime.isBefore(LocalDateTime.now()))
            throw new BadRequestException("Booking cannot be declined, its starting time has already passed");

        providerService.restoreAvailabilityForCancelledBooking(booking);

        booking.setStatus(BookingStatus.DECLINED);
        booking.setDeclinedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        updateProviderRates(booking.getProvider());
        providerRepository.save(booking.getProvider());

        Map<String, Object> declineNotificationData = new HashMap<>();
        declineNotificationData.put("title", "Booking Request Declined");
        declineNotificationData.put("content",
                "Your booking request for " + booking.getServiceType().getName() + " on " + booking.getRequestedDate()
                        + " at "
                        + booking.getRequestedStartTime() + " was declined by " + booking.getProvider().getName()
                        + ".");
        declineNotificationData.put("message",
                "Your booking request for " + booking.getServiceType().getName() + " on " + booking.getRequestedDate()
                        + " at "
                        + booking.getRequestedStartTime() + " was declined by " + booking.getProvider().getName()
                        + ".");
        declineNotificationData.put("bookingId", booking.getId());
        declineNotificationData.put("serviceName", booking.getServiceType().getName());
        declineNotificationData.put("bookingDate", booking.getRequestedDate().toString());
        declineNotificationData.put("bookingTime", booking.getRequestedStartTime().toString());
        declineNotificationData.put("providerName", booking.getProvider().getName());
        declineNotificationData.put("cancelledBy", "P");
        declineNotificationData.put("cancelledByName", booking.getProvider().getName());
        declineNotificationData.put("reason", "The provider declined this booking request.");

        notificationFactory.send(
                booking.getConsumer().getId(),
                NotificationType.BOOKING_CANCELLED,
                declineNotificationData);

        return bookingMapper.toBookingResponse(booking);
    }

    @Override
    public WeeklyBookingStatsResponse getWeeklyBookingStats(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        Object[] result = (Object[]) bookingRepository.findBookingStatsCurrentWeek(providerId, LocalDate.now());

        Integer acceptedAndCompletedBookings = result[0] != null ? ((Number) result[0]).intValue() : 0;
        Integer cancelledBookings = result[1] != null ? ((Number) result[1]).intValue() : 0;

        return WeeklyBookingStatsResponse.builder()
                .acceptedAndCompletedBookings(acceptedAndCompletedBookings)
                .cancelledBookings(cancelledBookings)
                .build();
    }

    @Override
    public MonthlyBookingStatsResponse getMonthlyBookingStats(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        List<Object[]> results = bookingRepository.findBookingStatsLastSixMonths(providerId, LocalDate.now());
        List<String> months = new ArrayList<>();
        List<Integer> completedBookings = new ArrayList<>(), cancelledBookings = new ArrayList<>();

        for (Object[] row : results) {
            months.add((String) row[0]);
            completedBookings.add(row[1] != null ? ((Number) row[1]).intValue() : 0);
            cancelledBookings.add(row[2] != null ? ((Number) row[2]).intValue() : 0);
        }

        return MonthlyBookingStatsResponse.builder()
                .months(months)
                .completedBookings(completedBookings)
                .cancelledBookings(cancelledBookings)
                .build();
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long userId, CancelBookingRequest cancelBookingRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Booking booking = bookingRepository.findById(cancelBookingRequest.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        boolean isConsumer;
        if (user.getRole() == UserType.CONSUMER) {
            if (!userId.equals(booking.getConsumer().getId()))
                throw new ForbiddenException("Booking does not belong to this consumer");

            isConsumer = true;
        } else if (user.getRole() == UserType.PROVIDER) {
            if (!userId.equals(booking.getProvider().getId()))
                throw new ForbiddenException("Booking does not belong to this provider");

            isConsumer = false;
        } else
            throw new ForbiddenException("User is not a provider or a consumer");

        if (booking.getStatus() != BookingStatus.ACCEPTED)
            throw new BadRequestException("Booking cannot be cancelled, it has already been " + booking.getStatus());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bookingStartTime = LocalDateTime.of(booking.getRequestedDate(), booking.getRequestedStartTime());
        if (bookingStartTime.isBefore(now))
            throw new BadRequestException("Booking cannot be cancelled, its starting time has already passed");

        boolean applyPenalty = now.isAfter(bookingStartTime.minusHours(2));

        providerService.restoreAvailabilityForCancelledBooking(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(cancelBookingRequest.getCancellationReason());
        booking.setCancelledAt(now);
        if (isConsumer)
            booking.setCancelledBy("C");
        else
            booking.setCancelledBy("P");
        bookingRepository.save(booking);

        if (isConsumer) {
            Consumer consumer = booking.getConsumer();
            consumer.setCancelledBookings(
                    (consumer.getCancelledBookings() != null ? consumer.getCancelledBookings() : 0) + 1);

            if (applyPenalty) {
                double currentRating = consumer.getAverageRating() != null ? consumer.getAverageRating() : 0.0;
                double newRating = Math.max(0.0, currentRating - 0.2);
                consumer.setAverageRating(Math.round(newRating * 10.0) / 10.0);
            }

            updateConsumerRates(consumer);
            consumerRepository.save(consumer);
        } else {
            Provider provider = booking.getProvider();
            provider.setCancelledBookings(
                    (provider.getCancelledBookings() != null ? provider.getCancelledBookings() : 0) + 1);

            if (applyPenalty) {
                double currentRating = provider.getAverageRating() != null ? provider.getAverageRating() : 0.0;
                double newRating = Math.max(0.0, currentRating - 0.2);
                provider.setAverageRating(Math.round(newRating * 10.0) / 10.0);
            }

            updateProviderRates(provider);
            providerRepository.save(provider);
        }

        Long recipientId = isConsumer ? booking.getProvider().getId() : booking.getConsumer().getId();
        String cancelledByCode = isConsumer ? "C" : "P";
        String cancelledByName = isConsumer ? booking.getConsumer().getName() : booking.getProvider().getName();

        Map<String, Object> cancellationNotificationData = new HashMap<>();
        cancellationNotificationData.put("title", "Booking Cancelled");
        cancellationNotificationData.put("content",
                "The booking for " + booking.getServiceType().getName() + " on " + booking.getRequestedDate() + " at "
                        + booking.getRequestedStartTime() + " has been cancelled.");
        cancellationNotificationData.put("message",
                "The booking for " + booking.getServiceType().getName() + " on " + booking.getRequestedDate() + " at "
                        + booking.getRequestedStartTime() + " has been cancelled.");
        cancellationNotificationData.put("bookingId", booking.getId());
        cancellationNotificationData.put("serviceName", booking.getServiceType().getName());
        cancellationNotificationData.put("bookingDate", booking.getRequestedDate().toString());
        cancellationNotificationData.put("bookingTime", booking.getRequestedStartTime().toString());
        cancellationNotificationData.put("providerName", booking.getProvider().getName());
        cancellationNotificationData.put("cancelledBy", cancelledByCode);
        cancellationNotificationData.put("cancelledByName", cancelledByName);
        cancellationNotificationData.put("reason",
                booking.getCancellationReason() != null && !booking.getCancellationReason().isBlank()
                        ? booking.getCancellationReason()
                        : "No reason provided");

        notificationFactory.send(
                recipientId,
                NotificationType.BOOKING_CANCELLED,
                cancellationNotificationData);

        return bookingMapper.toBookingResponse(booking);
    }

    public Page<BookingResponse> getBookingsByStatus(Long userId, BookingStatus status, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Page<Booking> bookings;
        if (user.getRole() == UserType.CONSUMER) {
            if (status == null)
                bookings = bookingRepository.findByConsumerId(userId, pageable);
            else
                bookings = bookingRepository.findByConsumerIdAndStatus(userId, status, pageable);
        } else if (user.getRole() == UserType.PROVIDER) {
            if (status == null)
                bookings = bookingRepository.findByProviderId(userId, pageable);
            else
                bookings = bookingRepository.findByProviderIdAndStatus(userId, status, pageable);
        } else
            throw new ForbiddenException("User is not a provider or a consumer");

        return bookings.map(bookingMapper::toBookingResponse);
    }

    public List<BookingResponse> getUpcomingBookings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Booking> bookings;

        if (user.getRole() == UserType.CONSUMER || user.getRole() == UserType.PROVIDER)
            bookings = bookingRepository.findUpcomingBookings(userId, LocalDate.now(), LocalTime.now());
        else
            throw new ForbiddenException("User is not a provider or a consumer");

        return bookings.stream().map(bookingMapper::toBookingResponse).toList();
    }

    @Override
    @Transactional
    public BookingResponse submitRating(Long consumerId, com.aykhedma.dto.request.RatingRequest ratingRequest) {
        Booking booking = bookingRepository.findById(ratingRequest.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!consumerId.equals(booking.getConsumer().getId()))
            throw new ForbiddenException("Booking does not belong to this consumer");

        LocalDateTime serviceStartTime = LocalDateTime.of(booking.getRequestedDate(), booking.getRequestedStartTime());
        if (serviceStartTime.plusMinutes(30).isAfter(LocalDateTime.now()))
            throw new BadRequestException("Rating is allowed only 30 minutes after service start");

        if (booking.getStatus() == BookingStatus.CANCELLED)
            throw new BadRequestException("Cancelled bookings cannot be rated");

        if (booking.getStatus() != BookingStatus.ACCEPTED && booking.getStatus() != BookingStatus.COMPLETED)
            throw new BadRequestException("Only accepted or completed bookings can be rated");

        if (booking.getConsumerRating() != null)
            throw new BadRequestException("You have already rated this booking");

        booking.setPunctualityRating(ratingRequest.getPunctualityRating().doubleValue());
        booking.setCommitmentRating(ratingRequest.getCommitmentRating().doubleValue());
        booking.setQualityOfWorkRating(ratingRequest.getQualityOfWorkRating().doubleValue());

        Double overallRating = (booking.getPunctualityRating() + booking.getCommitmentRating()
                + booking.getQualityOfWorkRating()) / 3.0;
        // Keep to 1 decimal place
        overallRating = Math.round(overallRating * 10.0) / 10.0;
        booking.setConsumerRating(overallRating);
        booking.setConsumerReview(ratingRequest.getReview()); // consumerReview stores the review FROM consumer TO
                                                              // provider

        // Mark as completed if both parties have rated
        if (booking.getProviderRating() != null) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setCompletedAt(LocalDateTime.now());
            Provider provider = booking.getProvider();
            provider.setCompletedJobs((provider.getCompletedJobs() != null ? provider.getCompletedJobs() : 0) + 1);
            // No need to call updateProviderRates here separately if we call it below
            // anyway
        }

        bookingRepository.save(booking);

        // Update provider averages
        Provider provider = booking.getProvider();
        // Since we are adding one more rating, we can calculate it dynamically or
        // update using formula.
        // Assuming completedJobs is already incremented when booking was marked
        // COMPLETED.
        // If not, we should probably calculate from all completed ratings.
        long ratedBookingsCount = bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId());

        if (ratedBookingsCount <= 1 || provider.getAverageRating() == null || provider.getAverageRating() == 0.0) {
            provider.setAveragePunctualityRating(booking.getPunctualityRating());
            provider.setAverageCommitmentRating(booking.getCommitmentRating());
            provider.setAverageQualityOfWorkRating(booking.getQualityOfWorkRating());
            provider.setAverageRating(overallRating);
        } else {
            // Because completed jobs usually include unrated ones, it's safer to use a
            // count of rated bookings.
            // Formula: new_avg = ((old_avg * old_count) + new_rating) / new_count
            long oldCount = ratedBookingsCount - 1; // since this booking was already saved and is included in the count
            // However, the count query includes this booking because we just saved it and
            // the transaction is open.
            if (oldCount < 1)
                oldCount = 1;

            double oldPunctuality = provider.getAveragePunctualityRating() != null
                    ? provider.getAveragePunctualityRating()
                    : 0.0;
            double oldCommitment = provider.getAverageCommitmentRating() != null ? provider.getAverageCommitmentRating()
                    : 0.0;
            double oldQuality = provider.getAverageQualityOfWorkRating() != null
                    ? provider.getAverageQualityOfWorkRating()
                    : 0.0;
            double oldOverall = provider.getAverageRating() != null ? provider.getAverageRating() : 0.0;

            provider.setAveragePunctualityRating(
                    ((oldPunctuality * oldCount) + booking.getPunctualityRating()) / ratedBookingsCount);
            provider.setAverageCommitmentRating(
                    ((oldCommitment * oldCount) + booking.getCommitmentRating()) / ratedBookingsCount);
            provider.setAverageQualityOfWorkRating(
                    ((oldQuality * oldCount) + booking.getQualityOfWorkRating()) / ratedBookingsCount);
            provider.setAverageRating(
                    ((oldOverall * oldCount) + overallRating) / ratedBookingsCount);
        }
        updateProviderRates(provider);
        providerRepository.save(provider);

        return bookingMapper.toBookingResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse submitConsumerRating(Long providerId,
            com.aykhedma.dto.request.ProviderRatingRequest ratingRequest) {
        Booking booking = bookingRepository.findById(ratingRequest.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!providerId.equals(booking.getProvider().getId()))
            throw new ForbiddenException("Booking does not belong to this provider");

        LocalDateTime serviceStartTime = LocalDateTime.of(booking.getRequestedDate(), booking.getRequestedStartTime());
        if (serviceStartTime.plusMinutes(30).isAfter(LocalDateTime.now()))
            throw new BadRequestException("Rating is allowed only 30 minutes after service start");

        if (booking.getStatus() == BookingStatus.CANCELLED)
            throw new BadRequestException("Cancelled bookings cannot be rated");

        if (booking.getStatus() != BookingStatus.ACCEPTED && booking.getStatus() != BookingStatus.COMPLETED)
            throw new BadRequestException("Only accepted or completed bookings can be rated");

        // providerRating stores score given BY provider TO consumer
        if (booking.getProviderRating() != null)
            throw new BadRequestException("You have already rated this booking");

        booking.setProviderRating(ratingRequest.getRating().doubleValue());
        booking.setProviderReview(ratingRequest.getReview());

        // Mark as completed if both parties have rated
        if (booking.getConsumerRating() != null) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setCompletedAt(LocalDateTime.now());
            Provider provider = booking.getProvider();
            provider.setCompletedJobs((provider.getCompletedJobs() != null ? provider.getCompletedJobs() : 0) + 1);
        }

        bookingRepository.save(booking);
        updateProviderRates(booking.getProvider());
        providerRepository.save(booking.getProvider());

        // Update consumer average
        Consumer consumer = booking.getConsumer();
        long ratedBookingsCount = bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId());

        if (ratedBookingsCount <= 1 || consumer.getAverageRating() == null || consumer.getAverageRating() == 0.0) {
            consumer.setAverageRating(booking.getProviderRating());
        } else {
            long oldCount = ratedBookingsCount - 1;
            if (oldCount < 1)
                oldCount = 1;

            double oldOverall = consumer.getAverageRating() != null ? consumer.getAverageRating() : 0.0;
            consumer.setAverageRating(
                    ((oldOverall * oldCount) + booking.getProviderRating()) / ratedBookingsCount);
        }
        consumerRepository.save(consumer);

        try {
            notificationFactory.send(consumer.getId(),
                    NotificationType.REVIEW_RECEIVED,
                    Map.of(
                            "title", "New Review Received",
                            "message",
                            booking.getProvider().getName() + " left you a review: " + ratingRequest.getReview(),
                            "rating", String.valueOf(booking.getProviderRating()),
                            "bookingId", booking.getId().toString()));
        } catch (Exception ignored) {
        }

        return bookingMapper.toBookingResponse(booking);
    }

    private void updateProviderRates(Provider provider) {
        int acceptanceRate;
        int bookingRate;

        Integer totalRequests = provider.getTotalRequests();
        if (totalRequests != null && totalRequests > 0) {
            // Acceptance Rate = (Accepted Bookings / Total Requests) * 100
            // totalBookings is used as accepted count in this system
            Integer accepted = provider.getTotalBookings() != null ? provider.getTotalBookings() : 0;
            acceptanceRate = clampRate((accepted * 100) / totalRequests);

            // Booking Rate = (Completed Jobs / Total Requests) * 100
            Integer completed = provider.getCompletedJobs() != null ? provider.getCompletedJobs() : 0;
            bookingRate = clampRate((completed * 100) / totalRequests);
        } else {
            acceptanceRate = 100;
            bookingRate = 0;
        }

        provider.setAcceptanceRate(acceptanceRate);
        provider.setBookingRate(bookingRate);
        provider.setCancellationRate(provider.getCancellationRate()); // This uses the helper method to get the value
    }

    private void updateConsumerRates(Consumer consumer) {
        consumer.setCancellationRate(consumer.getCancellationRate()); // This uses the helper method to get the value
    }

    private int clampRate(int value) {
        return Math.max(0, Math.min(100, value));
    }

    @Override
    public List<com.aykhedma.dto.response.ConsumerReviewResponse> getConsumerReviews(Long consumerId) {
        if (!consumerRepository.existsById(consumerId)) {
            throw new ResourceNotFoundException("Consumer not found");
        }
        List<Booking> bookings = bookingRepository.findByConsumerIdAndProviderRatingIsNotNull(consumerId);
        return bookings.stream()
                .map(booking -> com.aykhedma.dto.response.ConsumerReviewResponse.builder()
                        .id(booking.getId())
                        .providerId(booking.getProvider().getId())
                        .providerName(booking.getProvider().getName())
                        .rating(booking.getProviderRating())
                        .review(booking.getProviderReview())
                        .completedAt(booking.getCompletedAt())
                        .build())
                .toList();
    }
}
