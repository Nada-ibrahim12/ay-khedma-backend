package com.aykhedma.controller;

import com.aykhedma.dto.response.NotificationDTO;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.dto.request.NotificationPreferenceRequest;
import com.aykhedma.dto.response.NotificationPreferenceDTO;
import com.aykhedma.model.notification.NotificationPreference;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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

        request.forEmailChannel(email);
        notificationService.sendNotification(request);
        return ResponseEntity.ok(buildSuccessResponse(
                "Email notification sent successfully",
                request.getUserId(),
                request.getType(),
                Map.of("email", email)));
    }

    @PostMapping("/send-push")
    public ResponseEntity<Map<String, Object>> sendPushNotification(
            @RequestParam Long userId,
            @RequestBody NotificationRequest request) {

        log.info("Received request to send push notification to userId: {}", userId);
        request.forPushChannel(userId);
        notificationService.sendNotification(request);
        return ResponseEntity.ok(buildSuccessResponse(
                "Push notification sent successfully",
                userId,
                request.getType(),
                Map.of()));

    }

    @PostMapping("/send-inapp")
    public ResponseEntity<Map<String, Object>> sendInAppNotification(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @RequestBody NotificationRequest request) {
        Long userId = consumerId;
        log.info("Received request to send in-app notification to userId: {}", userId);
        request.forInAppChannel(userId);

        NotificationRequest notificationRequest = NotificationRequest.builder()
                .userId(userId)
                .type(request.getType() != null ? request.getType() : NotificationType.GENERAL)
                .title(request.getTitle() != null ? request.getTitle() : "Real-time Notification")
                .content(request.getContent() != null ? request.getContent()
                        : "This is a test WebSocket message")
                .deepLink(request.getDeepLink())
                .sendInApp(true)
                .sendPush(false)
                .sendEmail(false)
                .sendSms(false)
                .data(Map.of(
                        "source", "ay khedma app",
                        "timestamp", String.valueOf(System.currentTimeMillis())))
                .build();

        notificationService.sendNotification(notificationRequest);
        return ResponseEntity.ok(buildSuccessResponse(
                "In-app notification sent successfully",
                userId,
                notificationRequest,
                Map.of()));
    }

    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getUserNotifications(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Long userId = consumerId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Fetching notifications for user: {}", userId);
        Page<NotificationDTO> notifications = notificationService.getUserNotifications(userId, pageable);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/{notificationId}/delivery-status")
    public ResponseEntity<com.aykhedma.dto.response.DeliveryStatusDTO> getDeliveryStatus(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @PathVariable Long notificationId) {

        Long userId = consumerId;
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            com.aykhedma.dto.response.DeliveryStatusDTO dto = notificationService.getNotificationDeliveryStatus(userId,
                    notificationId);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/failed")
    public ResponseEntity<java.util.List<NotificationDTO>> getFailedNotifications(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId) {

        Long userId = consumerId;
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        java.util.List<NotificationDTO> list = notificationService.listFailedNotifications(userId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId) {

        Long userId = consumerId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        long count = notificationService.getUnreadCount(userId);
        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        response.put("userId", userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceDTO> getPreferences(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId) {

        Long userId = consumerId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        NotificationPreferenceDTO preferences = notificationService.getUserPreferences(userId);
        return ResponseEntity.ok(preferences);
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceDTO> updatePreferences(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @RequestBody NotificationPreferenceRequest request) {

        Long userId = consumerId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        NotificationPreference preference = NotificationPreference.builder()
                .inAppEnabled(request.isInAppEnabled())
                .emailEnabled(request.isEmailEnabled())
                .pushEnabled(request.isPushEnabled())
                .build();

        NotificationPreferenceDTO updated = notificationService.updateUserPreferences(userId, preference);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDTO> getNotification(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @PathVariable Long notificationId) {

        Long userId = consumerId;

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
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @PathVariable Long notificationId) {

        Long userId = consumerId;

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
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @RequestBody Map<String, Object> request) {

        Long userId = consumerId;

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
            @AuthenticationPrincipal(expression = "user.id") Long consumerId) {

        Long userId = consumerId;

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
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @PathVariable Long notificationId) {

        Long userId = consumerId;

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
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20) Pageable pageable) {

        Long userId = consumerId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        Page<NotificationDTO> filteredNotifications = notificationService.getUserNotificationsByDateRange(userId,
                startDate, endDate, pageable);
        return ResponseEntity.ok(filteredNotifications);
    }

    @GetMapping("/types/{type}")
    public ResponseEntity<java.util.List<NotificationDTO>> getByType(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @PathVariable NotificationType type, Pageable pageable) {
        Long userId = consumerId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        java.util.List<NotificationDTO> notifications = notificationService.getNotificationsByType(userId, type);
        return ResponseEntity.ok(notifications);
    }

    @DeleteMapping("/clear/all")
    public ResponseEntity<?> clearAllNotifications(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId) {
        Long userId = consumerId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        int deleted = notificationService.deleteAllNotifications(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "All notifications cleared");
        response.put("deletedCount", deleted);
        return ResponseEntity.ok(response);
    }

    // @PutMapping("/settings")
    // public ResponseEntity<?> updateNotificationSettings(
    // @AuthenticationPrincipal(expression = "user.id") Long consumerId
    // @RequestBody NotificationSettingsRequest settings) {
    // notificationService.updateNotificationSettings(currentUser.getName(),
    // settings);
    // }

    private Map<String, Object> buildSuccessResponse(String message, Long userId, Object type,
            Map<String, Object> extraFields) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("userId", userId);
        response.put("type", type);
        response.putAll(extraFields);
        return response;
    }
}