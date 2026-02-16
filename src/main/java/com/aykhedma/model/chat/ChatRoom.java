package com.aykhedma.model.chat;

import com.aykhedma.model.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotEmpty(message = "Chat room must have two participant")
    @Size(min = 2, max = 2, message = "Chat room must have 2 participants")
    @ManyToMany
    @JoinTable(
            name = "chat_room_participants",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private List<User> participants = new ArrayList<>();

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC")
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    @PastOrPresent(message = "Created date cannot be in the future")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @PastOrPresent(message = "Last message date cannot be in the future")
    private LocalDateTime lastMessageAt;

    @Size(max = 100, message = "Room name cannot exceed 100 characters")
    private String roomName;

    @NotNull(message = "Active status is required")
    @Column(nullable = false)
    private Boolean isActive = true;

    @AssertTrue(message = "A direct chat room must have exactly 2 participants")
    private boolean isValidDirectChat() {
        if (roomName == null && participants.size() != 2) {
            return false;
        }
        return true;
    }
}