package com.aykhedma.dto.request;

import com.aykhedma.model.notification.NotificationType;
import lombok.Data;
import lombok.Builder;
import java.util.Map;

@Data
@Builder
public class NotificationRequest {
    private Long userId;
    private NotificationType type;
    private String title;
    private String content;
    private String imageUrl;
    private Map<String, String> data;

    // Channel flags
    private boolean sendPush;
    private boolean sendInApp;
    private boolean sendEmail;
    private boolean sendSms;

    // Contact info (if different from user profile)
    private String email;
    private String phoneNumber;
}