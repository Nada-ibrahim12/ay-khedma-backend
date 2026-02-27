package com.aykhedma.service;

import com.aykhedma.Auth.AuthService;
import com.aykhedma.Auth.RefreshTokenService;
import com.aykhedma.dto.request.LoginRequest;
import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.dto.response.AuthResponse;
import com.aykhedma.model.RefreshToken;
import com.aykhedma.model.user.*;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    // ─── Helper ───────────────────────────────────────────
    private Consumer buildConsumer(String email, String phone, String encodedPwd) {
        return Consumer.builder()
                .id(1L)
                .name("Test User")
                .email(email)
                .phoneNumber(phone)
                .password(encodedPwd)
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(0)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // REGISTER
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("Should register a CONSUMER successfully")
        void register_consumer_success() {
            RegisterRequest req = RegisterRequest.builder()
                    .name("Ahmed")
                    .email("ahmed@mail.com")
                    .phoneNumber("01012345678")
                    .password("Password123")
                    .userType(UserType.CONSUMER)
                    .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            assertThatCode(() -> authService.register(req)).doesNotThrowAnyException();

            verify(userRepository).save(any(Consumer.class));
        }

        @Test
        @DisplayName("Should register a PROVIDER successfully")
        void register_provider_success() {
            RegisterRequest req = RegisterRequest.builder()
                    .name("Mohamed")
                    .email("mo@mail.com")
                    .phoneNumber("01098765432")
                    .password("Password123")
                    .userType(UserType.PROVIDER)
                    .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            assertThatCode(() -> authService.register(req)).doesNotThrowAnyException();

            verify(userRepository).save(any(Provider.class));
        }

        @Test
        @DisplayName("Should throw when email already exists")
        void register_duplicateEmail_throws() {
            RegisterRequest req = RegisterRequest.builder()
                    .email("dup@mail.com")
                    .build();

            when(userRepository.existsByEmail("dup@mail.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Email already exists");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when phone already exists")
        void register_duplicatePhone_throws() {
            RegisterRequest req = RegisterRequest.builder()
                    .email("new@mail.com")
                    .phoneNumber("01012345678")
                    .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByPhoneNumber("01012345678")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Phone already exists");
        }
    }

    // ═══════════════════════════════════════════════════════
    // LOGIN
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("Should login by email and return AuthResponse")
        void login_byEmail_success() {
            Consumer user = buildConsumer("test@mail.com", "01012345678", "encodedPwd");
            RefreshToken refreshToken = RefreshToken.builder()
                    .token("refresh-uuid")
                    .user(user)
                    .expiryDate(LocalDateTime.now().plusDays(7))
                    .build();

            when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Password123", "encodedPwd")).thenReturn(true);
            when(jwtService.generateToken(user)).thenReturn("jwt-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);

            LoginRequest req = LoginRequest.builder()
                    .emailOrPhone("test@mail.com")
                    .password("Password123")
                    .build();

            AuthResponse res = authService.login(req);

            assertThat(res.getToken()).isEqualTo("jwt-token");
            assertThat(res.getRefreshToken()).isEqualTo("refresh-uuid");
            assertThat(res.getTokenType()).isEqualTo("Bearer");
            assertThat(res.getUserId()).isEqualTo(1L);
            assertThat(res.getEmail()).isEqualTo("test@mail.com");
            assertThat(res.getRole()).isEqualTo(UserType.CONSUMER);
        }

        @Test
        @DisplayName("Should login by phone number when email not found")
        void login_byPhone_success() {
            Consumer user = buildConsumer("test@mail.com", "01012345678", "encodedPwd");
            RefreshToken refreshToken = RefreshToken.builder()
                    .token("refresh-uuid")
                    .user(user)
                    .expiryDate(LocalDateTime.now().plusDays(7))
                    .build();

            when(userRepository.findByEmail("01012345678")).thenReturn(Optional.empty());
            when(userRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Password123", "encodedPwd")).thenReturn(true);
            when(jwtService.generateToken(user)).thenReturn("jwt-token");
            when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);

            LoginRequest req = LoginRequest.builder()
                    .emailOrPhone("01012345678")
                    .password("Password123")
                    .build();

            AuthResponse res = authService.login(req);
            assertThat(res.getToken()).isEqualTo("jwt-token");
        }

        @Test
        @DisplayName("Should throw when user not found")
        void login_userNotFound_throws() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            when(userRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());

            LoginRequest req = LoginRequest.builder()
                    .emailOrPhone("ghost@mail.com")
                    .password("Password123")
                    .build();

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("Should throw when password is wrong")
        void login_wrongPassword_throws() {
            Consumer user = buildConsumer("test@mail.com", "01012345678", "encodedPwd");
            when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "encodedPwd")).thenReturn(false);

            LoginRequest req = LoginRequest.builder()
                    .emailOrPhone("test@mail.com")
                    .password("wrong")
                    .build();

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid credentials");
        }
    }
}
