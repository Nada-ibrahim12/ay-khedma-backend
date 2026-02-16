package com.aykhedma.repository;

import com.aykhedma.model.chat.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {

    @Query("SELECT cr FROM ChatRoom cr JOIN cr.participants p WHERE p.id = :userId")
    List<ChatRoom> findByParticipantId(@Param("userId") Long userId);

    @Query("SELECT cr FROM ChatRoom cr JOIN cr.participants p1 JOIN cr.participants p2 " +
            "WHERE p1.id = :user1Id AND p2.id = :user2Id AND SIZE(cr.participants) = 2")
    Optional<ChatRoom> findDirectChatRoom(@Param("user1Id") Long user1Id,
                                          @Param("user2Id") Long user2Id);

    @Query("SELECT cr FROM ChatRoom cr WHERE cr.isActive = true ORDER BY cr.lastMessageAt DESC")
    List<ChatRoom> findActiveRooms();
}