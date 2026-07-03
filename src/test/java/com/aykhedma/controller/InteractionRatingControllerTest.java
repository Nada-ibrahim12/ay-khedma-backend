package com.aykhedma.controller;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.dto.request.InteractionRatingRequest;
import com.aykhedma.dto.response.InteractionRatingResponse;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.service.InteractionRatingService;
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
@DisplayName("InteractionRatingController Tests")
class InteractionRatingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InteractionRatingService interactionRatingService;

    private InteractionRatingRequest validRequest;
    private InteractionRatingResponse mockResponse;
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

    @BeforeEach
    void setUp() {
        validRequest = InteractionRatingRequest.builder()
                .providerId(PROVIDER_ID)
                .rating(4)
                .comment("Excellent profile interaction")
                .build();

        mockResponse = InteractionRatingResponse.builder()
                .id(100L)
                .consumerId(CONSUMER_ID)
                .consumerName("Test User")
                .rating(4.0)
                .comment("Excellent profile interaction")
                .build();
    }

    @Test
    @DisplayName("POST /api/ratings/interaction - Should submit rating successfully")
    void submitRating_ShouldReturnOk() throws Exception {
        when(interactionRatingService.submitRating(eq(CONSUMER_ID), any(InteractionRatingRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/ratings/interaction")
                        .with(authenticatedUser(CONSUMER_ID, UserType.CONSUMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.rating").value(4.0))
                .andExpect(jsonPath("$.comment").value("Excellent profile interaction"));
    }

    @Test
    @DisplayName("GET /api/ratings/interaction/provider/{providerId} - Should return list of provider ratings")
    void getProviderRatings_ShouldReturnList() throws Exception {
        when(interactionRatingService.getProviderRatings(PROVIDER_ID))
                .thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/ratings/interaction/provider/{providerId}", PROVIDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100L))
                .andExpect(jsonPath("$[0].rating").value(4.0));
    }

    @Test
    @DisplayName("GET /api/ratings/interaction/me - Should return my ratings as provider")
    void getMyRatings_ShouldReturnList() throws Exception {
        when(interactionRatingService.getProviderRatings(PROVIDER_ID))
                .thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/ratings/interaction/me")
                        .with(authenticatedUser(PROVIDER_ID, UserType.PROVIDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100L))
                .andExpect(jsonPath("$[0].rating").value(4.0));
    }
}
