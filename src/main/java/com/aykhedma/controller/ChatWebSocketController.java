package com.aykhedma.controller;

import com.aykhedma.dto.request.ChatMessageRequest;
import com.aykhedma.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void send(ChatMessageRequest request) {

    }
//    @MessageMapping("/typing")
//    public void typing(TypingEvent event) {
//
//        messagingTemplate.convertAndSend(
//                "/topic/typing/" + event.getRoomId(),
//                event
//        );
//    }
//    private final SimpMessagingTemplate messagingTemplate;
//    private final ChatService chatService;
//    private final ChatRoomRepository roomRepository;

//    @MessageMapping("/chat.send")
//    public void sendMessage(@Payload ChatMessageRequest request) {
//        ChatRoom room = roomRepository.findById(request.getRoomId())
//                .orElseThrow(() -> new RuntimeException("Room not found"));
//
//        // 1. Save to Database
//        ChatMessage savedMsg = chatService.saveMessage(request, room);
//
//        // 2. Map to Response DTO
//        ChatMessageResponse response = ChatMessageResponse.fromEntity(savedMsg);
//
//        // 3. Broadcast to the specific room topic
//        // Frontend must subscribe to: /topic/room/{roomId}
//        messagingTemplate.convertAndSend("/topic/room/" + request.getRoomId(), response);
//    }
}
