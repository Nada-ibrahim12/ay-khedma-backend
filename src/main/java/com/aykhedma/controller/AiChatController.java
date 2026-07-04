package com.aykhedma.controller;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.response.ChatResponse;
import com.aykhedma.model.user.User;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.service.AiAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.http.HttpStatus;

@Slf4j
@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class AiChatController {

    private final AiAssistantService aiAssistantService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatResponse> chatWithVoice(
            @RequestPart(value = "sessionId", required = false) String sessionId,
            @RequestPart(value = "message", required = false) String message,
            @RequestPart(value = "voiceNote", required = false) MultipartFile voiceNote,
            @RequestPart(value = "providerId", required = false) Long providerId,
            @RequestPart(value = "serviceTypeId", required = false) Long serviceTypeId,
            @RequestPart(value = "requestedDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestedDate,
            @RequestPart(value = "requestedTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime requestedTime,
            @RequestPart(value = "location", required = false) LocationDTO location,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            log.info("Chat request - sessionId: {}, voiceNote: {}",
                    sessionId, voiceNote != null ? "present (" + voiceNote.getSize() + " bytes)" : "null");

            AiChatRequest request = AiChatRequest.builder()
                    .sessionId(sessionId)
                    .message(message)
                    .voiceNote(voiceNote)
                    .providerId(providerId)
                    .serviceTypeId(serviceTypeId)
                    .requestedDate(requestedDate)
                    .requestedTime(requestedTime)
                    .location(location)
                    .build();

            User currentUser = userDetails != null ? userDetails.getUser() : null;
            ChatResponse response = aiAssistantService.chat(request, currentUser);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in chatWithVoice: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing chat with voice: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            User currentUser = userDetails != null ? userDetails.getUser() : null;
            ChatResponse response = aiAssistantService.chat(request, currentUser);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in chat: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing chat: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/new-chat")
    public ResponseEntity<ChatResponse> startNewChat(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            User currentUser = userDetails != null ? userDetails.getUser() : null;
            ChatResponse response = aiAssistantService.startNewChat(currentUser);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in startNewChat: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error starting new chat: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/chat/{sessionId}")
    public ResponseEntity<ChatResponse> getChat(
            @PathVariable String sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            User currentUser = userDetails != null ? userDetails.getUser() : null;
            ChatResponse response = aiAssistantService.getChat(sessionId, currentUser);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getChat: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting chat: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/chats")
    public ResponseEntity<List<ChatResponse>> getUserChats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            User currentUser = userDetails != null ? userDetails.getUser() : null;
            List<ChatResponse> responses = aiAssistantService.getUserChats(currentUser);
            return ResponseEntity.ok(responses);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getUserChats: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting user chats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> deleteChat(@PathVariable String sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            User currentUser = userDetails != null ? userDetails.getUser() : null;

            boolean deleted = aiAssistantService.deleteChatbotChatSession(sessionId, currentUser);

            if (deleted) {
                return ResponseEntity.ok(Map.of(
                        "message", "Chat session deleted successfully",
                        "sessionId", sessionId));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Chat session not found or could not be deleted"));
            }

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in deleteChat: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid session ID provided"));
        } catch (Exception e) {
            log.error("Error deleting chat session: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An error occurred while deleting the chat session"));
        }
    }
}