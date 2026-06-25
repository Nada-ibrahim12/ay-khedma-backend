package com.aykhedma.service;

import com.aykhedma.dto.request.*;
import com.aykhedma.dto.response.*;
import com.aykhedma.model.booking.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookingService
{
    BookingResponse requestBooking (Long consumerId, BookingRequest bookingRequest);

    BookingResponse deleteBooking (Long consumerId, Long bookingId);

    BookingResponse completeBooking (Long consumerId, Long bookingId);

    AcceptBookingResponse acceptBooking (Long providerId, AcceptBookingRequest acceptBookingRequest);

    BookingResponse declineBooking (Long providerId, Long bookingId);

    WeeklyBookingStatsResponse getWeeklyBookingStats (Long providerId);

    MonthlyBookingStatsResponse getMonthlyBookingStats (Long providerId);

    BookingResponse cancelBooking (Long userId, CancelBookingRequest cancelBookingRequest);

    Page<BookingResponse> getBookingsByStatus (Long userId, BookingStatus status, Pageable pageable);

    List<BookingResponse> getUpcomingBookings (Long userId);

    BookingResponse submitRating(Long consumerId, RatingRequest ratingRequest);

    BookingResponse submitConsumerRating(Long providerId, ProviderRatingRequest ratingRequest);

    List<ConsumerReviewResponse> getConsumerReviews(Long consumerId);
}
