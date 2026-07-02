package com.aykhedma.controller;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.EmergencyRequestRequest;
import com.aykhedma.dto.request.PriceRecommendationRequest;
import com.aykhedma.dto.request.ProviderResponseRequest;
import com.aykhedma.dto.request.UpdateEmergencyRequestPriceRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.model.emergency.EmergencyRequestStatus;
import com.aykhedma.security.CustomUserDetailsService;
import com.aykhedma.security.JwtService;
import com.aykhedma.service.EmergencyRequestService;

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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EmergencyRequestController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("EmergencyRequestController Tests")
class EmergencyRequestControllerTest
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
    private EmergencyRequestService emergencyRequestService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private EmergencyRequestResponse emergencyRequestResponse;
    private ProviderResponseResponse providerResponseResponse;
    private PriceRecommendationResponse priceRecommendationResponse;
    private final Long consumerId = 2L;
    private final Long providerId = 1L;
    private final Long emergencyRequestId = 100L;
    private final Long providerResponseId = 200L;

    private RequestPostProcessor authenticatedConsumer()
    {
        var principal = new EmergencyRequestControllerTest.PayloadPrincipal(consumerId);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CONSUMER"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    private RequestPostProcessor authenticatedProvider()
    {
        var principal = new EmergencyRequestControllerTest.PayloadPrincipal(providerId);
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

        LocationResponse location = LocationResponse.builder()
                .latitude(30.0)
                .longitude(31.0)
                .build();

        providerResponseResponse = ProviderResponseResponse.builder()
                .id(providerResponseId)
                .provider(providerSummary)
                .estimatedArrivalTime(15)
                .distance(3.5)
                .proposedPrice(150.0)
                .notes("On my way")
                .responseTime(LocalDateTime.now())
                .build();

        emergencyRequestResponse = EmergencyRequestResponse.builder()
                .id(emergencyRequestId)
                .consumer(consumerSummary)
                .serviceType("Plumbing")
                .location(location)
                .price(120.0)
                .description("Leaky pipe")
                .status(EmergencyRequestStatus.BROADCASTING)
                .selectedProvider(null)
                .createdAt(LocalDateTime.now())
                .providerResponses(List.of(providerResponseResponse))
                .build();

        priceRecommendationResponse = PriceRecommendationResponse.builder()
                .price(130.0)
                .build();
    }

    @Nested
    @DisplayName("Consumer Side Endpoints")
    class ConsumerSideTest
    {
        @Test
        @DisplayName("Get Current Emergency Request - Success")
        void getCurrentEmergencyRequestSuccess() throws Exception
        {
            when(emergencyRequestService.getCurrentEmergencyRequest(eq(consumerId)))
                    .thenReturn(emergencyRequestResponse);

            mockMvc.perform(get("/api/emergency-requests/get-current-emergency-request")
                            .with(authenticatedConsumer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(emergencyRequestId))
                    .andExpect(jsonPath("$.consumer.id").value(consumerId))
                    .andExpect(jsonPath("$.status").value("BROADCASTING"))
                    .andExpect(jsonPath("$.providerResponses[0].id").value(providerResponseId));
        }

        @Test
        @DisplayName("Get Price Recommendation - Success (200)")
        void getPriceRecommendationSuccess() throws Exception
        {
            PriceRecommendationRequest request = PriceRecommendationRequest.builder()
                    .serviceTypeId(5L)
                    .location(LocationDTO.builder().latitude(30.0).longitude(31.0).build())
                    .build();

            when(emergencyRequestService.getEmergencyRequestPriceRecommendation(eq(consumerId), any(PriceRecommendationRequest.class)))
                    .thenReturn(priceRecommendationResponse);

            mockMvc.perform(get("/api/emergency-requests/get-emergency-request-price-recommendation")
                            .with(authenticatedConsumer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(130.0));
        }

        @Test
        @DisplayName("Get Price Recommendation - No Content (204)")
        void getPriceRecommendationNoContent() throws Exception
        {
            PriceRecommendationRequest request = PriceRecommendationRequest.builder()
                    .serviceTypeId(5L)
                    .location(LocationDTO.builder().latitude(30.0).longitude(31.0).build())
                    .build();

            PriceRecommendationResponse emptyResponse = PriceRecommendationResponse.builder().price(null).build();
            when(emergencyRequestService.getEmergencyRequestPriceRecommendation(eq(consumerId), any(PriceRecommendationRequest.class)))
                    .thenReturn(emptyResponse);

            mockMvc.perform(get("/api/emergency-requests/get-emergency-request-price-recommendation")
                            .with(authenticatedConsumer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Request Emergency Request - Success")
        void requestEmergencyRequestSuccess() throws Exception
        {
            EmergencyRequestRequest request = EmergencyRequestRequest.builder()
                    .serviceTypeId(5L)
                    .location(LocationDTO.builder().latitude(30.0).longitude(31.0).build())
                    .price(120.0)
                    .description("Leaky pipe")
                    .build();

            when(emergencyRequestService.requestEmergencyRequest(eq(consumerId), any(EmergencyRequestRequest.class)))
                    .thenReturn(emergencyRequestResponse);

            mockMvc.perform(post("/api/emergency-requests/request-emergency-request")
                            .with(authenticatedConsumer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(emergencyRequestId))
                    .andExpect(jsonPath("$.consumer.id").value(consumerId))
                    .andExpect(jsonPath("$.status").value("BROADCASTING"));
        }

        @Test
        @DisplayName("Request Emergency Request - Invalid Data (400)")
        void requestEmergencyRequestBadRequest() throws Exception
        {
            EmergencyRequestRequest invalidRequest = EmergencyRequestRequest.builder().build();

            mockMvc.perform(post("/api/emergency-requests/request-emergency-request")
                            .with(authenticatedConsumer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Accept Provider Response - Success")
        void acceptProviderResponseSuccess() throws Exception
        {
            when(emergencyRequestService.acceptProviderResponse(eq(consumerId), eq(providerResponseId)))
                    .thenReturn(providerResponseResponse);

            mockMvc.perform(put("/api/emergency-requests/accept-provider-response/{providerResponseId}", providerResponseId)
                            .with(authenticatedConsumer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(providerResponseId))
                    .andExpect(jsonPath("$.provider.id").value(providerId));
        }

        @Test
        @DisplayName("Decline Provider Response - Success")
        void declineProviderResponseSuccess() throws Exception
        {
            when(emergencyRequestService.declineProviderResponse(eq(consumerId), eq(providerResponseId)))
                    .thenReturn(providerResponseResponse);

            mockMvc.perform(put("/api/emergency-requests/decline-provider-response/{providerResponseId}", providerResponseId)
                            .with(authenticatedConsumer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(providerResponseId));
        }

        @Test
        @DisplayName("Update Emergency Request Price - Success")
        void updateEmergencyRequestPriceSuccess() throws Exception
        {
            UpdateEmergencyRequestPriceRequest request = UpdateEmergencyRequestPriceRequest.builder()
                    .emergencyRequestId(emergencyRequestId)
                    .updatedPrice(150.0)
                    .build();

            emergencyRequestResponse.setPrice(150.0);

            when(emergencyRequestService.updateEmergencyRequestPrice(eq(consumerId), any(UpdateEmergencyRequestPriceRequest.class)))
                    .thenReturn(emergencyRequestResponse);

            mockMvc.perform(put("/api/emergency-requests/update-emergency-request-price")
                            .with(authenticatedConsumer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(emergencyRequestId))
                    .andExpect(jsonPath("$.price").value(150.0));
        }

        @Test
        @DisplayName("Complete Emergency Request - Success")
        void completeEmergencyRequestSuccess() throws Exception
        {
            emergencyRequestResponse.setStatus(EmergencyRequestStatus.COMPLETED);

            when(emergencyRequestService.completeEmergencyRequest(eq(consumerId), eq(emergencyRequestId)))
                    .thenReturn(emergencyRequestResponse);

            mockMvc.perform(put("/api/emergency-requests/complete-emergency-request/{emergencyRequestId}", emergencyRequestId)
                            .with(authenticatedConsumer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(emergencyRequestId))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("Cancel Emergency Request - Success")
        void cancelEmergencyRequestSuccess() throws Exception
        {
            emergencyRequestResponse.setStatus(EmergencyRequestStatus.CANCELLED);

            when(emergencyRequestService.cancelEmergencyRequest(eq(consumerId), eq(emergencyRequestId)))
                    .thenReturn(emergencyRequestResponse);

            mockMvc.perform(put("/api/emergency-requests/cancel-emergency-request/{emergencyRequestId}", emergencyRequestId)
                            .with(authenticatedConsumer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(emergencyRequestId))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }
    }

    @Nested
    @DisplayName("Provider Side Endpoints")
    class ProviderSideTest
    {
        @Test
        @DisplayName("Get Pending Emergency Requests - Success")
        void getPendingEmergencyRequestsSuccess() throws Exception
        {
            List<ProviderResponseResponse> list = List.of(providerResponseResponse);

            when(emergencyRequestService.getPendingEmergencyRequests(eq(providerId)))
                    .thenReturn(list);

            mockMvc.perform(get("/api/emergency-requests/get-pending-emergency-requests")
                            .with(authenticatedProvider()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(providerResponseId))
                    .andExpect(jsonPath("$[0].provider.id").value(providerId));
        }

        @Test
        @DisplayName("Accept Emergency Request - Success")
        void acceptEmergencyRequestSuccess() throws Exception
        {
            ProviderResponseRequest request = ProviderResponseRequest.builder()
                    .providerResponseId(providerResponseId)
                    .location(LocationDTO.builder().latitude(30.0).longitude(31.0).build())
                    .proposedPrice(160.0)
                    .notes("Will arrive soon")
                    .build();

            when(emergencyRequestService.acceptEmergencyRequest(eq(providerId), any(ProviderResponseRequest.class)))
                    .thenReturn(providerResponseResponse);

            mockMvc.perform(put("/api/emergency-requests/accept-emergency-request")
                            .with(authenticatedProvider())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(providerResponseId))
                    .andExpect(jsonPath("$.provider.id").value(providerId));
        }

        @Test
        @DisplayName("Decline Emergency Request - Success")
        void declineEmergencyRequestSuccess() throws Exception
        {
            when(emergencyRequestService.declineEmergencyRequest(eq(providerId), eq(providerResponseId)))
                    .thenReturn(providerResponseResponse);

            mockMvc.perform(put("/api/emergency-requests/decline-emergency-request/{providerResponseId}", providerResponseId)
                            .with(authenticatedProvider()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(providerResponseId));
        }
    }

    @Nested
    @DisplayName("User Side Endpoints")
    class UserSideTest
    {
        @Test
        @DisplayName("Get Accepted Emergency Requests - Success (Consumer)")
        void getAcceptedEmergencyRequestsConsumerSuccess() throws Exception
        {
            List<EmergencyRequestResponse> list = List.of(emergencyRequestResponse);

            when(emergencyRequestService.getAcceptedEmergencyRequests(eq(consumerId)))
                    .thenReturn(list);

            mockMvc.perform(get("/api/emergency-requests/get-accepted-emergency-requests")
                            .with(authenticatedConsumer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(emergencyRequestId))
                    .andExpect(jsonPath("$[0].consumer.id").value(consumerId));
        }

        @Test
        @DisplayName("Get Accepted Emergency Requests - Success (Provider)")
        void getAcceptedEmergencyRequestsProviderSuccess() throws Exception
        {
            List<EmergencyRequestResponse> list = List.of(emergencyRequestResponse);

            when(emergencyRequestService.getAcceptedEmergencyRequests(eq(providerId)))
                    .thenReturn(list);

            mockMvc.perform(get("/api/emergency-requests/get-accepted-emergency-requests")
                            .with(authenticatedProvider()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(emergencyRequestId));
        }

        @Test
        @DisplayName("Get Emergency Requests History - Success (Consumer)")
        void getEmergencyRequestsHistoryConsumerSuccess() throws Exception
        {
            List<EmergencyRequestResponse> list = List.of(emergencyRequestResponse);

            when(emergencyRequestService.getEmergencyRequestsHistory(eq(consumerId)))
                    .thenReturn(list);

            mockMvc.perform(get("/api/emergency-requests/get-emergency-requests-history")
                            .with(authenticatedConsumer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(emergencyRequestId));
        }

        @Test
        @DisplayName("Get Emergency Requests History - Success (Provider)")
        void getEmergencyRequestsHistoryProviderSuccess() throws Exception
        {
            List<EmergencyRequestResponse> list = List.of(emergencyRequestResponse);

            when(emergencyRequestService.getEmergencyRequestsHistory(eq(providerId)))
                    .thenReturn(list);

            mockMvc.perform(get("/api/emergency-requests/get-emergency-requests-history")
                            .with(authenticatedProvider()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(emergencyRequestId));
        }
    }
}
