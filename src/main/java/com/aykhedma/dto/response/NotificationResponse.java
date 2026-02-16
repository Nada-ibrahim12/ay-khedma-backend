package com.aykhedma.dto.response;

import com.aykhedma.model.notification.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private String title;
    private String body;
    private NotificationType type;
    private Map<String, String> data;
    private LocalDateTime sentAt;
    private boolean isRead;
    private String imageUrl;
    private String deepLink;
}