package com.aykhedma.controller;

import com.aykhedma.dto.request.AcceptBookingRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.request.CancelBookingRequest;
import com.aykhedma.dto.response.AcceptBookingResponse;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.model.booking.BookingStatus;
import com.aykhedma.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController
{
    private final BookingService bookingService;

    // =================================== Consumer Side ===================================

    @PreAuthorize("hasRole('CONSUMER')")
    @PostMapping("/request-booking")
    @Operation(summary = "Request a booking from a provider")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "201", description = "Booking requested successfully",
                           content = @Content(schema = @Schema(implementation = BookingResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid booking request data"),
                   @ApiResponse(responseCode = "404", description = "Consumer or provider not found")
            })
    public ResponseEntity<BookingResponse> requestBooking(
            @Parameter(description = "ID of the consumer", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @Parameter(description = "Booking request data (provider ID, date, start time, and problem description)", required = true)
            @Valid @RequestBody BookingRequest request)
    {
        BookingResponse response = bookingService.requestBooking(consumerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =================================== Provider Side ===================================

    @PreAuthorize("hasRole('PROVIDER')")
    @PostMapping("/accept-booking")
    @Operation(summary = "Accept a consumer's booking")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Booking accepted successfully",
                           content = @Content(schema = @Schema(implementation = AcceptBookingResponse.class))),
                   @ApiResponse(responseCode = "200", description = "Booking processed with warning (end time exceeds working hours)",
                           content = @Content(schema = @Schema(implementation = AcceptBookingResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid booking to be accepted"),
                   @ApiResponse(responseCode = "403", description = "Provider does not own this booking"),
                   @ApiResponse(responseCode = "404", description = "Provider or booking not found"),
                   @ApiResponse(responseCode = "409", description = "Booking will conflict with other bookings",
                           content = @Content(schema = @Schema(implementation = AcceptBookingResponse.class)))
            })
    public ResponseEntity<AcceptBookingResponse> acceptBooking(
            @Parameter(description = "ID of the provider", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long providerId,
            @Parameter(description = "Accept booking request data (booking ID and estimated duration in minutes)", required = true)
            @Valid @RequestBody AcceptBookingRequest request)
    {
        AcceptBookingResponse response = bookingService.acceptBooking(providerId, request);
        if (response.getStatus().equals("CONFLICT"))
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        else
            return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasRole('PROVIDER')")
    @PostMapping("/decline-booking/{bookingId}")
    @Operation(summary = "Decline a consumer's booking")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Booking declined successfully",
                           content = @Content(schema = @Schema(implementation = BookingResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid booking to be declined"),
                   @ApiResponse(responseCode = "403", description = "Provider does not own this booking"),
                   @ApiResponse(responseCode = "404", description = "Provider or booking not found")
            })
    public ResponseEntity<BookingResponse> declineBooking(
            @Parameter(description = "ID of the provider", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long providerId,
            @Parameter(description = "ID of the booking", required = true)
            @PathVariable Long bookingId)
    {
        BookingResponse response = bookingService.declineBooking(providerId, bookingId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // =================================== User Side ===================================

    @PreAuthorize("hasAnyRole('PROVIDER','CONSUMER')")
    @PostMapping("/cancel-booking")
    @Operation(summary = "Cancel user's booking")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Booking cancelled successfully",
                           content = @Content(schema = @Schema(implementation = BookingResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid booking to be cancelled"),
                   @ApiResponse(responseCode = "403", description = "User does not own this booking"),
                   @ApiResponse(responseCode = "403", description = "User is not a provider or a consumer"),
                   @ApiResponse(responseCode = "404", description = "User or booking not found")
            })
    public ResponseEntity<BookingResponse> cancelBooking(
            @Parameter(description = "ID of the user", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long userId,
            @Parameter(description = "Cancel booking request data (booking ID and cancellation reason)", required = true)
            @Valid @RequestBody CancelBookingRequest request)
    {
        BookingResponse response = bookingService.cancelBooking(userId, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasAnyRole('PROVIDER','CONSUMER')")
    @GetMapping("/get-bookings")
    @Operation(summary = "Get user's bookings by booking status")
        @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Bookings retrieved successfully",
                           content = @Content(schema = @Schema(implementation = BookingResponse.class))),
                   @ApiResponse(responseCode = "403", description = "User is not a provider or a consumer"),
                   @ApiResponse(responseCode = "404", description = "User not found"),
                   @ApiResponse(responseCode = "500", description = "Invalid booking status")
            })
    public ResponseEntity<Page<BookingResponse>> getBookingsByStatus(
            @Parameter(description = "ID of the user", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long userId,
            @Parameter(description = "Bookings' status")
            @RequestParam(required = false) BookingStatus status,
            @Parameter(description = "Sorting specifications for the returned bookings")Pageable pageable)
    {
        Page<BookingResponse> response = bookingService.getBookingsByStatus(userId, status, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasAnyRole('PROVIDER','CONSUMER')")
    @GetMapping("/upcoming-bookings")
    @Operation(summary = "Get user's upcoming bookings for the day")
        @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Bookings retrieved successfully",
                           content = @Content(schema = @Schema(implementation = BookingResponse.class))),
                   @ApiResponse(responseCode = "403", description = "User is not a provider or a consumer"),
                   @ApiResponse(responseCode = "404", description = "User not found")
            })
    public ResponseEntity<List<BookingResponse>> getUpcomingBookings(
            @Parameter(description = "ID of the user", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long userId)
    {
        List<BookingResponse> response = bookingService.getUpcomingBookings(userId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
