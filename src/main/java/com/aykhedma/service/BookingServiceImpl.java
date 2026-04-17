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
public class BookingServiceImpl implements BookingService
{
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
    public BookingResponse requestBooking (Long consumerId, BookingRequest bookingRequest)
    {
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
    public AcceptBookingResponse acceptBooking (Long providerId, AcceptBookingRequest acceptBookingRequest)
    {
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

        if (!acceptBookingRequest.isOverrideWorkingHours())
        {
            WorkingDay workingDay = workingDayRepository.findByScheduleIdAndDate(scheduleId, date)
                    .orElseThrow(() -> new ResourceNotFoundException("Working day by the booking date is not found"));

            if (endTime.isAfter(workingDay.getEndTime()))
            {
                return AcceptBookingResponse.builder()
                        .status("WARNING")
                        .warningMessage("The booking end time will exceed the end time of the working day")
                        .build();
            }
        }

        List<Booking> conflictingBookings = bookingRepository.findConflictingBookings(providerId, bookingId, date, startTime, endTime);
        if (!conflictingBookings.isEmpty())
        {
            return AcceptBookingResponse.builder()
                    .status("CONFLICT")
                    .conflictingBookings(conflictingBookings.stream()
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

        providerRepository.incrementTotalBookings(booking.getProvider().getId());
        consumerRepository.incrementTotalBookings(booking.getConsumer().getId());

        return AcceptBookingResponse.builder()
                .status("ACCEPTED")
                .booking(bookingMapper.toBookingResponse(booking))
                .build();
    }

    @Override
    @Transactional
    public BookingResponse declineBooking (Long providerId, Long bookingId)
    {
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
    public BookingResponse cancelBooking (Long userId, CancelBookingRequest cancelBookingRequest)
    {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Booking booking = bookingRepository.findById(cancelBookingRequest.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        boolean isConsumer;
        if (user.getRole().equals(UserType.CONSUMER))
        {
            if (!userId.equals(booking.getConsumer().getId()))
                throw new ForbiddenException("Booking does not belong to this consumer");

            isConsumer = true;
        }
        else if (user.getRole().equals(UserType.PROVIDER))
        {
            if (!userId.equals(booking.getProvider().getId()))
                throw new ForbiddenException("Booking does not belong to this provider");

            isConsumer = false;
        }
        else
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

    public Page<BookingResponse> getBookingsByStatus (Long userId, BookingStatus status, Pageable pageable)
    {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Page<Booking>  bookings;
        if (user.getRole().equals(UserType.CONSUMER))
        {
            if (status == null)
                bookings = bookingRepository.findByConsumerId(userId, pageable);
            else
                bookings = bookingRepository.findByConsumerIdAndStatus(userId, status, pageable);
        }
        else if (user.getRole().equals(UserType.PROVIDER))
        {
            if (status == null)
                bookings = bookingRepository.findByProviderId(userId, pageable);
            else
                bookings = bookingRepository.findByProviderIdAndStatus(userId, status, pageable);
        }
        else
            throw new ForbiddenException("User is not a provider or a consumer");

        return bookings.map(bookingMapper::toBookingResponse);
    }

    public List<BookingResponse> getUpcomingBookings (Long userId)
    {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Booking>  bookings;
        if (user.getRole().equals(UserType.CONSUMER))
        {
            bookings = bookingRepository
                    .findByConsumerIdAndStatusAndRequestedDateAndRequestedStartTimeAfter
                            (userId, BookingStatus.ACCEPTED, LocalDate.now(), LocalTime.now());
        }
        else if (user.getRole().equals(UserType.PROVIDER))
        {
            bookings = bookingRepository
                    .findByProviderIdAndStatusAndRequestedDateAndRequestedStartTimeAfter
                            (userId, BookingStatus.ACCEPTED, LocalDate.now(), LocalTime.now());
        }
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

        if (booking.getStatus() != BookingStatus.COMPLETED)
            throw new BadRequestException("Only completed bookings can be rated");

        if (booking.getConsumerRating() != null)
            throw new BadRequestException("Booking has already been rated");

        booking.setPunctualityRating(ratingRequest.getPunctualityRating().doubleValue());
        booking.setCommitmentRating(ratingRequest.getCommitmentRating().doubleValue());
        booking.setQualityOfWorkRating(ratingRequest.getQualityOfWorkRating().doubleValue());
        
        Double overallRating = (booking.getPunctualityRating() + booking.getCommitmentRating() + booking.getQualityOfWorkRating()) / 3.0;
        // Keep to 1 decimal place
        overallRating = Math.round(overallRating * 10.0) / 10.0;
        booking.setConsumerRating(overallRating);
        booking.setConsumerReview(ratingRequest.getReview()); // consumerReview stores the review FROM consumer TO provider
        
        bookingRepository.save(booking);

        // Update provider averages
        Provider provider = booking.getProvider();
        // Since we are adding one more rating, we can calculate it dynamically or update using formula.
        // Assuming completedJobs is already incremented when booking was marked COMPLETED.
        // If not, we should probably calculate from all completed ratings.
        long ratedBookingsCount = bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId());

        if (ratedBookingsCount <= 1 || provider.getAverageRating() == null || provider.getAverageRating() == 0.0) {
            provider.setAveragePunctualityRating(booking.getPunctualityRating());
            provider.setAverageCommitmentRating(booking.getCommitmentRating());
            provider.setAverageQualityOfWorkRating(booking.getQualityOfWorkRating());
            provider.setAverageRating(overallRating);
        } else {
            // Because completed jobs usually include unrated ones, it's safer to use a count of rated bookings.
            // Formula: new_avg = ((old_avg * old_count) + new_rating) / new_count
            long oldCount = ratedBookingsCount - 1; // since this booking was already saved and is included in the count
            // However, the count query includes this booking because we just saved it and the transaction is open.
            if (oldCount < 1) oldCount = 1;

            double oldPunctuality = provider.getAveragePunctualityRating() != null ? provider.getAveragePunctualityRating() : 0.0;
            double oldCommitment = provider.getAverageCommitmentRating() != null ? provider.getAverageCommitmentRating() : 0.0;
            double oldQuality = provider.getAverageQualityOfWorkRating() != null ? provider.getAverageQualityOfWorkRating() : 0.0;
            double oldOverall = provider.getAverageRating() != null ? provider.getAverageRating() : 0.0;

            provider.setAveragePunctualityRating(
                ((oldPunctuality * oldCount) + booking.getPunctualityRating()) / ratedBookingsCount
            );
            provider.setAverageCommitmentRating(
                ((oldCommitment * oldCount) + booking.getCommitmentRating()) / ratedBookingsCount
            );
            provider.setAverageQualityOfWorkRating(
                ((oldQuality * oldCount) + booking.getQualityOfWorkRating()) / ratedBookingsCount
            );
            provider.setAverageRating(
                ((oldOverall * oldCount) + overallRating) / ratedBookingsCount
            );
        }
        providerRepository.save(provider);

        return bookingMapper.toBookingResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse submitConsumerRating(Long providerId, com.aykhedma.dto.request.ProviderRatingRequest ratingRequest) {
        Booking booking = bookingRepository.findById(ratingRequest.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!providerId.equals(booking.getProvider().getId()))
            throw new ForbiddenException("Booking does not belong to this provider");

        if (booking.getStatus() != BookingStatus.COMPLETED)
            throw new BadRequestException("Only completed bookings can be rated");

        // providerRating stores score given BY provider TO consumer
        if (booking.getProviderRating() != null)
            throw new BadRequestException("Booking has already been rated");

        booking.setProviderRating(ratingRequest.getRating().doubleValue());
        booking.setProviderReview(ratingRequest.getReview());
        
        bookingRepository.save(booking);

        // Update consumer average
        Consumer consumer = booking.getConsumer();
        long ratedBookingsCount = bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId());

        if (ratedBookingsCount <= 1 || consumer.getAverageRating() == null || consumer.getAverageRating() == 0.0) {
            consumer.setAverageRating(booking.getProviderRating());
        } else {
            long oldCount = ratedBookingsCount - 1;
            if (oldCount < 1) oldCount = 1;

            double oldOverall = consumer.getAverageRating() != null ? consumer.getAverageRating() : 0.0;
            consumer.setAverageRating(
                ((oldOverall * oldCount) + booking.getProviderRating()) / ratedBookingsCount
            );
        }
        consumerRepository.save(consumer);

        return bookingMapper.toBookingResponse(booking);
    }
}
