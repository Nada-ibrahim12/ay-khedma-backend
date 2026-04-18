package com.aykhedma.service;

import com.aykhedma.dto.request.ChatMessageRequest;
import com.aykhedma.dto.response.ChatMessageResponse;
import com.aykhedma.model.chat.ChatMessage;
import com.aykhedma.model.chat.ChatRoom;
import com.aykhedma.model.chat.MessageType;
import com.aykhedma.model.user.User;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.*;
import com.aykhedma.security.CustomUserDetailsService;
import com.aykhedma.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock UserRepository userRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock MediaStorageService mediaStorageService;

    @InjectMocks ChatService chatService;
    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private CustomUserDetails getPrincipal() {

        User user = new User() {};

        user.setId(1L);
        user.setName("test user");
        user.setEmail("test@test.com");
        user.setRole(UserType.CONSUMER);
        user.setEnabled(true);

        return new CustomUserDetails(user);
    }

    // ===================== helpers =====================

    private User user(Long id) {
        User u = new User() {};
        u.setId(id);
        u.setName("User " + id);
        return u;
    }

    private ChatRoom room(User a, User b) {
        ChatRoom r = new ChatRoom();
        r.setId("room1");
        r.setParticipants(List.of(a, b));
        return r;
    }


    @Test
    void sendMessage_success() throws Exception {

        User sender = user(1L);
        User receiver = user(2L);
        ChatRoom r = room(sender, receiver);

        ChatMessageRequest req = new ChatMessageRequest();
        req.setRoomId("room1");
        req.setContent("hello");
        req.setType(MessageType.TEXT);

        when(chatRoomRepository.findById("room1")).thenReturn(Optional.of(r));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ChatMessageResponse res = chatService.sendMessage(sender, req);

        assertNotNull(res);
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/room1"), any(ChatMessageResponse.class));
    }

    @Test
    void sendMessage_unauthorized() {

        User sender = user(1L);
        ChatRoom r = room(user(2L), user(3L));

        ChatMessageRequest req = new ChatMessageRequest();
        req.setRoomId("room1");

        when(chatRoomRepository.findById("room1")).thenReturn(Optional.of(r));

        assertThrows(RuntimeException.class,
                () -> chatService.sendMessage(sender, req));
    }

    @Test
    void getMessages_success() {

        User user = user(1L);
        User other = user(2L);

        ChatRoom r = room(user, other);

        ChatMessage msg = new ChatMessage();
        msg.setSenderId(1L);
        msg.setChatRoom(r);

        when(chatRoomRepository.findById("room1")).thenReturn(Optional.of(r));
        when(chatMessageRepository.findByChatRoomId(eq("room1"), any()))
                .thenReturn(new PageImpl<>(List.of(msg)));

        var result = chatService.getMessages(user, "room1", 0, 10);

        assertEquals(1, result.size());
    }

    @Test
    void deleteMessage_success() {

        User user = user(1L);

        ChatMessage msg = new ChatMessage();
        msg.setSenderId(1L);

        when(chatMessageRepository.findById("msg1"))
                .thenReturn(Optional.of(msg));

        chatService.deleteMessage("msg1", user);

        verify(chatMessageRepository).delete(msg);
    }
}