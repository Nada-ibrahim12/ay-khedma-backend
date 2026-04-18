package com.aykhedma.repository;

import com.aykhedma.model.chat.ChatMessage;
import com.aykhedma.model.chat.ChatRoom;
import com.aykhedma.model.chat.MessageRole;
import com.aykhedma.model.chat.MessageType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ChatMessageRepositoryTest {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private TestEntityManager em;


    private Consumer user(Long id) {
        Consumer u = TestDataFactory.createConsumer(id);
        return em.merge(u);
    }


    private ChatRoom room(Consumer u1, Consumer u2) {
        ChatRoom r = new ChatRoom();
        r.setId("room-" + System.nanoTime());
        r.setParticipants(List.of(u1, u2));
        return em.merge(r);
    }


    private ChatMessage message(ChatRoom room, Consumer sender) {
        ChatMessage m = new ChatMessage();
        m.setChatRoom(room);
        m.setSenderId(sender.getId());
        m.setSenderRole(MessageRole.USER); 
        m.setContent("hello");
        m.setType(MessageType.TEXT);
        m.setIsRead(false);

        return em.merge(m);
    }


    @Test
    void findByChatRoomId_shouldReturnMessages() {

        Consumer u1 = user(1L);
        Consumer u2 = user(2L);

        ChatRoom r = room(u1, u2);

        message(r, u1);
        message(r, u1);

        var result = chatMessageRepository.findByChatRoomId(
                r.getId(),
                PageRequest.of(0, 10)
        );

        assertEquals(2, result.getTotalElements());
    }

    @Test
    void markMessagesAsRead_shouldUpdateMessages() {

        Consumer u1 = user(1L);
        Consumer u2 = user(2L);

        ChatRoom r = room(u1, u2);

        ChatMessage m1 = message(r, u1);
        ChatMessage m2 = message(r, u1);

        chatMessageRepository.markMessagesAsRead(r.getId(), u2.getId());

        em.flush();
        em.clear();

        ChatMessage updated =
                em.find(ChatMessage.class, m1.getId());

        assertTrue(updated.getIsRead());
    }

    @Test
    void countUnreadMessages_shouldReturnCorrectCount() {

        Consumer u1 = user(1L);
        Consumer u2 = user(2L);

        ChatRoom r = room(u1, u2);

        message(r, u1);
        message(r, u1);

        long count = chatMessageRepository.countUnreadMessages(
                r.getId(),
                u2.getId()
        );

        assertEquals(2, count);
    }
}