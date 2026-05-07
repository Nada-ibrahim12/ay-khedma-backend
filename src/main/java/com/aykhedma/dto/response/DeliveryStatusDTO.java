package com.aykhedma.dto.response;

import com.aykhedma.model.notification.Notification;
import com.aykhedma.model.notification.NotificationChannel;
import com.aykhedma.model.notification.NotificationType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class DeliveryStatusDTO {
    private Long id;
    private NotificationType type;
    private Set<NotificationChannel> methods;
    private String title;
    private String body;
    private LocalDateTime createdAt;

    // Per-channel tracking
    private boolean pushSent;
    private LocalDateTime pushSentAt;
    private boolean pushFailed;

    private boolean emailSent;
    private LocalDateTime emailSentAt;
    private boolean emailFailed;

    private boolean inAppDelivered;
    private LocalDateTime inAppDeliveredAt;
    private boolean inAppFailed;

    public static DeliveryStatusDTO fromEntity(Notification n) {
        DeliveryStatusDTO dto = new DeliveryStatusDTO();
        dto.setId(n.getId());
        dto.setType(n.getType());
        dto.setMethods(n.getMethods());
        dto.setTitle(n.getTitle());
        dto.setBody(n.getBody());
        dto.setCreatedAt(n.getCreatedAt());

        dto.setPushSent(n.isPushSent());
        dto.setPushSentAt(n.getPushSentAt());
        dto.setPushFailed(n.isPushFailed());

        dto.setEmailSent(n.isEmailSent());
        dto.setEmailSentAt(n.getEmailSentAt());
        dto.setEmailFailed(n.isEmailFailed());

        dto.setInAppDelivered(n.isInAppDelivered());
        dto.setInAppDeliveredAt(n.getInAppDeliveredAt());
        dto.setInAppFailed(n.isInAppFailed());

        return dto;
    }
}
