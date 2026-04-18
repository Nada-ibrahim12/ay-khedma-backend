package com.aykhedma.controller;

import com.aykhedma.dto.request.ChatMessageRequest;
import com.aykhedma.dto.response.ChatMessageResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.model.chat.MessageType;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.service.ChatService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/room")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Room created or returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Receiver not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized") })
    public ResponseEntity<String> createRoom(
            @RequestParam(required = false) Long receiverId,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        if (receiverId == null) {
            throw new BadRequestException("receiverId is required");
        }

        String roomId = chatService
                .getOrCreateRoom(user.getUser(), receiverId)
                .getId();

        return ResponseEntity.ok(roomId);
    }

    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Message sent"),
            @ApiResponse(responseCode = "400", description = "Invalid request"), @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Room not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized") })
    public ResponseEntity<ChatMessageResponse> send(
            @RequestPart(required = false) String content,
            @RequestPart(required = false) List<MultipartFile> mediaFiles,
            @RequestPart(required = false) MessageType type,
            @RequestPart(required = false) String roomId,
            @AuthenticationPrincipal CustomUserDetails user
    ) throws IOException {

        if (roomId == null || roomId.isBlank()) { throw new BadRequestException("roomId is required"); }
        if ((content == null || content.isBlank()) && (mediaFiles == null || mediaFiles.isEmpty())) {
            throw new BadRequestException("Message must contain content or media"); }

        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent(content);
        request.setMediaFiles(mediaFiles);
        request.setType(type != null ? type : MessageType.TEXT);
        request.setRoomId(roomId);

        return ResponseEntity.ok(
                chatService.sendMessage(user.getUser(), request)
        );
    }

    @GetMapping("/messages")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Messages returned"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "400", description = "Invalid params"),
            @ApiResponse(responseCode = "401", description = "Unauthorized") })
    public ResponseEntity<List<ChatMessageResponse>> messages(
            @RequestParam String roomId,
            @RequestParam int page,
            @RequestParam int size,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        if (roomId == null || roomId.isBlank()) {
            throw new BadRequestException("roomId is required"); }
        if (page < 0 || size <= 0) {
            throw new BadRequestException("Invalid pagination parameters"); }

        return ResponseEntity.ok(
                chatService.getMessages(user.getUser(), roomId, page, size)
        );
    }

    @GetMapping("/unread")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Unread count returned"), @ApiResponse(responseCode = "400", description = "Invalid request"), @ApiResponse(responseCode = "401", description = "Unauthorized") })
    public ResponseEntity<Long> unread(
            @RequestParam String roomId,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        if (roomId == null || roomId.isBlank()) { throw new BadRequestException("roomId is required"); }
        return ResponseEntity.ok(
                chatService.getUnreadCount(roomId, user.getUser().getId())
        );
    }

    @DeleteMapping("/message/{id}")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Message deleted"), @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Message not found"), @ApiResponse(responseCode = "401", description = "Unauthorized") })
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        chatService.deleteMessage(id, user.getUser());
        return ResponseEntity.noContent().build();
    }
}