package com.aykhedma.repository;

import com.aykhedma.model.RefreshToken;
import com.aykhedma.model.user.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);
    @Transactional
    @Modifying
    void deleteByUser(User user);
}
