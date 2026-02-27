package com.aykhedma.controller;

import com.aykhedma.BaseIntegrationTest;
import com.aykhedma.dto.request.LoginRequest;
import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Auth Controller Tests")
class AuthControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    // ─── Helpers ──────────────────────────────────────────
    private RegisterRequest validConsumerRequest() {
        return RegisterRequest.builder()
                .name("Test User")
                .email("test_" + UUID.randomUUID() + "@mail.com")
                .password("Password123")
                .phoneNumber("010" + String.format("%08d", (int) (Math.random() * 100_000_000)))
                .userType(UserType.CONSUMER)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // REGISTER
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /auth/register")
    class Register {

        @Test
        @DisplayName("Should return 200 when valid CONSUMER input")
        void register_validConsumer_returns200() throws Exception {
            RegisterRequest req = validConsumerRequest();

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Registered successfully")));
        }

        @Test
        @DisplayName("Should fail for PROVIDER without required entity fields (service type, location, etc.)")
        void register_providerWithoutRequiredFields_fails() throws Exception {
            RegisterRequest req = RegisterRequest.builder()
                    .name("Provider Test")
                    .email("provider_" + UUID.randomUUID() + "@mail.com")
                    .password("Password123")
                    .phoneNumber("011" + String.format("%08d", (int) (Math.random() * 100_000_000)))
                    .userType(UserType.PROVIDER)
                    .build();

            assertThatThrownBy(() -> mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req))))
                    .hasRootCauseInstanceOf(jakarta.validation.ConstraintViolationException.class);
        }

        @Test
        @DisplayName("Should return 400 when email is invalid")
        void register_invalidEmail_returns400() throws Exception {
            RegisterRequest req = RegisterRequest.builder()
                    .name("Test User")
                    .email("invalid-email")
                    .password("Password123")
                    .phoneNumber("01012345678")
                    .userType(UserType.CONSUMER)
                    .build();

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when password is too short")
        void register_shortPassword_returns400() throws Exception {
            RegisterRequest req = RegisterRequest.builder()
                    .name("Test User")
                    .email("valid@mail.com")
                    .password("123")
                    .phoneNumber("01012345678")
                    .userType(UserType.CONSUMER)
                    .build();

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when required fields are missing")
        void register_missingFields_returns400() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setName("Test User");

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when phone number format is invalid")
        void register_invalidPhone_returns400() throws Exception {
            RegisterRequest req = RegisterRequest.builder()
                    .name("Test User")
                    .email("test@mail.com")
                    .password("Password123")
                    .phoneNumber("123456")
                    .userType(UserType.CONSUMER)
                    .build();

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════
    // LOGIN
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("Should return 200 + AuthResponse when valid credentials")
        void login_validCredentials_returns200WithTokens() throws Exception {
            RegisterRequest regReq = validConsumerRequest();
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(regReq)))
                    .andExpect(status().isOk());

            LoginRequest loginReq = LoginRequest.builder()
                    .emailOrPhone(regReq.getEmail())
                    .password("Password123")
                    .build();

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.email").value(regReq.getEmail()));
        }

        @Test
        @DisplayName("Should throw when password is wrong")
        void login_wrongPassword_throws() throws Exception {
            RegisterRequest regReq = validConsumerRequest();
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(regReq)))
                    .andExpect(status().isOk());

            LoginRequest loginReq = LoginRequest.builder()
                    .emailOrPhone(regReq.getEmail())
                    .password("WrongPassword1")
                    .build();

            assertThatThrownBy(() -> mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginReq)))).hasRootCauseMessage("Invalid credentials");
        }

        @Test
        @DisplayName("Should throw when user does not exist")
        void login_nonExistentUser_throws() throws Exception {
            LoginRequest loginReq = LoginRequest.builder()
                    .emailOrPhone("nobody@mail.com")
                    .password("Password123")
                    .build();

            assertThatThrownBy(() -> mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginReq)))).hasRootCauseMessage("User not found");
        }

        @Test
        @DisplayName("Should return 400 when login fields are blank")
        void login_blankFields_returns400() throws Exception {
            LoginRequest loginReq = new LoginRequest();

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ═══════════════════════════════════════════════════════
    // PROTECTED ENDPOINTS
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("Protected Endpoints")
    class ProtectedEndpoints {

        @Test
        @DisplayName("Should return 403 when no token provided")
        void protectedEndpoint_noToken_returns403() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isForbidden());
        }
    }
}
