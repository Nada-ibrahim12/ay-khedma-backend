package com.aykhedma.repository;

import com.aykhedma.model.RefreshToken;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("RefreshTokenRepository Tests")
class RefreshTokenRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Consumer savedUser;

    @BeforeEach
    void setUp() {
        Consumer consumer = Consumer.builder()
                .name("Token User")
                .email("token@test.com")
                .phoneNumber("01011111111")
                .password("encodedPassword1")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(0)
                .build();
        savedUser = entityManager.persistAndFlush(consumer);
    }

    // ═══════════════════════════════════════════════════════
    // findByToken
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("findByToken() should return token when it exists")
    void findByToken_existingToken_returnsToken() {
        RefreshToken token = RefreshToken.builder()
                .token("test-refresh-token-uuid")
                .user(savedUser)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();
        entityManager.persistAndFlush(token);

        Optional<RefreshToken> result = refreshTokenRepository.findByToken("test-refresh-token-uuid");

        assertThat(result).isPresent();
        assertThat(result.get().getUser().getEmail()).isEqualTo("token@test.com");
    }

    @Test
    @DisplayName("findByToken() should return empty when token does not exist")
    void findByToken_nonExistentToken_returnsEmpty() {
        Optional<RefreshToken> result = refreshTokenRepository.findByToken("non-existent");

        assertThat(result).isEmpty();
    }

    // ═══════════════════════════════════════════════════════
    // deleteByUser
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("deleteByUser() should remove the token for a user")
    void deleteByUser_removesToken() {
        RefreshToken token = RefreshToken.builder()
                .token("token-to-delete")
                .user(savedUser)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();
        entityManager.persistAndFlush(token);

        // Verify it exists
        assertThat(refreshTokenRepository.findByToken("token-to-delete")).isPresent();

        // Delete
        refreshTokenRepository.deleteByUser(savedUser);
        entityManager.flush();
        entityManager.clear();

        // Verify it's gone
        assertThat(refreshTokenRepository.findByToken("token-to-delete")).isEmpty();
    }
}
