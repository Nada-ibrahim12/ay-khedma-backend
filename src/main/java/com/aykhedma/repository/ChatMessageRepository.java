package com.aykhedma.repository;

import com.aykhedma.model.chat.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findByChatRoomIdOrderByTimestampAsc(String roomId);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.chatRoom.id = :roomId ORDER BY cm.timestamp DESC")
    List<ChatMessage> findRecentMessages(@Param("roomId") String roomId, Pageable pageable);

    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true, cm.readAt = CURRENT_TIMESTAMP " +
            "WHERE cm.chatRoom.id = :roomId AND cm.senderId != :userId AND cm.isRead = false")
    int markMessagesAsRead(@Param("roomId") String roomId, @Param("userId") Long userId);

    @Query("SELECT COUNT(cm) FROM ChatMessage cm WHERE cm.chatRoom.id = :roomId AND cm.senderId != :userId AND cm.isRead = false")
    long countUnreadMessages(@Param("roomId") String roomId, @Param("userId") Long userId);
}