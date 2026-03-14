package com.aykhedma.controller;
import com.aykhedma.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

@Controller
public class NotificationWebSocketController {

    @Autowired
    private NotificationService notificationService;

    @MessageMapping("/notifications/mark-read")
    @SendToUser("/queue/notifications/status")
    public Boolean markAsRead(Long userId,Long notificationId) {
        notificationService.markAsRead(userId, notificationId);
        return true;
    }


    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Throwable exception) {
        return exception.getMessage();
    }
}