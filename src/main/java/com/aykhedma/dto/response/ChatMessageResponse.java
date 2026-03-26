package com.aykhedma.dto.response;

import com.aykhedma.model.chat.ChatMessage;
import com.aykhedma.model.chat.MessageRole;
import com.aykhedma.model.chat.MessageType;
import com.aykhedma.model.user.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {

    private String id;
    private Long senderId;
    private MessageRole senderRole;
    private String content;
    private MessageType type;
    private LocalDateTime timestamp;
    private String mediaUrl;
    private boolean isRead;

    public static ChatMessageResponse fromEntity(ChatMessage msg) {
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .senderId(msg.getSenderId())
                .senderRole(msg.getSenderRole())
                .content(msg.getContent())
                .type(msg.getType())
                .timestamp(msg.getTimestamp())
                .mediaUrl(msg.getMediaUrl())
                .isRead(msg.getIsRead())
                .build();
    }
}