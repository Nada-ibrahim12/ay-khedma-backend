package com.aykhedma.service;

import com.aykhedma.dto.request.AiChatRequest;
import com.aykhedma.dto.response.ChatResponse;
import com.aykhedma.model.user.User;
import java.util.List;

public interface AiAssistantService {

    ChatResponse chat(AiChatRequest request, User currentUser);

    ChatResponse startNewChat(User currentUser);

    ChatResponse getChat(String sessionId, User currentUser);

    List<ChatResponse> getUserChats(User currentUser);

    boolean deleteChatbotChatSession(String sessionId, User currentUser);
}
