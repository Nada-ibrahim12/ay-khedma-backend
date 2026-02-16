package com.aykhedma.model.chat;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String sessionId;

    @NotNull(message = "User ID is required")
    private Long userId;

    @PastOrPresent(message = "Start time cannot be in the future")
    @CreationTimestamp
    private LocalDateTime startTime;

    @PastOrPresent(message = "End time cannot be in the future")
    private LocalDateTime endTime;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    @OrderBy("timestamp ASC")
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    @Size(min = 2, max = 10, message = "Detected language must be between 2 and 10 characters")
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Invalid language format (e.g., 'en', 'ar-EG')")
    private String detectedLanguage;

    @Size(max = 50, message = "Detected dialect cannot exceed 50 characters")
    private String detectedDialect;

    @NotNull(message = "Active status is required")
    @Column(nullable = false)
    private Boolean isActive = true;

    @AssertTrue(message = "End time must be after start time if session is ended")
    private boolean isValidEndTime() {
        if (endTime != null && !isActive) {
            return endTime.isAfter(startTime);
        }
        return true;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public ChatMessage getLastMessage() {
        if (messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    public void clearMessages() {
        messages.clear();
    }

    public void endSession() {
        this.isActive = false;
        this.endTime = LocalDateTime.now();
    }
}