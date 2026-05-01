package com.aykhedma.dto.response;

import com.aykhedma.model.chat.*;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.UserRepository;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {

    private String id;
    private String roomId;

    private Long senderId;
    private String senderName;
    private MessageRole senderRole;
    private UserType senderUserType;

    private Long receiverId;
    private String receiverName;


    private String content;
    private MessageType type;

    private List<String> mediaUrls;
    private LocalDateTime timestamp;
    private boolean isRead;

//    private MessageStatus status;


    public static ChatMessageResponse fromEntity(ChatMessage msg, Long currentUserId, UserRepository userRepository) {

        var participants = msg.getChatRoom().getParticipants();

        var sender = userRepository.findById(msg.getSenderId()).orElse(null);
        var receiver = participants.stream()
                .filter(u -> !u.getId().equals(msg.getSenderId()))
                .findFirst()
                .orElse(null);

        return ChatMessageResponse.builder()
                .id(msg.getId())
                .roomId(msg.getChatRoom().getId())
                .senderId(msg.getSenderId())
                .senderName(sender != null ? sender.getName() : "Unknown")
                .senderRole(msg.getSenderRole())
                .senderUserType(sender != null ? sender.getRole() : null)
                .receiverId(receiver != null ? receiver.getId() : null)
                .receiverName(receiver != null ? receiver.getName() : null)
                .content(msg.getContent())
                .type(msg.getType())
                .mediaUrls(msg.getMediaUrls())
                .isRead(Boolean.TRUE.equals(msg.getIsRead()))
                .timestamp(msg.getTimestamp())
                .build();
    }
}