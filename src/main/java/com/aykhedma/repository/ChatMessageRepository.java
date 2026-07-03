package com.aykhedma.repository;

import com.aykhedma.model.chat.ChatMessage;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

  List<ChatMessage> findByChatSessionSessionId(String sessionId);

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

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("""
        UPDATE ChatMessage m
        SET m.isRead = true,
            m.readAt = :currentTimestamp
        WHERE m.chatRoom.id = :roomId
          AND m.senderId <> :userId
          AND m.isRead = false
    """)
  int markMessagesAsRead(
          @Param("roomId") String roomId,
          @Param("userId") Long userId,
          @Param("currentTimestamp") LocalDateTime currentTimestamp
  );
  @Query("""
SELECT COUNT(m) > 0
FROM ChatMessage m
WHERE m.chatRoom.id = :roomId
AND m.senderId <> :userId
AND m.isRead = false
""")
  boolean existsUnreadMessages(
          @Param("roomId") String roomId,
          @Param("userId") Long userId
  );

  @Query("""
      SELECT COUNT(m) FROM ChatMessage m
      WHERE m.chatRoom.id = :roomId
        AND m.senderId != :userId
        AND m.isRead = false
      """)
  long countUnreadMessages(@Param("roomId") String roomId,
      @Param("userId") Long userId);

  @Query("""
SELECT cm.chatRoom.id, COUNT(cm)
FROM ChatMessage cm
WHERE cm.chatRoom.id IN :roomIds
AND cm.senderId <> :userId
AND cm.isRead = false
GROUP BY cm.chatRoom.id
""")
  List<Object[]> countUnreadMessagesForRooms(
          @Param("roomIds") List<String> roomIds,
          @Param("userId") Long userId);

}
