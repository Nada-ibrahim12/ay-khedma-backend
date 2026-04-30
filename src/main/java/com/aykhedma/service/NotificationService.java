package com.aykhedma.service;

import com.aykhedma.dto.response.NotificationDTO;
import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.model.notification.Notification;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.repository.NotificationRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.service.notification.EmailService;
import com.aykhedma.service.notification.FirebaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FirebaseService firebaseService;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public NotificationDTO getNotificationById(Long userID, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUserId().equals(userID)) {
            throw new RuntimeException("Unauthorized");
        }

        return NotificationDTO.fromEntity(notification);
    }

    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        notificationRepository.deleteById(notificationId);
    }

    @Async
    @Transactional
    public void sendNotification(NotificationRequest request) {
        validateRequest(request);
        log.info("Sending notification to user: {}, type: {}", request.getUserId(), request.getType());

        Notification notification = saveNotification(request);

        if (request.isSendInApp()) {
            sendWebSocketNotification(notification);
        }

        if (request.isSendPush() && firebaseService.isFirebaseEnabled()) {
            firebaseService.sendPushNotification(
                    request.getUserId(),
                    request.getTitle(),
                    request.getContent(),
                    request.getData());
        }

        if (request.isSendEmail()) {
            String email = request.getEmail() != null ? request.getEmail() : resolveUserEmail(request.getUserId());
            if (email != null) {
                sendEmailNotification(email, request);
            }
        }
    }

    private Notification saveNotification(NotificationRequest request) {
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .title(request.getTitle())
                .body(request.getContent())
                .imageUrl(request.getImageUrl())
                .build();

        return notificationRepository.save(notification);
    }

    /**
     * Send real-time notification via WebSocket
     */
    private void sendWebSocketNotification(Notification notification) {
        NotificationDTO dto = NotificationDTO.fromEntity(notification);

        messagingTemplate.convertAndSendToUser(
                notification.getUserId().toString(),
                "/queue/notifications",
                dto);

        long unreadCount = notificationRepository.countUnreadByUserId(notification.getUserId());

        messagingTemplate.convertAndSendToUser(
                notification.getUserId().toString(),
                "/queue/notifications/count",
                Map.of("count", unreadCount));

        notification.setDelivered(true);
        notification.setDeliveredAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    private void sendEmailNotification(String email, NotificationRequest request) {
        Map<String, Object> templateVars = new HashMap<>();
        templateVars.put("title", request.getTitle());
        templateVars.put("content", request.getContent());
        templateVars.put("type", request.getType());

        if (request.getData() != null) {
            templateVars.putAll(request.getData());
        }

        String templateName = getEmailTemplate(request.getType());
        emailService.sendHtmlEmail(email, request.getTitle(), templateName, templateVars);
    }

    public void sendOtpEmail(String email, String otp) {
        String subject = "Your OTP Verification Code";
        Map<String, Object> templateVars = new HashMap<>();
        templateVars.put("otp", otp);
        emailService.sendHtmlEmail(email, subject, "email/otp-verification", templateVars);
    }

    public void sendPasswordResetEmail(String email, String otp) {
        String subject = "Password Reset Request";
        Map<String, Object> templateVars = new HashMap<>();
        templateVars.put("otp", otp);
        emailService.sendHtmlEmail(email, subject, "email/password-reset", templateVars);
    }

    private String getEmailTemplate(NotificationType type) {
        return switch (type) {
            case BOOKING_CONFIRMATION -> "email/booking-confirmation";
            case BOOKING_REMINDER -> "email/booking-reminder";
            case BOOKING_REQUEST -> "email/booking-request";
            case BOOKING_CANCELLED -> "email/booking-cancelled";
            case BOOKING_COMPLETED -> "email/booking-completed";
            case PROVIDER_ACCEPTED -> "email/provider-approved";
            case PROVIDER_REJECTED -> "email/provider-rejected";
            case ACCOUNT_UPDATE -> "email/account-update";
            case PASSWORD_CHANGED -> "email/password-changed";
            case LOCATION_UPDATE -> "email/location-update";
            case STATUS_UPDATE -> "email/status-update";
            case SYSTEM_ALERT -> "email/system-alert";
            default -> "email/general-notification";
        };
    }

    private String resolveUserEmail(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getEmail())
                .orElse(null);
    }

    public Page<NotificationDTO> getUserNotifications(Long userId, Pageable pageable) {
        Page<Notification> notificationPage = notificationRepository.findByUserId(userId, pageable);
        return notificationPage.map(NotificationDTO::fromEntity);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);

        // Send updated count via WebSocket
        long unreadCount = notificationRepository.countUnreadByUserId(userId);
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications/count",
                Map.of("count", unreadCount));
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsRead(userId);
        log.info("Marked {} notifications as read for user: {}", updated, userId);

        // Send updated count via WebSocket
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications/count",
                Map.of("count", 0));
    }

    @Transactional
    public void cleanupOldNotifications(int retentionDays) {
        LocalDateTime expiryDate = LocalDateTime.now().minusDays(retentionDays);
        int deleted = notificationRepository.deleteOldNotifications(expiryDate);
        log.info("Deleted {} old notifications", deleted);
    }

    // filter
    /**
     * Get notifications filtered by date range
     *
     * @param userId    User ID (passed as header or parameter)
     * @param startDate Start date (optional)
     * @param endDate   End date (optional)
     * @param pageable  Pagination information
     * @return Filtered notifications
     */
    public Page<NotificationDTO> getUserNotificationsByDateRange(Long userId, LocalDateTime startDate,
            LocalDateTime endDate, Pageable pageable) {
        List<Notification> notifications = notificationRepository.findByUserIdAndCreatedAtBetween(userId, startDate,
                endDate, pageable);
        List<NotificationDTO> notificationDTOs = notifications.stream()
                .map(NotificationDTO::fromEntity)
                .collect(Collectors.toList());
        return new PageImpl<>(notificationDTOs, pageable, notifications.size());
    }

    private void validateRequest(NotificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Notification request is required");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("Notification type is required");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Notification title is required");
        }
    }

}