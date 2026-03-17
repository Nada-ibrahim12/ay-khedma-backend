package com.aykhedma.service;

import com.aykhedma.dto.request.AcceptBookingRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.request.CancelBookingRequest;
import com.aykhedma.dto.response.AcceptBookingResponse;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.model.booking.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookingService {
    BookingResponse requestBooking(Long consumerId, BookingRequest bookingRequest);

    AcceptBookingResponse acceptBooking(Long providerId, AcceptBookingRequest acceptBookingRequest);

    BookingResponse declineBooking(Long providerId, Long bookingId);

    BookingResponse cancelBooking(Long userId, CancelBookingRequest cancelBookingRequest);

    Page<BookingResponse> getFilteredBookings(Long userId, BookingStatus status, boolean upcoming, Pageable pageable);

}
