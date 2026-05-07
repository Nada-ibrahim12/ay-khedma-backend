package com.aykhedma.repository;

import com.aykhedma.model.chat.ChatMessage;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

  Page<ChatMessage> findByChatRoomId(String roomId, Pageable pageable);

  Optional<ChatMessage> findTopByChatRoomIdOrderByTimestampDesc(String roomId);

  List<ChatMessage> findByChatSessionSessionIdOrderByTimestampAsc(String sessionId);

  @Query("""
SELECT m
FROM ChatMessage m
WHERE m.chatRoom.id = :roomId
ORDER BY m.timestamp DESC
""")
  List<ChatMessage> findLastMessages(@Param("roomId") String roomId, Pageable pageable);

  @Modifying
  @Query("""
      UPDATE ChatMessage m
      SET m.isRead = true,
          m.readAt = :currentTimestamp
      WHERE m.chatRoom.id = :roomId
        AND m.senderId != :userId
        AND m.isRead = false
      """)
  void markMessagesAsRead(@Param("roomId") String roomId,
                          @Param("userId") Long userId,
                          @Param("currentTimestamp")LocalDateTime currentTimestamp);

  @Query("""
      SELECT COUNT(m) FROM ChatMessage m
      WHERE m.chatRoom.id = :roomId
        AND m.senderId != :userId
        AND m.isRead = false
      """)
  long countUnreadMessages(@Param("roomId") String roomId,
      @Param("userId") Long userId);

}
