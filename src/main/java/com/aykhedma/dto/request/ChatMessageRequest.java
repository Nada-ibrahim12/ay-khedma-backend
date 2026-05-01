package com.aykhedma.dto.request;

import com.aykhedma.model.chat.MessageRole;
import com.aykhedma.model.chat.MessageType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {

    @NotBlank(message = "Room ID is required")
    private String roomId;

    private String sessionId;

    private Boolean isDraft;
//    @NotNull(message = "Sender ID is required")
//    private Long senderId;

    @NotNull(message = "receiver ID is required")
    private Long receiverId;

    @NotNull(message = "Sender role is required")
    private MessageRole senderRole;

    //@NotBlank(message = "Message content is required")
    @Size(min = 1, max = 5000, message = "Message content must be between 1 and 5000 characters")
    private String content;

    private String mediaUrl;
    private List<MultipartFile> mediaFiles;

    private MessageType type = MessageType.TEXT;

    @AssertTrue(message = "Either roomId or sessionId must be provided")
    private boolean isValidChat() {
        return (roomId != null && !roomId.isEmpty()) ||
                (sessionId != null && !sessionId.isEmpty());
    }

    @AssertTrue(message = "Message must have content or media files")
    private boolean isValidMessage() {
        boolean hasContent = content != null && !content.isEmpty();
        boolean hasFiles = mediaFiles != null && !mediaFiles.isEmpty();

        return hasContent || hasFiles;
    }
}