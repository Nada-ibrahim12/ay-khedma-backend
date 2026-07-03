package com.aykhedma.controller;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.dto.request.AcceptBookingRequest;
import com.aykhedma.dto.request.BookingRequest;
import com.aykhedma.dto.request.CancelBookingRequest;
import com.aykhedma.dto.response.AcceptBookingResponse;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.ConsumerSummaryResponse;
import com.aykhedma.dto.response.MonthlyBookingStatsResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.dto.response.WeeklyBookingStatsResponse;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("BookingController Tests")
class BookingControllerTest
{
    private record UserPrincipal(Long id) { }

    private record PayloadPrincipal(UserPrincipal user)
    {
        private PayloadPrincipal(Long userId)
        {
            this(new UserPrincipal(userId));
        }
    }

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
    private WeeklyBookingStatsResponse weeklyStatsResponse;
    private MonthlyBookingStatsResponse monthlyStatsResponse;
    private final Long consumerId = 2L;
    private final Long providerId = 1L;
    private final Long bookingId = 10L;

    private RequestPostProcessor authenticatedConsumer()
    {
        var principal = new BookingControllerTest.PayloadPrincipal(consumerId);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CONSUMER"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    private RequestPostProcessor authenticatedProvider()
    {
        var principal = new BookingControllerTest.PayloadPrincipal(providerId);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_PROVIDER"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

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

        weeklyStatsResponse = WeeklyBookingStatsResponse.builder()
                .acceptedAndCompletedBookings(8)
                .cancelledBookings(2)
                .build();

        monthlyStatsResponse = MonthlyBookingStatsResponse.builder()
                .months(List.of("January", "February", "March"))
                .completedBookings(List.of(5, 3, 7))
                .cancelledBookings(List.of(1, 2, 0))
                .build();
    }

    @Nested
    @DisplayName("Consumer Side Endpoints")
    class ConsumerSideTest
    {
        @Test
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

            mockMvc.perform(post("/api/bookings/request-booking")
                            .with(authenticatedConsumer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(bookingId))
                    .andExpect(jsonPath("$.consumer.id").value(consumerId))
                    .andExpect(jsonPath("$.provider.id").value(providerId))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Request Booking - Invalid Request Data (400)")
        void requestBookingBadRequest() throws Exception
        {
            BookingRequest invalidRequest = BookingRequest.builder().build();

            mockMvc.perform(post("/api/bookings/request-booking")
                            .with(authenticatedConsumer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Delete Booking - Success")
        void deleteBookingSuccess() throws Exception
        {
            when(bookingService.deleteBooking(eq(consumerId), eq(bookingId)))
                    .thenReturn(bookingResponse);

            mockMvc.perform(put("/api/bookings/delete-booking/{bookingId}", bookingId)
                            .with(authenticatedConsumer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(bookingId))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Complete Booking - Success")
        void completeBookingSuccess() throws Exception
        {
            bookingResponse.setStatus(BookingStatus.COMPLETED);

            when(bookingService.completeBooking(eq(consumerId), eq(bookingId)))
                    .thenReturn(bookingResponse);

            mockMvc.perform(put("/api/bookings/complete-booking/{bookingId}", bookingId)
                            .with(authenticatedConsumer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(bookingId))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
    }

    @Nested
    @DisplayName("Provider Side Endpoints")
    class ProviderSideTest
    {
        @Test
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

            mockMvc.perform(put("/api/bookings/accept-booking")
                            .with(authenticatedProvider())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.booking.id").value(bookingId));
        }

        @Test
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

            mockMvc.perform(put("/api/bookings/accept-booking")
                            .with(authenticatedProvider())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("CONFLICT"))
                    .andExpect(jsonPath("$.conflictingBookings").isArray());
        }

        @Test
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

            mockMvc.perform(put("/api/bookings/accept-booking")
                            .with(authenticatedProvider())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("WARNING"))
                    .andExpect(jsonPath("$.warningMessage").exists());
        }

        @Test
        @DisplayName("Decline Booking - Success")
        void declineBookingSuccess() throws Exception
        {
            when(bookingService.declineBooking(eq(providerId), eq(bookingId)))
                    .thenReturn(bookingResponse);

            mockMvc.perform(put("/api/bookings/decline-booking/{bookingId}", bookingId)
                            .with(authenticatedProvider()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(bookingId))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Get Weekly Booking Stats - Success")
        void getWeeklyBookingStatsSuccess() throws Exception
        {
            when(bookingService.getWeeklyBookingStats(eq(providerId)))
                    .thenReturn(weeklyStatsResponse);

            mockMvc.perform(get("/api/bookings/weekly-booking-stats")
                            .with(authenticatedProvider()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.acceptedAndCompletedBookings").value(8))
                    .andExpect(jsonPath("$.cancelledBookings").value(2));
        }

        @Test
        @DisplayName("Get Monthly Booking Stats - Success")
        void getMonthlyBookingStatsSuccess() throws Exception
        {
            when(bookingService.getMonthlyBookingStats(eq(providerId)))
                    .thenReturn(monthlyStatsResponse);

            mockMvc.perform(get("/api/bookings/monthly-booking-stats")
                            .with(authenticatedProvider()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.months[0]").value("January"))
                    .andExpect(jsonPath("$.months[1]").value("February"))
                    .andExpect(jsonPath("$.months[2]").value("March"))
                    .andExpect(jsonPath("$.completedBookings[0]").value(5))
                    .andExpect(jsonPath("$.completedBookings[1]").value(3))
                    .andExpect(jsonPath("$.completedBookings[2]").value(7))
                    .andExpect(jsonPath("$.cancelledBookings[0]").value(1))
                    .andExpect(jsonPath("$.cancelledBookings[1]").value(2))
                    .andExpect(jsonPath("$.cancelledBookings[2]").value(0));
        }
    }

    @Nested
    @DisplayName("User Side Endpoints")
    class UserSideTest
    {
        @Test
        @DisplayName("Cancel Booking - Success (Consumer)")
        void cancelBookingByConsumerSuccess() throws Exception
        {
            CancelBookingRequest request = CancelBookingRequest.builder()
                    .bookingId(bookingId)
                    .cancellationReason("Personal reasons")
                    .build();

            when(bookingService.cancelBooking(eq(consumerId), any(CancelBookingRequest.class)))
                    .thenReturn(bookingResponse);

            mockMvc.perform(put("/api/bookings/cancel-booking")
                            .with(authenticatedConsumer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(bookingId));
        }

        @Test
        @DisplayName("Cancel Booking - Success (Provider)")
        void cancelBookingByProviderSuccess() throws Exception
        {
            CancelBookingRequest request = CancelBookingRequest.builder()
                    .bookingId(bookingId)
                    .cancellationReason("Provider unavailable")
                    .build();

            when(bookingService.cancelBooking(eq(providerId), any(CancelBookingRequest.class)))
                    .thenReturn(bookingResponse);

            mockMvc.perform(put("/api/bookings/cancel-booking")
                            .with(authenticatedProvider())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(bookingId));
        }

        @Test
        @DisplayName("Get Pending Bookings - Success")
        void getBookingsByStatusSuccess() throws Exception
        {
            Pageable pageable = PageRequest.of(0, 10);
            Page<BookingResponse> page = new PageImpl<>(List.of(bookingResponse), pageable, 1);

            when(bookingService.getBookingsByStatus(eq(consumerId), eq(BookingStatus.PENDING), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/bookings/get-bookings")
                            .with(authenticatedConsumer())
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
        @DisplayName("Get Invalid Status Bookings - Invalid status (500)")
        void getBookingsByStatusInvalidStatus() throws Exception
        {
            mockMvc.perform(get("/api/bookings/get-bookings")
                            .with(authenticatedProvider())
                            .param("status", "INVALID_STATUS"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Get Upcoming Bookings - Success")
        void getUpcomingBookingsSuccess() throws Exception
        {
            List<BookingResponse> list = List.of(bookingResponse);

            when(bookingService.getUpcomingBookings(providerId))
                    .thenReturn(list);

            mockMvc.perform(get("/api/bookings/upcoming-bookings")
                            .with(authenticatedProvider()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(bookingId))
                    .andExpect(jsonPath("$[0].consumer.id").value(consumerId))
                    .andExpect(jsonPath("$[0].provider.id").value(providerId));
        }
    }
}
