package com.aykhedma.controller;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.dto.request.RatingRequest;
import com.aykhedma.dto.request.EmergencyRatingRequest;
import com.aykhedma.dto.request.ProviderEmergencyRatingRequest;
import com.aykhedma.dto.request.ProviderRatingRequest;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.dto.response.ConsumerReviewResponse;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.service.BookingService;
import com.aykhedma.service.EmergencyRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock-security")
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
@DisplayName("RatingController Tests")
class RatingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private EmergencyRequestService emergencyRequestService;

    private final Long CONSUMER_ID = 1L;
    private final Long PROVIDER_ID = 2L;

    private RequestPostProcessor authenticatedUser(Long userId, UserType role) {
        User user = User.builder()
                .id(userId)
                .name("Test User")
                .email("test@example.com")
                .phoneNumber("01011112222")
                .password("password")
                .role(role)
                .enabled(true)
                .build();
        CustomUserDetails principal = new CustomUserDetails(user);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    @Test
    @DisplayName("GET /api/ratings/questions - Should return list of questions in English by default")
    void getEvaluationQuestions_ShouldReturnDefaultQuestions() throws Exception {
        mockMvc.perform(get("/api/ratings/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dimension").value("Punctuality"))
                .andExpect(jsonPath("$[1].dimension").value("Commitment"))
                .andExpect(jsonPath("$[2].dimension").value("Quality of Work"));
    }

    @Test
    @DisplayName("GET /api/ratings/questions - Should return list of questions in Arabic when specified")
    void getEvaluationQuestions_ShouldReturnArabicQuestions() throws Exception {
        mockMvc.perform(get("/api/ratings/questions")
                        .header("Accept-Language", "ar-EG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dimension").value("الالتزام بالوقت"))
                .andExpect(jsonPath("$[1].dimension").value("الالتزام والجدية"))
                .andExpect(jsonPath("$[2].dimension").value("جودة العمل"));
    }

    @Test
    @DisplayName("POST /api/ratings - Should submit consumer rating successfully")
    void submitRating_ShouldReturnOk() throws Exception {
        RatingRequest request = RatingRequest.builder()
                .bookingId(10L)
                .punctualityRating(5)
                .commitmentRating(5)
                .qualityOfWorkRating(5)
                .review("Amazing service!")
                .build();

        BookingResponse response = BookingResponse.builder()
                .id(10L)
                .consumerRating(5.0)
                .consumerReview("Amazing service!")
                .build();

        when(bookingService.submitRating(eq(CONSUMER_ID), any(RatingRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/ratings")
                        .with(authenticatedUser(CONSUMER_ID, UserType.CONSUMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.consumerRating").value(5.0))
                .andExpect(jsonPath("$.consumerReview").value("Amazing service!"));
    }

    @Test
    @DisplayName("POST /api/ratings/consumer - Should submit provider rating successfully")
    void submitConsumerRating_ShouldReturnOk() throws Exception {
        ProviderRatingRequest request = ProviderRatingRequest.builder()
                .bookingId(10L)
                .rating(4)
                .review("Polite customer")
                .build();

        BookingResponse response = BookingResponse.builder()
                .id(10L)
                .providerRating(4.0)
                .providerReview("Polite customer")
                .build();

        when(bookingService.submitConsumerRating(eq(PROVIDER_ID), any(ProviderRatingRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/ratings/consumer")
                        .with(authenticatedUser(PROVIDER_ID, UserType.PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.providerRating").value(4.0))
                .andExpect(jsonPath("$.providerReview").value("Polite customer"));
    }

    @Test
    @DisplayName("GET /api/ratings/consumer/{consumerId} - Should get consumer reviews")
    void getConsumerReviews_ShouldReturnList() throws Exception {
        ConsumerReviewResponse response = ConsumerReviewResponse.builder()
                .id(10L)
                .providerId(PROVIDER_ID)
                .providerName("Provider One")
                .rating(4.0)
                .review("Great consumer")
                .build();

        when(bookingService.getConsumerReviews(CONSUMER_ID)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/ratings/consumer/{consumerId}", CONSUMER_ID)
                        .with(authenticatedUser(PROVIDER_ID, UserType.PROVIDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].review").value("Great consumer"));
    }

    @Test
    @DisplayName("POST /api/ratings/emergency - Should submit consumer emergency rating successfully")
    void submitEmergencyRating_ShouldReturnOk() throws Exception {
        EmergencyRatingRequest request = EmergencyRatingRequest.builder()
                .emergencyRequestId(20L)
                .punctualityRating(5)
                .commitmentRating(5)
                .qualityOfWorkRating(5)
                .review("Fast response")
                .build();

        EmergencyRequestResponse response = EmergencyRequestResponse.builder()
                .id(20L)
                .consumerRating(5.0)
                .consumerReview("Fast response")
                .build();

        when(emergencyRequestService.submitEmergencyRequestRating(eq(CONSUMER_ID), any(EmergencyRatingRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/ratings/emergency")
                        .with(authenticatedUser(CONSUMER_ID, UserType.CONSUMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20L))
                .andExpect(jsonPath("$.consumerRating").value(5.0));
    }

    @Test
    @DisplayName("POST /api/ratings/emergency/consumer - Should submit provider emergency rating successfully")
    void submitConsumerEmergencyRating_ShouldReturnOk() throws Exception {
        ProviderEmergencyRatingRequest request = ProviderEmergencyRatingRequest.builder()
                .emergencyRequestId(20L)
                .rating(4)
                .review("Collaborative consumer")
                .build();

        EmergencyRequestResponse response = EmergencyRequestResponse.builder()
                .id(20L)
                .providerRating(4.0)
                .providerReview("Collaborative consumer")
                .build();

        when(emergencyRequestService.submitConsumerEmergencyRequestRating(eq(PROVIDER_ID), any(ProviderEmergencyRatingRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/ratings/emergency/consumer")
                        .with(authenticatedUser(PROVIDER_ID, UserType.PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20L))
                .andExpect(jsonPath("$.providerRating").value(4.0));
    }
}
