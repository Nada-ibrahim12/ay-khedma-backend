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
    private Map<String, Object> data;

    private boolean sendPush;
    private boolean sendInApp;
    private boolean sendEmail;
    private boolean sendSms;
    private String email;
    private String phoneNumber;

    public NotificationRequest withUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public NotificationRequest forEmailChannel(String email) {
        this.email = email;
        this.sendEmail = true;
        this.sendInApp = false;
        this.sendPush = false;
        return this;
    }

    public NotificationRequest forPushChannel(Long userId) {
        this.userId = userId;
        this.sendEmail = false;
        this.sendInApp = false;
        this.sendPush = true;
        return this;
    }

    public NotificationRequest forInAppChannel(Long userId) {
        this.userId = userId;
        this.sendEmail = false;
        this.sendInApp = true;
        this.sendPush = false;
        return this;
    }
}