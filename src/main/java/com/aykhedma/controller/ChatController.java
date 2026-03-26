package com.aykhedma.controller;

import com.aykhedma.dto.request.ChatMessageRequest;
import com.aykhedma.dto.response.ChatMessageResponse;
import com.aykhedma.dto.response.DraftMessageResponse;
import com.aykhedma.model.chat.MessageType;
import com.aykhedma.model.user.User;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.security.JwtService;
import com.aykhedma.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
    private final UserRepository userRepository;
    private final JwtService jwtService;

    private User getUser(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        String email = jwtService.extractUsername(token);
        return userRepository.findByEmail(email).orElseThrow();
    }

    @PostMapping("/room")
    @Operation(summary = "Create or get chat room with another user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Room retrieved or created successfully",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "404", description = "Receiver not found")
    })
    public ResponseEntity<String> createRoom(@RequestParam Long receiverId, HttpServletRequest req) {
        String roomId = chatService.getOrCreateRoom(getUser(req), receiverId).getId();
        return ResponseEntity.ok(roomId);
    }

    @PostMapping(value = "/send", consumes = {"multipart/form-data"})
    @Operation(summary = "Send message to a chat room (text or media)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message sent successfully",
                    content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<ChatMessageResponse> send(
            @RequestPart(required = false) String content,
            @RequestPart(required = false) MultipartFile mediaFile,
            @RequestPart(required = false) MessageType type,
            @RequestPart String roomId,
            HttpServletRequest req
    ) throws IOException {

        // Build request DTO
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent(content);
        request.setMediaFile(mediaFile);
        request.setType(type != null ? type : MessageType.TEXT);
        request.setRoomId(roomId);

        // Send message
        return ResponseEntity.ok(chatService.sendMessage(getUser(req), request));
    }


    @GetMapping("/messages")
    @Operation(summary = "Get messages in a chat room with pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Messages retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthorized access to room")
    })
    public ResponseEntity<List<ChatMessageResponse>> messages(@RequestParam String roomId,
                                                              @RequestParam int page,
                                                              @RequestParam int size,
                                                              HttpServletRequest req) {
        List<ChatMessageResponse> messages =
                chatService.getMessages(getUser(req), roomId, page, size);

        return ResponseEntity.ok(messages);
    }


    @GetMapping("/unread")
    @Operation(summary = "Get unread messages count in a room")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unread count retrieved successfully")
    })
    public ResponseEntity<Long> unread(@RequestParam String roomId,
                                       HttpServletRequest req) {
        long count = chatService.getUnreadCount(roomId, getUser(req).getId());
        return ResponseEntity.ok(count);
    }

    @PutMapping("/message/{id}")
    @Operation(summary = "Edit a message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message edited successfully",
                    content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @ApiResponse(responseCode = "403", description = "User not owner of message"),
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    public ResponseEntity<ChatMessageResponse> edit(@PathVariable String id,
                                                    @RequestParam String content,
                                                    HttpServletRequest req) {
        ChatMessageResponse response =
                chatService.editMessage(id, content, getUser(req));

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/message/{id}")
    @Operation(summary = "Delete a message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message deleted successfully"),
            @ApiResponse(responseCode = "403", description = "User not owner of message"),
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       HttpServletRequest req) {
        chatService.deleteMessage(id, getUser(req));
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/draft/{receiverId}")
    @Operation(summary = "Get or create draft message for a room")
    public ResponseEntity<DraftMessageResponse> getDraft(
            @PathVariable Long receiverId,
            HttpServletRequest req) {
        DraftMessageResponse draft = chatService.getOrCreateDraft(getUser(req), receiverId);
        return ResponseEntity.ok(draft);
    }

    @PutMapping("/draft/{roomId}")
    @Operation(summary = "Update draft message before sending")
    public ResponseEntity<DraftMessageResponse> updateDraft(
            @PathVariable String roomId,
            @RequestBody String newContent,
            HttpServletRequest req) {
        DraftMessageResponse draft = chatService.updateDraft(roomId, getUser(req).getId(), newContent);
        return ResponseEntity.ok(draft);
    }

    @PostMapping("/draft/send/{roomId}")
    @Operation(summary = "Send the draft message to the chat")
    public ResponseEntity<ChatMessageResponse> sendDraft(
            @PathVariable String roomId,
            HttpServletRequest req) {
        ChatMessageResponse sentMessage = chatService.sendDraft(roomId, getUser(req));
        return ResponseEntity.ok(sentMessage);
    }
}