package com.aykhedma.dto.request;

import com.aykhedma.model.chat.MessageType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {

    private String roomId;

    private String sessionId;

    @NotBlank(message = "Message content is required")
    @Size(min = 1, max = 5000, message = "Message content must be between 1 and 5000 characters")
    private String content;

    private MessageType type = MessageType.TEXT;

    @AssertTrue(message = "Either roomId or sessionId must be provided")
    private boolean isValidChat() {
        return (roomId != null && !roomId.isEmpty()) ||
                (sessionId != null && !sessionId.isEmpty());
    }
}