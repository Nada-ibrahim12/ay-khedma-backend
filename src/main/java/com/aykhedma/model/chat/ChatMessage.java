package com.aykhedma.model.chat;

import com.aykhedma.model.user.UserType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotNull(message = "Chat room is required")
    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    @NotNull(message = "Sender ID is required")
    private Long senderId;

    @NotNull(message = "Sender role is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole senderRole;

    @Size(max = 5000, message = "Message content cannot exceed 5000 characters")
    @Column(length = 5000, nullable = true)
    private String content;

    @NotNull(message = "Message type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type = MessageType.TEXT;

    @PastOrPresent(message = "Timestamp cannot be in the future")
    @CreationTimestamp
    private LocalDateTime timestamp;

    @Size(max = 500, message = "Media URL cannot exceed 500 characters")
    @ElementCollection
    //@Pattern(regexp = "^(http|https|ftp)://.*$", message = "Invalid media URL format")
    private List<String> mediaUrls;

    @Size(max = 64, message = "Audio hash must be 64 characters or less")
    private String originalAudioHash;

    private Boolean isRead = false;
//    @Enumerated(EnumType.STRING)
//    private MessageStatus status;

    @PastOrPresent(message = "Read timestamp cannot be in the future")
    private LocalDateTime readAt;

    public boolean isUserMessage() {
        return senderRole == MessageRole.USER;
    }

    public boolean isAssistantMessage() {
        return senderRole == MessageRole.ASSISTANT;
    }
}