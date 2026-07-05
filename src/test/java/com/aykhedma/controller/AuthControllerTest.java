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
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.repository.ServiceCategoryRepository;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.booking.Schedule;
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
        @Autowired
        private ProviderRepository providerRepository;
        @Autowired
        private ServiceTypeRepository serviceTypeRepository;
        @Autowired
        private ServiceCategoryRepository serviceCategoryRepository;
        @Autowired
        private PasswordEncoder passwordEncoder;

        // ─── Helpers ──────────────────────────────────────────
        private RegisterRequest validConsumerRequest() {
                return RegisterRequest.builder()
                                .name("Test User")
                                .email("test_" + UUID.randomUUID() + "@mail.com")
                                .password("Password123")
                                .phoneNumber("010" + String.format("%08d", (int) (Math.random() * 100_000_000)))
                                .userType(UserType.CONSUMER)
                                .preferredLanguage("en")
                                .latitude(30.0444) 
                                .longitude(31.2357) 
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
                                        .latitude(30.0444) 
                                        .longitude(31.2357) 
                                        .build();

                        mockMvc.perform(post("/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                                        .andExpect(status().isBadRequest());
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
                                        .latitude(30.0444) 
                                        .longitude(31.2357) 
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
                                        .latitude(30.0444) 
                                        .longitude(31.2357) 
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
                        req.setLatitude(30.0444); 
                        req.setLongitude(31.2357); 

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
                                        .latitude(30.0444) 
                                        .longitude(31.2357) 
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

                private String registerAndEnableUser(RegisterRequest req) throws Exception {
                        mockMvc.perform(post("/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                                        .andExpect(status().isOk());

                        User user = userRepository.findByEmail(req.getEmail())
                                        .orElseThrow(() -> new RuntimeException("User not found"));
                        user.setEnabled(true);
                        userRepository.save(user);

                        return req.getEmail();
                }

                @Test
                @DisplayName("Should return 200 + AuthResponse when valid credentials")
                void login_validCredentials_returns200WithTokens() throws Exception {
                        RegisterRequest regReq = validConsumerRequest();
                        String email = registerAndEnableUser(regReq);

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
                                        .andExpect(jsonPath("$.email").value(regReq.getEmail()))
                                        .andExpect(jsonPath("$.preferredLanguage").value("en"));
                }

                @Test
                @DisplayName("Should return 401 when password is wrong")
                void login_wrongPassword_throws() throws Exception {
                        RegisterRequest regReq = validConsumerRequest();
                        String email = registerAndEnableUser(regReq);

                        LoginRequest loginReq = LoginRequest.builder()
                                        .emailOrPhone(regReq.getEmail())
                                        .password("WrongPassword1")
                                        .build();

                        mockMvc.perform(post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginReq)))
                                        .andExpect(status().isUnauthorized());
                }

                @Test
                @DisplayName("Should return 404 when user does not exist")
                void login_nonExistentUser_throws() throws Exception {
                        LoginRequest loginReq = LoginRequest.builder()
                                        .emailOrPhone("nobody@mail.com")
                                        .password("Password123")
                                        .build();

                        mockMvc.perform(post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginReq)))
                                        .andExpect(status().isNotFound());
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

                @Test
                @DisplayName("Should block login for provider pending verification")
                void login_pendingProvider_returns401() throws Exception {
                        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                                        .name("Category " + UUID.randomUUID().toString().substring(0, 8))
                                        .build());

                        ServiceType serviceType = serviceTypeRepository.save(ServiceType.builder()
                                        .name("High Risk " + UUID.randomUUID().toString().substring(0, 8))
                                        .riskLevel(RiskLevel.HIGH)
                                        .category(category)
                                        .defaultPriceType(PriceType.HOUR)
                                        .basePrice(200.0)
                                        .build());

                        Location location1 = Location.builder()
                                        .latitude(30.0444)
                                        .longitude(31.2357)
                                        .address("123 Test Street")
                                        .area("Maadi")
                                        .city("Cairo")
                                        .build();

                        Schedule schedule1 = Schedule.builder().build();

                        Provider provider = Provider.builder()
                                        .name("Pending Provider")
                                        .email("pending_prov_" + UUID.randomUUID() + "@mail.com")
                                        .password(passwordEncoder.encode("Password123"))
                                        .phoneNumber("010" + String.format("%08d", (int) (Math.random() * 100_000_000)))
                                        .role(UserType.PROVIDER)
                                        .enabled(true)
                                        .verificationStatus(VerificationStatus.PENDING)
                                        .serviceType(serviceType)
                                        .location(location1)
                                        .schedule(schedule1)
                                        .nationalId(String.format("%014d", (long) (Math.random() * 100_000_000_000_000L)))
                                        .price(150.0)
                                        .priceType(PriceType.HOUR)
                                        .build();

                        providerRepository.save(provider);

                        LoginRequest loginReq = LoginRequest.builder()
                                        .emailOrPhone(provider.getEmail())
                                        .password("Password123")
                                        .build();

                        mockMvc.perform(post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginReq)))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.message", containsString("pending admin verification")));
                }

                @Test
                @DisplayName("Should allow login for verified provider")
                void login_verifiedProvider_returns200() throws Exception {
                        ServiceCategory category = serviceCategoryRepository.save(ServiceCategory.builder()
                                        .name("Category " + UUID.randomUUID().toString().substring(0, 8))
                                        .build());

                        ServiceType serviceType = serviceTypeRepository.save(ServiceType.builder()
                                        .name("Low Risk " + UUID.randomUUID().toString().substring(0, 8))
                                        .riskLevel(RiskLevel.LOW)
                                        .category(category)
                                        .defaultPriceType(PriceType.HOUR)
                                        .basePrice(200.0)
                                        .build());

                        Location location2 = Location.builder()
                                        .latitude(30.0444)
                                        .longitude(31.2357)
                                        .address("123 Test Street")
                                        .area("Maadi")
                                        .city("Cairo")
                                        .build();

                        Schedule schedule2 = Schedule.builder().build();

                        Provider provider = Provider.builder()
                                        .name("Verified Provider")
                                        .email("verified_prov_" + UUID.randomUUID() + "@mail.com")
                                        .password(passwordEncoder.encode("Password123"))
                                        .phoneNumber("010" + String.format("%08d", (int) (Math.random() * 100_000_000)))
                                        .role(UserType.PROVIDER)
                                        .enabled(true)
                                        .verificationStatus(VerificationStatus.VERIFIED)
                                        .serviceType(serviceType)
                                        .location(location2)
                                        .schedule(schedule2)
                                        .nationalId(String.format("%014d", (long) (Math.random() * 100_000_000_000_000L)))
                                        .price(150.0)
                                        .priceType(PriceType.HOUR)
                                        .build();

                        providerRepository.save(provider);

                        LoginRequest loginReq = LoginRequest.builder()
                                        .emailOrPhone(provider.getEmail())
                                        .password("Password123")
                                        .build();

                        mockMvc.perform(post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginReq)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.token").isNotEmpty());
                }
        }

        // ═══════════════════════════════════════════════════════
        // PROTECTED ENDPOINTS
        // ═══════════════════════════════════════════════════════
        // @Nested
        // @DisplayName("Protected Endpoints")
        // class ProtectedEndpoints {

        //         @Test
        //         @DisplayName("Should return 403 when no token provided")
        //         void protectedEndpoint_noToken_returns403() throws Exception {
        //                 mockMvc.perform(get("/api/providers")) 
        //                                 .andExpect(status().isForbidden());
        //         }
        // }
}