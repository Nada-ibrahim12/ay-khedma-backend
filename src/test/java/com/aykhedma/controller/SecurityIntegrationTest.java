package com.aykhedma.controller;

import com.aykhedma.BaseIntegrationTest;
import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.security.JwtService;
import com.aykhedma.model.user.Admin;
import com.aykhedma.model.user.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration,org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
})
@DisplayName("Security Integration Tests")
class SecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    // ─── Helpers ──────────────────────────────────────────
    private Consumer createAndSaveConsumer() {
        Consumer consumer = Consumer.builder()
                .name("Security User")
                .email("sec_" + UUID.randomUUID() + "@mail.com")
                .phoneNumber("012" + String.format("%08d", (int) (Math.random() * 100_000_000)))
                .password(passwordEncoder.encode("Password123"))
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(0)
                .build();
        return (Consumer) userRepository.save(consumer);
    }

    // ═══════════════════════════════════════════════════════
    // PUBLIC ENDPOINTS
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("POST /auth/register should be accessible without token")
    void publicRegisterEndpoint_noAuth_returns200() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .name("Public Test")
                .email("pub_" + UUID.randomUUID() + "@mail.com")
                .password("Password123")
                .phoneNumber("010" + String.format("%08d", (int) (Math.random() * 100_000_000)))
                .userType(UserType.CONSUMER)
                .latitude(30.0444)
                .longitude(31.2357)
                .build();

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/login should be accessible without token (returns 404 User not found, not 401/403)")
    void publicLoginEndpoint_noAuth_notBlocked() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailOrPhone\":\"x@x.com\",\"password\":\"12345678\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    // ═══════════════════════════════════════════════════════
    // PROTECTED ENDPOINTS — NO TOKEN
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("GET /api/users should return 401 without token")
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════
    // PROTECTED ENDPOINTS — WITH VALID TOKEN
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("GET /api/users with valid CONSUMER JWT should not return 403")
    void protectedEndpoint_validConsumerToken_notForbidden() throws Exception {
        Consumer user = createAndSaveConsumer();
        String jwt = jwtService.generateToken(user);

        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + jwt))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    // ═══════════════════════════════════════════════════════
    // ROLE-BASED ACCESS
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("CONSUMER should get 403 on /admin/** endpoints")
    void adminEndpoint_withConsumerRole_returns403() throws Exception {
        Consumer user = createAndSaveConsumer();
        String jwt = jwtService.generateToken(user);

        mockMvc.perform(get("/admin/dashboard")
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CONSUMER should get 403 on /provider/** endpoints")
    void providerEndpoint_withConsumerRole_returns403() throws Exception {
        Consumer user = createAndSaveConsumer();
        String jwt = jwtService.generateToken(user);

        mockMvc.perform(get("/provider/profile")
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    private Admin createAndSaveAdmin() {
        Admin admin = Admin.builder()
                .name("Security Admin")
                .email("admin_" + UUID.randomUUID() + "@mail.com")
                .phoneNumber("011" + String.format("%08d", (int) (Math.random() * 100_000_000)))
                .password(passwordEncoder.encode("Password123"))
                .role(UserType.ADMIN)
                .enabled(true)
                .credentialsNonExpired(true)
                .build();
        return (Admin) userRepository.save(admin);
    }

    @Test
    @DisplayName("GET /admin/users with ADMIN JWT should not return the calling admin themselves")
    void adminUsersEndpoint_excludesCallingAdmin() throws Exception {
        Admin admin = createAndSaveAdmin();
        Consumer consumer = createAndSaveConsumer();
        String jwt = jwtService.generateToken(admin);

        mockMvc.perform(get("/admin/users")
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email == '" + consumer.getEmail() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.email == '" + admin.getEmail() + "')]").doesNotExist());
    }
}
