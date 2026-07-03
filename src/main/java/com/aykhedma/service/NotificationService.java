package com.aykhedma.service;

import com.aykhedma.dto.response.NotificationDTO;
import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.dto.response.NotificationPreferenceDTO;
import com.aykhedma.model.notification.Notification;
import com.aykhedma.model.notification.NotificationChannel;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.model.notification.NotificationStatus;
import com.aykhedma.model.notification.NotificationPreference;
import com.aykhedma.repository.NotificationRepository;
import com.aykhedma.repository.NotificationPreferenceRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.service.notification.EmailService;
import com.aykhedma.service.notification.FirebaseService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
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

    /**
     * Returns delivery status details for a single notification (owner-only)
     */
    public com.aykhedma.dto.response.DeliveryStatusDTO getNotificationDeliveryStatus(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        return com.aykhedma.dto.response.DeliveryStatusDTO.fromEntity(notification);
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotification(NotificationRequest request) {
        validateRequest(request);

        // Validate user exists
        if (!userRepository.existsById(request.getUserId())) {
            log.error("User not found: {}", request.getUserId());
            throw new RuntimeException("User not found: " + request.getUserId());
        }

        log.info("Sending notification to user: {}, type: {}", request.getUserId(), request.getType());

        Set<NotificationChannel> requestedMethods = resolveRequestedMethods(request);

        requestedMethods = applyUserPreferences(request.getUserId(), requestedMethods);
        Notification notification = saveNotification(request, requestedMethods);

        if (requestedMethods.contains(NotificationChannel.IN_APP)) {
            try {
                sendWebSocketNotification(notification);
                notification.setInAppDelivered(true);
                notification.setInAppDeliveredAt(LocalDateTime.now());
            } catch (Exception e) {
                notification.setInAppFailed(true);
                log.error("In-app notification failed for user {}: {}", request.getUserId(), e.getMessage(), e);
            }
        }

        if (requestedMethods.contains(NotificationChannel.PUSH)) {
            try {
                Map<String, Object> pushData = new HashMap<>();
                if (request.getData() != null) {
                    pushData.putAll(request.getData());
                }
                if (request.getDeepLink() != null) {
                    pushData.put("deepLink", request.getDeepLink());
                }
                if (request.getType() != null) {
                    pushData.put("type", request.getType().name());
                }

                NotificationStatus pushStatus = firebaseService.sendPushNotification(
                        request.getUserId(),
                        request.getTitle(),
                        request.getContent(),
                        pushData);

                if (pushStatus == NotificationStatus.DELIVERED) {
                    notification.setPushSent(true);
                    notification.setPushSentAt(LocalDateTime.now());
                } else {
                    notification.setPushFailed(true);
                    log.warn("Failed to send push notification to user: {}", request.getUserId());
                }
            } catch (Exception e) {
                notification.setPushFailed(true);
                log.error("Push notification failed for user {}: {}", request.getUserId(), e.getMessage(), e);
            }
        }

        if (requestedMethods.contains(NotificationChannel.EMAIL)) {
            String email = request.getEmail() != null ? request.getEmail() : resolveUserEmail(request.getUserId());
            if (email != null) {
                try {
                    sendEmailNotification(email, request);
                    notification.setEmailSent(true);
                    notification.setEmailSentAt(LocalDateTime.now());
                } catch (Exception e) {
                    notification.setEmailFailed(true);
                    log.error("Email notification failed for user {}: {}", request.getUserId(), e.getMessage(), e);
                }
            } else {
                notification.setEmailFailed(true);
                log.warn("No email address found for user {}", request.getUserId());
            }
        }

        updateNotificationStatus(notification, requestedMethods);
        notificationRepository.saveAndFlush(notification);
    }

    private Notification saveNotification(NotificationRequest request, Set<NotificationChannel> methods) {
        log.info("Persisting notification type={} userId={} deepLink={}", request.getType(), request.getUserId(),
                request.getDeepLink());
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .methods(methods)
                .title(request.getTitle())
                .body(request.getContent())
                .imageUrl(request.getImageUrl())
                .deepLink(request.getDeepLink())
                .build();

        return notificationRepository.saveAndFlush(notification);
    }

    /**
     * Send real-time notification via WebSocket
     */
    private void sendWebSocketNotification(Notification notification) {
        NotificationDTO dto = NotificationDTO.fromEntity(notification);

        messagingTemplate.convertAndSend(
                "/topic/notifications-" + notification.getUserId(),
                dto);

        long unreadCount = notificationRepository.countUnreadByUserId(notification.getUserId());

        messagingTemplate.convertAndSend(
                "/topic/notifications-count-" + notification.getUserId(),
                Map.of("count", unreadCount));

        notification.setDelivered(true);
        notification.setDeliveredAt(LocalDateTime.now());
        notificationRepository.saveAndFlush(notification);
    }

    private void sendEmailNotification(String email, NotificationRequest request) {
        Map<String, Object> templateVars = new HashMap<>();
        templateVars.put("title", request.getTitle());
        templateVars.put("content", request.getContent());
        templateVars.put("type", request.getType());
        templateVars.put("deepLink", request.getDeepLink());

        if (request.getData() != null) {
            templateVars.putAll(request.getData());
        }

        String templateName = getEmailTemplate(request.getType());
        emailService.sendHtmlEmail(email, request.getTitle(), templateName, templateVars);
    }

    private Set<NotificationChannel> resolveRequestedMethods(NotificationRequest request) {
        if (request.getMethods() != null && !request.getMethods().isEmpty()) {
            return request.getMethods();
        }

        Set<NotificationChannel> methods = EnumSet.noneOf(NotificationChannel.class);
        if (request.isSendPush()) {
            methods.add(NotificationChannel.PUSH);
        }
        if (request.isSendEmail()) {
            methods.add(NotificationChannel.EMAIL);
        }
        if (request.isSendInApp()) {
            methods.add(NotificationChannel.IN_APP);
        }
        return methods;
    }

    private void updateNotificationStatus(Notification notification, Set<NotificationChannel> requestedMethods) {
        boolean anySuccess = notification.isPushSent() || notification.isEmailSent() || notification.isInAppDelivered();

        if (anySuccess) {
            notification.setStatus(NotificationStatus.DELIVERED);
            return;
        }

        boolean anyRequested = requestedMethods != null && !requestedMethods.isEmpty();
        boolean pushDone = !requestedMethods.contains(NotificationChannel.PUSH) || notification.isPushFailed();
        boolean emailDone = !requestedMethods.contains(NotificationChannel.EMAIL) || notification.isEmailFailed();
        boolean inAppDone = !requestedMethods.contains(NotificationChannel.IN_APP) || notification.isInAppFailed();

        if (anyRequested && pushDone && emailDone && inAppDone) {
            notification.setStatus(NotificationStatus.FAILED);
        } else {
            notification.setStatus(NotificationStatus.PENDING);
        }
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

    /**
     * List failed notifications for a user (any channel failed)
     */
    public List<com.aykhedma.dto.response.NotificationDTO> listFailedNotifications(Long userId) {
        List<Notification> failed = notificationRepository.findFailedNotifications(userId);
        return failed.stream().map(com.aykhedma.dto.response.NotificationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    public List<NotificationDTO> getNotificationsByType(Long userId, NotificationType type) {
        List<Notification> notifications = notificationRepository.findByUserIdAndTypeOrderBySentAtDesc(userId, type);
        return notifications.stream().map(NotificationDTO::fromEntity).collect(Collectors.toList());
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
        messagingTemplate.convertAndSend(
                "/topic/notifications-count-" + userId,
                Map.of("count", unreadCount));
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsRead(userId, LocalDateTime.now());
        log.info("Marked {} notifications as read for user: {}", updated, userId);

        // Send updated count via WebSocket
        messagingTemplate.convertAndSend(
                "/topic/notifications-count-" + userId,
                Map.of("count", 0));
    }

    @Transactional
    public int deleteAllNotifications(Long userId) {
        int deleted = notificationRepository.deleteByUserId(userId);
        log.info("Deleted {} notifications for user: {}", deleted, userId);

        // Send updated count via WebSocket
        messagingTemplate.convertAndSend(
                "/topic/notifications-count-" + userId,
                Map.of("count", 0));

        return deleted;
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

    public NotificationPreferenceDTO getUserPreferences(Long userId) {
        NotificationPreference preference = notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));
        return NotificationPreferenceDTO.fromEntity(preference);
    }

    @Transactional
    public NotificationPreferenceDTO updateUserPreferences(Long userId, NotificationPreference updatedPreference) {
        NotificationPreference preference = notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));

        preference.setInAppEnabled(updatedPreference.isInAppEnabled());
        preference.setEmailEnabled(updatedPreference.isEmailEnabled());
        preference.setPushEnabled(updatedPreference.isPushEnabled());
        preference.setUpdatedAt(LocalDateTime.now());

        NotificationPreference saved = notificationPreferenceRepository.save(preference);
        return NotificationPreferenceDTO.fromEntity(saved);
    }

    private NotificationPreference createDefaultPreferences(Long userId) {
        NotificationPreference preference = NotificationPreference.builder()
                .userId(userId)
                .inAppEnabled(true)
                .emailEnabled(true)
                .pushEnabled(true)
                .updatedAt(LocalDateTime.now())
                .build();
        return notificationPreferenceRepository.save(preference);
    }

    private Set<NotificationChannel> applyUserPreferences(Long userId, Set<NotificationChannel> requestedMethods) {
        NotificationPreference preferences = notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));

        Set<NotificationChannel> filteredMethods = EnumSet.noneOf(NotificationChannel.class);

        if (requestedMethods.contains(NotificationChannel.IN_APP) && preferences.isInAppEnabled()) {
            filteredMethods.add(NotificationChannel.IN_APP);
        }
        if (requestedMethods.contains(NotificationChannel.EMAIL) && preferences.isEmailEnabled()) {
            filteredMethods.add(NotificationChannel.EMAIL);
        }
        if (requestedMethods.contains(NotificationChannel.PUSH) && preferences.isPushEnabled()) {
            filteredMethods.add(NotificationChannel.PUSH);
        }

        return filteredMethods;
    }

}