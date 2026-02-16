package com.aykhedma.repository;

import com.aykhedma.model.chat.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findByUserId(Long userId);

    Optional<ChatSession> findBySessionIdAndIsActiveTrue(String sessionId);

    @Query("SELECT cs FROM ChatSession cs WHERE cs.userId = :userId AND cs.isActive = true")
    Optional<ChatSession> findActiveSessionByUser(@Param("userId") Long userId);

    List<ChatSession> findByUserIdOrderByStartTimeDesc(Long userId);
}