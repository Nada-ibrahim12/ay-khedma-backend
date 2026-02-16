package com.aykhedma.model.chat;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message content cannot exceed 5000 characters")
    @Column(length = 5000, nullable = false)
    private String content;

    @NotNull(message = "Message type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type = MessageType.TEXT;

    @PastOrPresent(message = "Timestamp cannot be in the future")
    @CreationTimestamp
    private LocalDateTime timestamp;

    @Size(max = 500, message = "Media URL cannot exceed 500 characters")
    @Pattern(regexp = "^(http|https|ftp)://.*$", message = "Invalid media URL format")
    private String mediaUrl;

    @Size(max = 64, message = "Audio hash must be 64 characters or less")
    private String originalAudioHash;

    private Boolean isRead = false;

    @PastOrPresent(message = "Read timestamp cannot be in the future")
    private LocalDateTime readAt;

    public boolean isUserMessage() {
        return senderRole == MessageRole.USER;
    }

    public boolean isAssistantMessage() {
        return senderRole == MessageRole.ASSISTANT;
    }
}