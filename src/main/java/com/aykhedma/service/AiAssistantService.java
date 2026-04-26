package com.aykhedma.service;

import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.response.ChatResponse;
import com.aykhedma.model.user.User;

public interface AiAssistantService {

    ChatResponse chat(AiChatRequest request, User currentUser);

    ChatResponse startNewChat(User currentUser);
}
