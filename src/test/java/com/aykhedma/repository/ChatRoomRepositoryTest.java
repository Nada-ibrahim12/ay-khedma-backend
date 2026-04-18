package com.aykhedma.repository;

import com.aykhedma.model.chat.ChatRoom;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ChatRoomRepositoryTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private TestEntityManager em;


    private Consumer createUser(Long id) {
        Consumer u = TestDataFactory.createConsumer(id);
        return em.merge(u);
    }


    private ChatRoom createRoom(Consumer u1, Consumer u2) {
        ChatRoom r = new ChatRoom();
        r.setId("room-" + System.nanoTime());
        r.setParticipants(List.of(u1, u2));

        return em.merge(r);
    }


    @Test
    void findRoomBetweenUsers_shouldReturnRoom() {

        Consumer u1 = createUser(1L);
        Consumer u2 = createUser(2L);

        createRoom(u1, u2);

        Optional<ChatRoom> result =
                chatRoomRepository.findRoomBetweenUsers(u1, u2);

        assertTrue(result.isPresent());
    }

    @Test
    void findAllByParticipant_shouldReturnRooms() {

        Consumer u1 = createUser(1L);
        Consumer u2 = createUser(2L);
        Consumer u3 = createUser(3L);

        createRoom(u1, u2);
        createRoom(u1, u3);

        List<ChatRoom> rooms =
                chatRoomRepository.findAllByParticipant(u1);

        assertEquals(2, rooms.size());
    }
}