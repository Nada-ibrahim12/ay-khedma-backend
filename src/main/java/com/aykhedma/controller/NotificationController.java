// NotificationController.java
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Get all notifications for a user
     *
     * @param userId User ID (passed as header or parameter)
     * @param pageable Pagination information
     * @return Page of notifications
     */
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

    /**
     * Get unread notifications count for a user
     *
     * @param userId User ID (passed as header or parameter)
     * @return Unread count
     */
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

    /**
     * Get a specific notification by ID
     *
     * @param userId User ID (passed as header or parameter)
     * @param notificationId Notification ID
     * @return Notification details
     */
    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDTO> getNotification(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @PathVariable Long notificationId) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        // TODO: Implement getNotificationById in service
        return ResponseEntity.ok().build();
    }

    /**
     * Mark a single notification as read
     *
     * @param userId User ID (passed as header or parameter)
     * @param notificationId Notification ID to mark as read
     * @return Success response
     */
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

    /**
     * Mark multiple notifications as read
     *
     * @param userId User ID (passed as header or parameter)
     * @param request Map containing notification IDs
     * @return Success response
     */
    @PutMapping("/read-batch")
    public ResponseEntity<Map<String, Object>> markMultipleAsRead(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestBody Map<String, Object> request) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        // Extract notification IDs from request
        // This expects JSON like: { "notificationIds": [1, 2, 3] }
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

    /**
     * Mark all notifications as read for a user
     *
     * @param userId User ID (passed as header or parameter)
     * @return Success response
     */
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

    /**
     * Delete a notification
     *
     * @param userId User ID (passed as header or parameter)
     * @param notificationId Notification ID to delete
     * @return Success response
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Map<String, Object>> deleteNotification(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @PathVariable Long notificationId) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        // TODO: Implement delete method in service
        // notificationService.deleteNotification(userId, notificationId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Notification deleted");
        response.put("notificationId", notificationId);

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

    /**
     * Get notifications filtered by date range
     *
     * @param userId User ID (passed as header or parameter)
     * @param startDate Start date (optional)
     * @param endDate End date (optional)
     * @param pageable Pagination information
     * @return Filtered notifications
     */
    @GetMapping("/filter")
    public ResponseEntity<Page<NotificationDTO>> filterNotifications(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20) Pageable pageable) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        // TODO: Implement filter method in service
        return ResponseEntity.ok(Page.empty());
    }

    /**
     * Get notification statistics for a user
     *
     * @param userId User ID (passed as header or parameter)
     * @return Statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getNotificationStats(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long paramUserId) {

        Long userId = headerUserId != null ? headerUserId : paramUserId;

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        long unreadCount = notificationService.getUnreadCount(userId);
        // TODO: Get total count, today's count, etc.

        Map<String, Object> stats = new HashMap<>();
        stats.put("userId", userId);
        stats.put("unreadCount", unreadCount);
        stats.put("totalCount", 0); // Implement this
        stats.put("todayCount", 0);  // Implement this

        return ResponseEntity.ok(stats);
    }
}