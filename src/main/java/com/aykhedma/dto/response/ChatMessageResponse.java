package com.aykhedma.dto.response;

import com.aykhedma.model.chat.MessageRole;
import com.aykhedma.model.chat.MessageType;
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
    private MessageRole senderRole;
    private String content;
    private MessageType type;
    private LocalDateTime timestamp;
    private String mediaUrl;
    private boolean isRead;
}