package com.aykhedma.service;

import com.aykhedma.Auth.RefreshTokenService;
import com.aykhedma.model.RefreshToken;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService Unit Tests")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User buildUser() {
        return Consumer.builder()
                .id(1L)
                .name("Test")
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
    @DisplayName("createRefreshToken() should delete old tokens and save new one")
    void createRefreshToken_success() {
        User user = buildUser();
        when(repository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshToken result = refreshTokenService.createRefreshToken(user);

        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getExpiryDate()).isAfter(LocalDateTime.now());

        verify(repository).deleteByUser(user);
        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("verify() should return token when valid and not expired")
    void verify_validToken_returnsToken() {
        RefreshToken token = RefreshToken.builder()
                .id(1L)
                .token("valid-token")
                .user(buildUser())
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();

        when(repository.findByToken("valid-token")).thenReturn(Optional.of(token));

        RefreshToken result = refreshTokenService.verify("valid-token");

        assertThat(result.getToken()).isEqualTo("valid-token");
    }

    @Test
    @DisplayName("verify() should throw when token not found")
    void verify_invalidToken_throws() {
        when(repository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.verify("bad-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("verify() should throw when token is expired")
    void verify_expiredToken_throws() {
        RefreshToken token = RefreshToken.builder()
                .id(1L)
                .token("expired-token")
                .user(buildUser())
                .expiryDate(LocalDateTime.now().minusDays(1))
                .build();

        when(repository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.verify("expired-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Expired refresh token");
    }
}
