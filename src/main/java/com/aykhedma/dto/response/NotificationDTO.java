package com.aykhedma.dto.response;

import com.aykhedma.model.notification.Notification;
import com.aykhedma.model.notification.NotificationType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationDTO {
    private Long id;
    private NotificationType type;
    private String title;
    private String content;
    private String imageUrl;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationDTO fromEntity(Notification notification) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setType(notification.getType());
        dto.setTitle(notification.getTitle());
        dto.setContent(notification.getBody());
        dto.setImageUrl(notification.getImageUrl());
        dto.setRead(notification.isRead());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }
}