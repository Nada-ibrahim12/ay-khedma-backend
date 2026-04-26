package com.aykhedma.controller;

import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.response.ChatResponse;
import com.aykhedma.model.user.User;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.service.AiAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class AiChatController {

    private final AiAssistantService aiAssistantService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User currentUser = userDetails != null ? userDetails.getUser() : null;
        return ResponseEntity.ok(aiAssistantService.chat(request, currentUser));
    }

    @PostMapping("/new-chat")
    public ResponseEntity<ChatResponse> startNewChat(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User currentUser = userDetails != null ? userDetails.getUser() : null;
        return ResponseEntity.ok(aiAssistantService.startNewChat(currentUser));
    }
}
