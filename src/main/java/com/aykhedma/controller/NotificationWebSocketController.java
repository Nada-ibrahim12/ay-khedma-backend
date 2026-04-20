package com.aykhedma.controller;

import com.aykhedma.dto.request.NotificationReadRequest;
import com.aykhedma.service.NotificationService;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class NotificationWebSocketController {

    private final NotificationService notificationService;

    @MessageMapping("/notifications/mark-read")
    @SendToUser("/queue/notifications/status")
    public Boolean markAsRead(@Payload NotificationReadRequest request) {
        notificationService.markAsRead(request.getUserId(), request.getNotificationId());
        return true;
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Throwable exception) {
        return exception.getMessage();
    }
}