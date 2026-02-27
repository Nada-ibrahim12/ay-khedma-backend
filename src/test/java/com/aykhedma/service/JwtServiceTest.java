package com.aykhedma.service;

import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                "test-secret-key-that-is-long-enough-32bytes!!");
        ReflectionTestUtils.setField(jwtService, "expiration", 3600000L);
    }

    private User buildUser() {
        return Consumer.builder()
                .id(1L)
                .name("Test User")
                .email("test@mail.com")
                .phoneNumber("01012345678")
                .password("encoded")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(0)
                .build();
    }

    @Test
    @DisplayName("generateToken() should produce a non-null JWT string")
    void generateToken_returnsNonNullJwt() {
        String token = jwtService.generateToken(buildUser());

        assertThat(token).isNotNull().isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("extractUsername() should return the user's email")
    void extractUsername_returnsCorrectEmail() {
        User user = buildUser();
        String token = jwtService.generateToken(user);

        String username = jwtService.extractUsername(token);

        assertThat(username).isEqualTo("test@mail.com");
    }

    @Test
    @DisplayName("isTokenValid() should return true for a freshly generated token")
    void isTokenValid_freshToken_returnsTrue() {
        String token = jwtService.generateToken(buildUser());

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid() should return false for a tampered token")
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = jwtService.generateToken(buildUser());
        String tampered = token + "TAMPERED";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid() should return false for garbage input")
    void isTokenValid_garbageInput_returnsFalse() {
        assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
        assertThat(jwtService.isTokenValid("completely-random-garbage-string")).isFalse();
    }
}
