package com.aykhedma.dto.response;

import com.aykhedma.model.notification.Notification;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.model.notification.NotificationChannel;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class NotificationDTO {
    private Long id;
    private NotificationType type;
    private Set<NotificationChannel> methods;
    private String title;
    private String content;
    private String imageUrl;
    private String deepLink;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationDTO fromEntity(Notification notification) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setType(notification.getType());
        dto.setMethods(notification.getMethods());
        dto.setTitle(notification.getTitle());
        dto.setContent(notification.getBody());
        dto.setImageUrl(notification.getImageUrl());
        dto.setDeepLink(notification.getDeepLink());
        dto.setRead(notification.isRead());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }
}