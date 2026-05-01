package com.aykhedma.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatRoomResponse {

    private String roomId;
    private Long otherUserId;
    private String otherUserName;
    private String otherUserProfileImage;

    private String lastMessage;
    private LocalDateTime lastMessageTime;

    private long unreadCount;
}
