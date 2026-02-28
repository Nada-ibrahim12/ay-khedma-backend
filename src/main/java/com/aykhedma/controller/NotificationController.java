package com.aykhedma.controller;

import com.aykhedma.dto.response.NotificationDTO;
import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send-email")
    public ResponseEntity<Map<String, Object>> sendEmailNotification(
            @RequestParam String email, 
            @RequestBody NotificationRequest request) {
        
        log.info("Received request to send email notification to: {}", email);
        
        request.setSendEmail(true);
        request.setSendInApp(false);
        request.setSendPush(false);
        request.setEmail(email);
        
        notificationService.sendNotification(request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Email notification sent successfully");
        response.put("email", email);
        response.put("userId", request.getUserId());
        response.put("type", request.getType());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/send-push")
    public ResponseEntity<Map<String, Object>> sendPushNotification(
            @RequestParam Long userId,
            @RequestBody NotificationRequest request) {

        log.info("Received request to send push notification to userId: {}", userId);
        request.setSendEmail(false);
        request.setSendInApp(false);
        request.setSendPush(true);
        notificationService.sendNotification(request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Push notification sent successfully");
        response.put("userId", userId);
        response.put("type", request.getType());
        return ResponseEntity.ok(response);

    }

    @PostMapping("/send-inapp")
    public ResponseEntity<Map<String, Object>> sendInAppNotification(
            @RequestParam Long userId,
            @RequestBody NotificationRequest request) {
        log.info("Received request to send in-app notification to userId: {}", userId);
        request.setSendEmail(false);    
        request.setSendInApp(true);
        request.setSendPush(false);
        notificationService.sendNotification(request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "In-app notification sent successfully");
        response.put("userId", userId);
        response.put("type", request.getType());
        return ResponseEntity.ok(response);
    }


    
    /**
     * Send a test notification (for development only)
     *
     * @param request Notification request
     * @return Success response
     */
    @PostMapping("/test/send")
    public ResponseEntity<Map<String, Object>> sendTestNotification(
            @RequestBody NotificationRequest request) {

        log.info("Sending test notification to user: {}", request.getUserId());

        notificationService.sendNotification(request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Test notification sent");
        response.put("userId", request.getUserId());
        response.put("type", request.getType());

        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getUserNotifications(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        // Get userId from header or parameter
        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Fetching notifications for user: {}", userId);
        Page<NotificationDTO> notifications = notificationService.getUserNotifications(userId, pageable);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        long count = notificationService.getUnreadCount(userId);
        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        response.put("userId", userId);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDTO> getNotification(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @PathVariable Long notificationId) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            NotificationDTO notification = notificationService.getNotificationById(userId, notificationId);
            return ResponseEntity.ok(notification);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }


    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @PathVariable Long notificationId) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            notificationService.markAsRead(userId, notificationId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Notification marked as read");
            response.put("notificationId", notificationId);
            response.put("userId", userId);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }


    @PutMapping("/read-batch")
    public ResponseEntity<Map<String, Object>> markMultipleAsRead(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestBody Map<String, Object> request) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }


        @SuppressWarnings("unchecked")
        java.util.List<Integer> ids = (java.util.List<Integer>) request.get("notificationIds");

        int count = 0;
        for (Integer id : ids) {
            try {
                notificationService.markAsRead(userId, id.longValue());
                count++;
            } catch (Exception e) {
                log.warn("Failed to mark notification {} as read: {}", id, e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("markedCount", count);
        response.put("userId", userId);

        return ResponseEntity.ok(response);
    }


    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        notificationService.markAllAsRead(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "All notifications marked as read");
        response.put("userId", userId);

        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Map<String, Object>> deleteNotification(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @PathVariable Long notificationId) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        notificationService.deleteNotification(userId, notificationId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Notification deleted");
        response.put("notificationId", notificationId);

        return ResponseEntity.ok(response);
    }


    @GetMapping("/filter")
    public ResponseEntity<Page<NotificationDTO>> filterNotifications(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20) Pageable pageable) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        Page<NotificationDTO> filteredNotifications = notificationService.getUserNotificationsByDateRange(userId, startDate, endDate, pageable);
        return ResponseEntity.ok(filteredNotifications);
    }
}