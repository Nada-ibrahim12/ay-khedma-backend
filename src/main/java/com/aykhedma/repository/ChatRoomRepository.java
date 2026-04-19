package com.aykhedma.repository;

import com.aykhedma.model.chat.ChatMessage;
import com.aykhedma.model.chat.ChatRoom;
import com.aykhedma.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {

    @Query("SELECT r FROM ChatRoom r JOIN r.participants p1 JOIN r.participants p2 " +
            "WHERE p1 = :user1 AND p2 = :user2")
    Optional<ChatRoom> findByParticipants(User user1, User user2);

    @Query("SELECT r FROM ChatRoom r JOIN r.participants p WHERE p = :user")
    List<ChatRoom> findAllByParticipant(User user);

    @Query("""
    SELECT r FROM ChatRoom r
    JOIN r.participants p1
    JOIN r.participants p2
    WHERE p1 = :u1 AND p2 = :u2
    """)
    Optional<ChatRoom> findRoomBetweenUsers(@Param("u1") User u1,
                                            @Param("u2") User u2);
}
