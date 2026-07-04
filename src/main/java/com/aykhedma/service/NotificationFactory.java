package com.aykhedma.service;

import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;
import com.aykhedma.model.notification.NotificationChannel;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationFactory {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public void send(Long userId, NotificationType type, Map<String, Object> params) {
        Map<String, Object> immutableCopy = new HashMap<>(params);

        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendNow(userId, type, immutableCopy);
                }
            });
            return;
        }

        sendNow(userId, type, immutableCopy);
    }

    private void sendNow(Long userId, NotificationType type, Map<String, Object> params) {
        if (!params.containsKey("userName")) {
            String userName = userRepository.findById(userId)
                    .map(user -> user.getName())
                    .orElse("User");
            params = new HashMap<>(params);
            params.put("userName", userName);
        }

        NotificationRequest request = createRequest(userId, type, params);

        if (request.getMethods() == null || request.getMethods().isEmpty()) {
            Set<NotificationChannel> methods = EnumSet.noneOf(NotificationChannel.class);
            if (request.isSendPush())
                methods.add(NotificationChannel.PUSH);
            if (request.isSendEmail())
                methods.add(NotificationChannel.EMAIL);
            if (request.isSendInApp())
                methods.add(NotificationChannel.IN_APP);
            if (request.isSendSms())
                methods.add(NotificationChannel.SMS);
            if (!methods.isEmpty()) {
                request.setMethods(methods);
            }
        }
        notificationService.sendNotification(request);
    }

    private NotificationRequest createRequest(Long userId, NotificationType type, Map<String, Object> params) {
        return switch (type) {
            case EMERGENCY_OFFER -> createEmergencyOfferNotification(userId, params);
            case PROVIDER_SELECTED -> createProviderSelectedNotification(userId, params);
            case BOOKING_REQUEST -> createBookingRequestNotification(userId, params);
            case STATUS_UPDATE -> createStatusUpdateNotification(userId, params);
            case SYSTEM_ALERT -> createSystemAlertNotification(userId, params);
            case BOOKING_CONFIRMATION -> createBookingConfirmationNotification(userId, params);
            case BOOKING_REMINDER -> createBookingReminderNotification(userId, params);
            case BOOKING_CANCELLED -> createBookingCancelledNotification(userId, params);
            case BOOKING_COMPLETED -> createBookingCompletedNotification(userId, params);
            case REVIEW_RECEIVED -> createReviewReceivedNotification(userId, params);
            case RATING_RECEIVED -> createRatingReceivedNotification(userId, params);
            case NEW_MESSAGE -> createNewMessageNotification(userId, params);
            case PROVIDER_ACCEPTED -> createProviderAcceptedNotification(userId, params);
            case PROVIDER_REJECTED -> createProviderRejectedNotification(userId, params);
            case ACCOUNT_UPDATE -> createAccountUpdateNotification(userId, params);
            case PASSWORD_CHANGED -> createPasswordChangedNotification(userId, params);
            case LOCATION_UPDATE -> createLocationUpdateNotification(userId, params);
            case EMERGENCY_ALERT -> createEmergencyAlertNotification(userId, params);
            case RATING_REMINDER -> createRatingReminderNotification(userId, params);
            case GENERAL -> createGeneralNotification(userId, params);
        };
    }

    // general
    private NotificationRequest createGeneralNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.GENERAL)
                .title((String) params.getOrDefault("title", "Notification"))
                .content((String) params.getOrDefault("content", "You have a new notification"))
                .imageUrl((String) params.get("imageUrl"))
                .data(extractDataMap(params))
                .sendEmail(false)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }
    // emergency

    private NotificationRequest createEmergencyOfferNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.EMERGENCY_OFFER)
                .title((String) params.getOrDefault("title", "Emergency Offer Available"))
                .content((String) params.getOrDefault("content", "A new emergency offer is available near you"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.EMERGENCY_OFFER, params))
                .data(extractDataMap(params))
                .sendEmail(false)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createEmergencyAlertNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.EMERGENCY_ALERT)
                .title((String) params.getOrDefault("title", "Emergency Alert"))
                .content((String) params.getOrDefault("content", "Important emergency notification"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.EMERGENCY_ALERT, params))
                .data(extractDataMap(params))
                .sendEmail(false)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    // booking

    private NotificationRequest createBookingRequestNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.BOOKING_REQUEST)
                .title((String) params.getOrDefault("title", "New Booking Request"))
                .content((String) params.getOrDefault("content", "You have a new booking request"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.BOOKING_REQUEST, params))
                .data(extractDataMap(params))
                .sendEmail(true)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createBookingConfirmationNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.BOOKING_CONFIRMATION)
                .title((String) params.getOrDefault("title", "Booking Confirmed"))
                .content((String) params.getOrDefault("content", "Your booking has been confirmed"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.BOOKING_CONFIRMATION, params))
                .data(extractDataMap(params))
                .sendEmail(true)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createBookingReminderNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.BOOKING_REMINDER)
                .title((String) params.getOrDefault("title", "Booking Reminder"))
                .content((String) params.getOrDefault("content", "Your booking is coming up soon"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.BOOKING_REMINDER, params))
                .data(extractDataMap(params))
                .sendEmail(true)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createBookingCancelledNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.BOOKING_CANCELLED)
                .title((String) params.getOrDefault("title", "Booking Cancelled"))
                .content((String) params.getOrDefault("content", "Your booking has been cancelled"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.BOOKING_CANCELLED, params))
                .data(extractDataMap(params))
                .sendEmail(true)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createBookingCompletedNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.BOOKING_COMPLETED)
                .title((String) params.getOrDefault("title", "Booking Completed"))
                .content((String) params.getOrDefault("content", "Your booking has been completed"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.BOOKING_COMPLETED, params))
                .data(extractDataMap(params))
                .sendEmail(true)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    // provider

    private NotificationRequest createProviderSelectedNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.PROVIDER_SELECTED)
                .title((String) params.getOrDefault("title", "You've Been Selected"))
                .content((String) params.getOrDefault("content", "A customer has selected you for their booking"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.PROVIDER_SELECTED, params))
                .data(extractDataMap(params))
                .sendEmail(false)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createProviderAcceptedNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.PROVIDER_ACCEPTED)
                .title((String) params.getOrDefault("title", "Booking Accepted"))
                .content((String) params.getOrDefault("content", "The provider has accepted your booking"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.PROVIDER_ACCEPTED, params))
                .data(extractDataMap(params))
                .sendEmail(false)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createProviderRejectedNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.PROVIDER_REJECTED)
                .title((String) params.getOrDefault("title", "Booking Declined"))
                .content((String) params.getOrDefault("content", "The provider has declined your booking"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.PROVIDER_REJECTED, params))
                .data(extractDataMap(params))
                .sendEmail(false)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    // review and feedback

    private NotificationRequest createReviewReceivedNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.REVIEW_RECEIVED)
                .title((String) params.getOrDefault("title", "New Review Received"))
                .content((String) params.getOrDefault("content", "A user has left a review for you"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.REVIEW_RECEIVED, params))
                .data(extractDataMap(params))
                .sendEmail(false)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createRatingReceivedNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.RATING_RECEIVED)
                .title((String) params.getOrDefault("title", "New Rating Received"))
                .content((String) params.getOrDefault("content", "You have received a new rating"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.RATING_RECEIVED, params))
                .data(extractDataMap(params))
                .sendEmail(false)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createRatingReminderNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.RATING_REMINDER)
                .title((String) params.getOrDefault("title", "Rate Your Experience"))
                .content((String) params.getOrDefault("content", "Please rate your recent service"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(NotificationType.RATING_REMINDER, params))
                .data(extractDataMap(params))
                .sendEmail(false)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    // messages

    private NotificationRequest createNewMessageNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.NEW_MESSAGE)
                .title((String) params.getOrDefault("title", "New Message"))
                .content((String) params.getOrDefault("content", "You have a new message"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(userId, NotificationType.NEW_MESSAGE, params))
                .data(extractDataMap(params))
                .sendEmail(false)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    // account and security

    private NotificationRequest createAccountUpdateNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.ACCOUNT_UPDATE)
                .title((String) params.getOrDefault("title", "Account Update"))
                .content((String) params.getOrDefault("content", "Your account has been updated"))
                .imageUrl((String) params.get("imageUrl"))
                .data(extractDataMap(params))
                .sendEmail(true)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createPasswordChangedNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.PASSWORD_CHANGED)
                .title((String) params.getOrDefault("title", "Password Changed"))
                .content((String) params.getOrDefault("content", "Your password has been changed successfully"))
                .imageUrl((String) params.get("imageUrl"))
                .data(extractDataMap(params))
                .sendEmail(true)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createLocationUpdateNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.LOCATION_UPDATE)
                .title((String) params.getOrDefault("title", "Location Updated"))
                .content((String) params.getOrDefault("content", "Your location has been updated"))
                .imageUrl((String) params.get("imageUrl"))
                .deepLink(generateDeepLink(userId, NotificationType.LOCATION_UPDATE, params))
                .data(extractDataMap(params))
                .sendEmail(true)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    // system

    private NotificationRequest createStatusUpdateNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.STATUS_UPDATE)
                .title((String) params.getOrDefault("title", "Status Update"))
                .content((String) params.getOrDefault("content", "A status has been updated"))
                .imageUrl((String) params.get("imageUrl"))
                .data(extractDataMap(params))
                .sendEmail(true)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private NotificationRequest createSystemAlertNotification(Long userId, Map<String, Object> params) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.SYSTEM_ALERT)
                .title((String) params.getOrDefault("title", "System Alert"))
                .content((String) params.getOrDefault("content", "An important system alert"))
                .imageUrl((String) params.get("imageUrl"))
                .data(extractDataMap(params))
                .sendEmail(true)
                .sendInApp(true)
                .sendPush(true)
                .build();
    }

    private String generateDeepLink(NotificationType type, Map<String, Object> params) {
        return generateDeepLink(null, type, params);
    }

    private String generateDeepLink(Long userId, NotificationType type, Map<String, Object> params) {
        return switch (type) {
            // Booking-related
            case BOOKING_REQUEST, BOOKING_CONFIRMATION, BOOKING_REMINDER,
                    BOOKING_CANCELLED, BOOKING_COMPLETED, PROVIDER_SELECTED,
                    PROVIDER_ACCEPTED, PROVIDER_REJECTED -> {
                Long bookingId = extractLongParam(params, "bookingId");
                yield bookingId != null ? "ay-khedma://booking/" + bookingId : null;
            }
            // Chat/Message-related
            case NEW_MESSAGE -> {
                Object chatRoom = params.get("chatRoomId");
                if (chatRoom == null)
                    chatRoom = params.get("roomId");
                if (chatRoom == null) {
                    yield null;
                }
                yield "ay-khedma://chat/" + String.valueOf(chatRoom);
            }
            // Provider-related
            case REVIEW_RECEIVED, RATING_RECEIVED, RATING_REMINDER -> {
                Long reviewId = extractLongParam(params, "reviewId");
                Long providerId = extractLongParam(params, "providerId");
                yield reviewId != null ? "ay-khedma://review/" + reviewId
                        : providerId != null ? "ay-khedma://provider/" + providerId : null;
            }
            case LOCATION_UPDATE -> userId != null ? "ay-khedma://provider/" + userId : null;
            // Emergency-related
            case EMERGENCY_OFFER, EMERGENCY_ALERT -> {
                Long emergencyId = extractLongParam(params, "emergencyId");
                yield emergencyId != null ? "ay-khedma://emergency/" + emergencyId : null;
            }
            // Account/Security -> no deep links
            case ACCOUNT_UPDATE, PASSWORD_CHANGED, SYSTEM_ALERT, STATUS_UPDATE, GENERAL -> null;
        };
    }

    private Long extractLongParam(Map<String, Object> params, String key) {
        try {
            Object value = params.get(key);
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof Integer) {
                return ((Integer) value).longValue();
            } else if (value instanceof String) {
                return Long.parseLong((String) value);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse {} as Long: {}", key, params.get(key));
        }
        return null;
    }

    /**
     * Extract custom data from params map, excluding common fields
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDataMap(Map<String, Object> params) {
        Map<String, Object> data = (Map<String, Object>) params.get("data");
        if (data == null) {
            data = new java.util.HashMap<>(params);
            data.remove("title");
            data.remove("content");
            data.remove("imageUrl");
            data.remove("data");
        }
        return data.isEmpty() ? null : data;
    }
}
