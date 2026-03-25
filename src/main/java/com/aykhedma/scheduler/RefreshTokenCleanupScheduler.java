package com.aykhedma.scheduler;

import com.aykhedma.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled task that periodically cleans up expired refresh tokens.
 * This ensures that users with expired sessions are fully logged out
 * and can log in from other devices without errors.
 *
 * Runs every 30 minutes to remove stale tokens from the database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(fixedRate = 1800000) // every 30 minutes
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        log.info("Running expired refresh token cleanup...");
        refreshTokenService.deleteExpiredTokens();
        log.info("Expired refresh token cleanup completed.");
    }
}
