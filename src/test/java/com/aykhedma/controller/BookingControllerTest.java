package com.aykhedma.controller;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.dto.request.AcceptBookingRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.request.CancelBookingRequest;
import com.aykhedma.dto.response.AcceptBookingResponse;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.ConsumerSummaryResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.model.booking.BookingStatus;
import com.aykhedma.security.CustomUserDetailsService;
import com.aykhedma.security.JwtService;
import com.aykhedma.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = BookingController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("BookingController Tests")
class BookingControllerTest
{
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private BookingResponse bookingResponse;
    private AcceptBookingResponse acceptSuccessResponse;
    private AcceptBookingResponse acceptConflictResponse;
    private AcceptBookingResponse acceptWarningResponse;
    private final Long consumerId = 2L;
    private final Long providerId = 1L;
    private final Long bookingId = 10L;

    @BeforeEach
    void setUp()
    {
        ConsumerSummaryResponse consumerSummary = ConsumerSummaryResponse.builder()
                .id(consumerId)
                .name("Test Consumer")
                .build();

        ProviderSummaryResponse providerSummary = ProviderSummaryResponse.builder()
                .id(providerId)
                .name("Test Provider")
                .build();

        bookingResponse = BookingResponse.builder()
                .id(bookingId)
                .consumer(consumerSummary)
                .provider(providerSummary)
                .requestedDate(LocalDate.now().plusDays(1))
                .requestedStartTime(LocalTime.of(10, 0))
                .estimatedDuration(60L)
                .problemDescription("Test problem")
                .status(BookingStatus.PENDING)
                .build();

        acceptSuccessResponse = AcceptBookingResponse.builder()
                .status("ACCEPTED")
                .booking(bookingResponse)
                .build();

        acceptConflictResponse = AcceptBookingResponse.builder()
                .status("CONFLICT")
                .conflictingBookings(List.of(bookingResponse))
                .build();

        acceptWarningResponse = AcceptBookingResponse.builder()
                .status("WARNING")
                .warningMessage("The booking end time will exceed the end time of the working day")
                .build();
    }

