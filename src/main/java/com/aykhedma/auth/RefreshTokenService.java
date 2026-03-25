package com.aykhedma.auth;

import com.aykhedma.model.user.RefreshToken;
import com.aykhedma.model.user.User;
import com.aykhedma.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    @Value("${jwt.refresh-expiration-minutes:10080}")
    private long refreshExpirationMinutes; // default 7 days

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    public void deleteByUser(User user) {
        repository.deleteByUser(user);
    }

    public RefreshToken createRefreshToken(User user) {
        // Delete any existing refresh tokens for this user (invalidates old sessions)
        repository.deleteByUser(user);

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(LocalDateTime.now().plusMinutes(refreshExpirationMinutes))
                .build();

        return repository.save(token);
    }

    public RefreshToken verify(String token) {
        RefreshToken refreshToken = repository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            // Clean up the expired token
            repository.delete(refreshToken);
            throw new RuntimeException("Expired refresh token. Please login again.");
        }

        return refreshToken;
    }

    /**
     * Deletes all expired refresh tokens from the database.
     * Called by the scheduled cleanup task.
     */
    public void deleteExpiredTokens() {
        repository.deleteByExpiryDateBefore(LocalDateTime.now());
    }
}
