package com.aykhedma.service;

import com.aykhedma.dto.response.NotificationDTO;
import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.model.notification.Notification;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.repository.NotificationRepository;
import com.aykhedma.repository.NotificationPreferenceRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.service.notification.EmailService;
import com.aykhedma.service.notification.FirebaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final FirebaseService firebaseService;
    private final EmailService emailService;
//    private final SmsService smsService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Async
    @Transactional
    public void sendNotification(NotificationRequest request) {
        log.info("Sending notification to user: {}, type: {}", request.getUserId(), request.getType());

        Notification notification = saveNotification(request);

        // 2. Send real-time WebSocket (if user is online)
        if (request.isSendInApp()) {
            sendWebSocketNotification(notification);
        }

        // 3. Send push notification (for lock screen when app is closed)
        if (request.isSendPush() && firebaseService.isFirebaseEnabled()) {
            firebaseService.sendPushNotification(
                    request.getUserId(),
                    request.getTitle(),
                    request.getContent(),
                    request.getData()
            );
        }

        // 4. Send email
        if (request.isSendEmail()) {
            String email = request.getEmail() != null ? request.getEmail() : getUserEmail(request.getUserId());
            if (email != null) {
                sendEmailNotification(email, request);
            }
        }

//        // 5. Send SMS
//        if (request.isSendSms()) {
//            String phone = request.getPhoneNumber() != null ? request.getPhoneNumber() : getUserPhone(request.getUserId());
//            if (phone != null) {
//                smsService.sendSms(phone, request.getContent());
//            }
//        }
    }

    private Notification saveNotification(NotificationRequest request) {
        try {
            Notification notification = Notification.builder()
                    .userId(request.getUserId())
                    .type(request.getType())
                    .title(request.getTitle())
                    .body(request.getContent())
                    .imageUrl(request.getImageUrl())
                    .build();

            return notificationRepository.save(notification);

        } catch (Exception e) {
            log.error("Failed to save notification: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Send real-time notification via WebSocket
     */
    private void sendWebSocketNotification(Notification notification) {
        try {
            if (notification == null) return;

            NotificationDTO dto = NotificationDTO.fromEntity(notification);

            // Send to user's personal queue
            messagingTemplate.convertAndSendToUser(
                    notification.getUserId().toString(),
                    "/queue/notifications",
                    dto
            );

            // Update unread count
            long unreadCount = notificationRepository.countUnreadByUserId(notification.getUserId());

            messagingTemplate.convertAndSendToUser(
                    notification.getUserId().toString(),
                    "/queue/notifications/count",
                    Map.of("count", unreadCount)
            );

            // Mark as delivered
            notification.setDelivered(true);
            notification.setDeliveredAt(LocalDateTime.now());
            notificationRepository.save(notification);

        } catch (Exception e) {
            log.error("Failed to send WebSocket notification: {}", e.getMessage());
        }
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

    private String getEmailTemplate(NotificationType type) {
        switch (type) {
            case BOOKING_CONFIRMATION:
                return "email/booking-confirmation";
            case BOOKING_REMINDER:
                return "email/booking-reminder";
            default:
                return "email/general-notification";
        }
    }

    private String getUserEmail(Long userId) {
        String userEmail = userRepository.findById(userId).get().getEmail();
        return userEmail;
    }

    private String getUserPhone(Long userId) {
        String userPhone = userRepository.findById(userId).get().getPhoneNumber();
        return userPhone;
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
                Map.of("count", unreadCount)
        );
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsRead(userId);
        log.info("Marked {} notifications as read for user: {}", updated, userId);

        // Send updated count via WebSocket
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications/count",
                Map.of("count", 0)
        );
    }

    @Transactional
    public void cleanupOldNotifications(int retentionDays) {
        LocalDateTime expiryDate = LocalDateTime.now().minusDays(retentionDays);
        int deleted = notificationRepository.deleteOldNotifications(expiryDate);
        log.info("Deleted {} old notifications", deleted);
    }
}