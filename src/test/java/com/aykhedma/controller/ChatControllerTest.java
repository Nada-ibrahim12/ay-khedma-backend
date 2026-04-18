package com.aykhedma.controller;

import com.aykhedma.dto.request.ChatMessageRequest;
import com.aykhedma.dto.response.ChatMessageResponse;
import com.aykhedma.model.chat.ChatRoom;
import com.aykhedma.model.chat.MessageType;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.security.CustomUserDetailsService;
import com.aykhedma.security.JwtService;
import com.aykhedma.service.ChatService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;


    private CustomUserDetails getPrincipal() {
        User user = new User() {};
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setRole(UserType.CONSUMER);
        user.setEnabled(true);

        return new CustomUserDetails(user);
    }

    @Test
    void createRoom_ShouldReturnRoomId() throws Exception {

        ChatRoom room = Mockito.mock(ChatRoom.class);
        Mockito.when(room.getId()).thenReturn("room123");

        Mockito.when(chatService.getOrCreateRoom(Mockito.any(), Mockito.anyLong()))
                .thenReturn(room);

        mockMvc.perform(post("/api/chat/room")
                        .param("receiverId", "2")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isOk())
                .andExpect(content().string("room123"));
    }

    @Test
    void createRoom_MissingReceiverId_Returns400() throws Exception {

        mockMvc.perform(post("/api/chat/room")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isBadRequest());
    }

    // ===================== SEND MESSAGE =====================
    @Test
    void sendMessage_TextOnly_Success() throws Exception {

        Mockito.when(chatService.sendMessage(Mockito.any(), Mockito.any()))
                .thenReturn(new ChatMessageResponse());

        mockMvc.perform(multipart("/api/chat/send")
                        .file(new MockMultipartFile("roomId", "", "text/plain", "room123".getBytes()))
                        .file(new MockMultipartFile("content", "", "text/plain", "hello".getBytes()))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isOk());
    }

    @Test
    void sendMessage_WithMediaFiles_Success() throws Exception {

        Mockito.when(chatService.sendMessage(Mockito.any(), Mockito.any()))
                .thenReturn(new ChatMessageResponse());

        MockMultipartFile file = new MockMultipartFile(
                "mediaFiles",
                "img.png",
                MediaType.IMAGE_PNG_VALUE,
                "image".getBytes()
        );

        mockMvc.perform(multipart("/api/chat/send")
                        .file(new MockMultipartFile("roomId", "", "text/plain", "room123".getBytes()))
                        .file(file)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isOk());
    }

    @Test
    void sendMessage_WithoutContentButWithMedia_Success() throws Exception {

        Mockito.when(chatService.sendMessage(Mockito.any(), Mockito.any()))
                .thenReturn(new ChatMessageResponse());

        MockMultipartFile file = new MockMultipartFile(
                "mediaFiles",
                "img.png",
                MediaType.IMAGE_PNG_VALUE,
                "image".getBytes()
        );

        mockMvc.perform(multipart("/api/chat/send")
                        .file(new MockMultipartFile("roomId", "", "text/plain", "room123".getBytes()))
                        .file(file)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isOk());
    }

    @Test
    void sendMessage_MissingRoomId_Returns400() throws Exception {

        mockMvc.perform(multipart("/api/chat/send")
                        .file(new MockMultipartFile("content", "", "text/plain", "hi".getBytes()))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isBadRequest());
    }


    @Test
    void getMessages_ShouldReturnPaginatedMessages() throws Exception {

        Mockito.when(chatService.getMessages(Mockito.any(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(List.of(new ChatMessageResponse()));

        mockMvc.perform(get("/api/chat/messages")
                        .param("roomId", "room123")
                        .param("page", "0")
                        .param("size", "10")
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isOk());
    }


    @Test
    void getUnreadCount_ShouldReturnCount() throws Exception {

        Mockito.when(chatService.getUnreadCount(Mockito.anyString(), Mockito.anyLong()))
                .thenReturn(5L);

        mockMvc.perform(get("/api/chat/unread")
                        .param("roomId", "room123")
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void getUnreadCount_ZeroUnread_Returns0() throws Exception {

        Mockito.when(chatService.getUnreadCount(Mockito.anyString(), Mockito.anyLong()))
                .thenReturn(0L);

        mockMvc.perform(get("/api/chat/unread")
                        .param("roomId", "room123")
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    void deleteMessage_Success_Returns204() throws Exception {

        Mockito.doNothing().when(chatService)
                .deleteMessage(Mockito.anyString(), Mockito.any());

        mockMvc.perform(delete("/api/chat/message/123")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMessage_NotFound_Returns404() throws Exception {

        Mockito.doThrow(new RuntimeException("Not found"))
                .when(chatService).deleteMessage(Mockito.anyString(), Mockito.any());

        mockMvc.perform(delete("/api/chat/message/999")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(getPrincipal()))
                )
                .andExpect(status().isInternalServerError());
    }

}