    @Nested
    @DisplayName("Consumer Side Endpoints")
    class ConsumerSideTest
    {
        @Test
        @WithMockUser(roles = "CONSUMER")
        @DisplayName("Request Booking - Success")
        void requestBookingSuccess() throws Exception
        {
            BookingRequest request = BookingRequest.builder()
                    .providerId(providerId)
                    .requestedDate(LocalDate.now().plusDays(1))
                    .requestedTime(LocalTime.of(10, 0))
                    .problemDescription("Test problem")
                    .build();

            when(bookingService.requestBooking(eq(consumerId), any(BookingRequest.class)))
                    .thenReturn(bookingResponse);

            mockMvc.perform(post("/api/bookings/{consumerId}/request-booking", consumerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(bookingId))
                    .andExpect(jsonPath("$.consumer.id").value(consumerId))
                    .andExpect(jsonPath("$.provider.id").value(providerId))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @WithMockUser(roles = "CONSUMER")
        @DisplayName("Request Booking - Invalid Request Data (400)")
        void requestBookingBadRequest() throws Exception
        {
            BookingRequest invalidRequest = BookingRequest.builder().build(); // missing required fields

            mockMvc.perform(post("/api/bookings/{consumerId}/request-booking", consumerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Provider Side Endpoints")
    class ProviderSideTest
    {
        @Test
        @WithMockUser(roles = "PROVIDER")
        @DisplayName("Accept Booking - ACCEPTED")
        void acceptBookingAccepted() throws Exception
        {
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(bookingId)
                    .estimatedDuration(60L)
                    .overrideWorkingHours(false)
                    .build();

            when(bookingService.acceptBooking(eq(providerId), any(AcceptBookingRequest.class)))
                    .thenReturn(acceptSuccessResponse);

            mockMvc.perform(post("/api/bookings/{providerId}/accept-booking", providerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.booking.id").value(bookingId));
        }

        @Test
        @WithMockUser(roles = "PROVIDER")
        @DisplayName("Accept Booking - CONFLICT (409)")
        void acceptBookingConflict() throws Exception
        {
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(bookingId)
                    .estimatedDuration(60L)
                    .overrideWorkingHours(false)
                    .build();

            when(bookingService.acceptBooking(eq(providerId), any(AcceptBookingRequest.class)))
                    .thenReturn(acceptConflictResponse);

            mockMvc.perform(post("/api/bookings/{providerId}/accept-booking", providerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("CONFLICT"))
                    .andExpect(jsonPath("$.conflictingBookings").isArray());
        }

        @Test
        @WithMockUser(roles = "PROVIDER")
        @DisplayName("Accept Booking - WARNING (200)")
        void acceptBookingWarning() throws Exception
        {
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(bookingId)
                    .estimatedDuration(60L)
                    .overrideWorkingHours(false)
                    .build();

            when(bookingService.acceptBooking(eq(providerId), any(AcceptBookingRequest.class)))
                    .thenReturn(acceptWarningResponse);

            mockMvc.perform(post("/api/bookings/{providerId}/accept-booking", providerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("WARNING"))
                    .andExpect(jsonPath("$.warningMessage").exists());
        }

        @Test
        @WithMockUser(roles = "PROVIDER")
        @DisplayName("Decline Booking - Success")
        void declineBookingSuccess() throws Exception
        {
            when(bookingService.declineBooking(providerId, bookingId))
                    .thenReturn(bookingResponse);

            mockMvc.perform(post("/api/bookings/{providerId}/decline-booking/{bookingId}", providerId, bookingId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(bookingId))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }
    }

    @Nested
    @DisplayName("User Side Endpoints")
    class UserSideTest
    {
        @Test
        @WithMockUser(roles = "CONSUMER")
        @DisplayName("Cancel Booking - Success (Consumer)")
        void cancelBookingByConsumerSuccess() throws Exception
        {
            CancelBookingRequest request = CancelBookingRequest.builder()
                    .bookingId(bookingId)
                    .cancellationReason("Personal reasons")
                    .build();

            when(bookingService.cancelBooking(eq(consumerId), any(CancelBookingRequest.class)))
                    .thenReturn(bookingResponse);

            mockMvc.perform(post("/api/bookings/{userId}/cancel-booking", consumerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(bookingId));
        }

        @Test
        @WithMockUser(roles = "PROVIDER")
        @DisplayName("Cancel Booking - Success (Provider)")
        void cancelBookingByProviderSuccess() throws Exception
        {
            CancelBookingRequest request = CancelBookingRequest.builder()
                    .bookingId(bookingId)
                    .cancellationReason("Provider unavailable")
                    .build();

            when(bookingService.cancelBooking(eq(providerId), any(CancelBookingRequest.class)))
                    .thenReturn(bookingResponse);

            mockMvc.perform(post("/api/bookings/{userId}/cancel-booking", providerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(bookingId));
        }

        @Test
        @WithMockUser(roles = "CONSUMER")
        @DisplayName("Get Pending Bookings - Success")
        void getBookingsByStatusSuccess() throws Exception
        {
            Pageable pageable = PageRequest.of(0, 10);
            Page<BookingResponse> page = new PageImpl<>(List.of(bookingResponse), pageable, 1);

            when(bookingService.getBookingsByStatus(eq(consumerId), eq(BookingStatus.PENDING), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/bookings/{userId}", consumerId)
                            .param("status", "PENDING")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(bookingId))
                    .andExpect(jsonPath("$.content[0].consumer.id").value(consumerId))
                    .andExpect(jsonPath("$.content[0].provider.id").value(providerId))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @WithMockUser(roles = "CONSUMER")
        @DisplayName("Get Invalid Status Bookings - Invalid status (500)")
        void getBookingsByStatusInvalidStatus() throws Exception
        {
            mockMvc.perform(get("/api/bookings/{userId}", consumerId)
                            .param("status", "INVALID_STATUS"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @WithMockUser(roles = "CONSUMER")
        @DisplayName("Get Upcoming Bookings - Success")
        void getUpcomingBookingsSuccess() throws Exception
        {
            List<BookingResponse> list = List.of(bookingResponse);

            when(bookingService.getUpcomingBookings(consumerId))
                    .thenReturn(list);

            mockMvc.perform(get("/api/bookings/{userId}/upcoming-bookings", consumerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(bookingId))
                    .andExpect(jsonPath("$[0].consumer.id").value(consumerId))
                    .andExpect(jsonPath("$[0].provider.id").value(providerId));
        }
    }
}
