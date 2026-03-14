package com.aykhedma.controller;

import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.service.NotificationService;
import com.aykhedma.service.notification.EmailService;
import com.aykhedma.service.notification.FirebaseService;
//import com.aykhedma.service.notification.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/test/notifications")
@RequiredArgsConstructor
@Slf4j
public class TestNotificationController {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final FirebaseService firebaseService;
//    private final SmsService smsService;

    /**
     * Test email notification
     * POST /api/test/notifications/send-email
     */
    @PostMapping("/send-email")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestBody TestEmailRequest request) {
        log.info("📧 Sending test email to: {}", request.getEmail());

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("subject", request.getSubject());
        variables.put("message", request.getMessage());
        variables.put("timestamp", timestamp);

        emailService.sendHtmlEmail(
                request.getEmail(),
                request.getSubject() != null ? request.getSubject() : "Ay Khedma Test Notification",
                "email/test",
                variables
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Email sent successfully");
        response.put("email", request.getEmail());

        return ResponseEntity.ok(response);
    }

    /**
     * Test push notification (Firebase)
     * POST /api/test/notifications/send-push
     */
    @PostMapping("/send-push")
    public ResponseEntity<Map<String, Object>> sendTestPush(
            @RequestHeader(value = "X-User-Id", required = true) Long userId,
            @RequestBody TestPushRequest request) {

        log.info("🔔 Sending test push notification to user: {}", userId);

        Map<String, String> data = new HashMap<>();
        data.put("type", request.getType() != null ? request.getType() : "test");
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));
        if (request.getData() != null) {
            data.putAll(request.getData());
        }

        firebaseService.sendPushNotification(
                userId,
                request.getTitle() != null ? request.getTitle() : "Test Notification",
                request.getBody() != null ? request.getBody() : "This is a test push notification",
                data
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Push notification sent");
        response.put("userId", userId);
        response.put("firebaseEnabled", firebaseService.isFirebaseEnabled());

        return ResponseEntity.ok(response);
    }

    /**
     * Test SMS notification (requires Twilio)
     * POST /api/test/notifications/send-sms
     */
    @PostMapping("/send-sms")
    public ResponseEntity<Map<String, Object>> sendTestSms(@RequestBody TestSmsRequest request) {
        log.info("📱 Sending test SMS to: {}", request.getPhoneNumber());

//        smsService.sendSms(
//                request.getPhoneNumber(),
//                request.getMessage() != null ? request.getMessage() : "This is a test SMS from Ay Khedma"
//        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "SMS sent");
        response.put("phoneNumber", request.getPhoneNumber());

        return ResponseEntity.ok(response);
    }

    /**
     * Test in-app notification (WebSocket)
     * POST /api/test/notifications/send-inapp
     */
    @PostMapping("/send-inapp")
    public ResponseEntity<Map<String, Object>> sendTestInApp(
            @RequestHeader(value = "X-User-Id", required = true) Long userId,
            @RequestBody TestInAppRequest request) {

        log.info("📱 Sending test in-app notification to user: {}", userId);

        NotificationRequest notificationRequest = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.SYSTEM_ALERT)
                .title(request.getTitle() != null ? request.getTitle() : "Real-time Notification")
                .content(request.getMessage() != null ? request.getMessage() : "This is a test WebSocket message")
                .sendInApp(true)
                .sendPush(false)
                .sendEmail(false)
                .sendSms(false)
                .data(Map.of(
                        "source", "test-controller",
                        "timestamp", String.valueOf(System.currentTimeMillis())
                ))
                .build();

        notificationService.sendNotification(notificationRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "In-app notification sent via WebSocket");
        response.put("userId", userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Test all channels at once
     * POST /api/test/notifications/send-all
     */
    @PostMapping("/send-all")
    public ResponseEntity<Map<String, Object>> sendToAllChannels(
            @RequestParam Long userId,
            @RequestBody TestAllChannelsRequest request) {

        log.info("🚀 Sending test notification to ALL channels for user: {}", userId);

        NotificationRequest notificationRequest = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.SYSTEM_ALERT)
                .title(request.getTitle() != null ? request.getTitle() : "Multi-Channel Test")
                .content(request.getMessage() != null ? request.getMessage() : "This notification was sent to all channels")
                .sendInApp(true)
                .sendPush(true)
                .sendEmail(request.getEmail() != null)
                .email(request.getEmail())
                .sendSms(request.getPhoneNumber() != null)
                .phoneNumber(request.getPhoneNumber())
                .data(Map.of(
                        "testId", UUID.randomUUID().toString(),
                        "channels", "all"
                ))
                .build();

        notificationService.sendNotification(notificationRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Notification sent to all channels");
        response.put("userId", userId);
        response.put("channels", "In-App, Push, Email, SMS");

        return ResponseEntity.ok(response);
    }

    /**
     * Test booking confirmation scenario
     * POST /api/test/notifications/test-booking
     */
    @PostMapping("/test-booking")
    public ResponseEntity<Map<String, Object>> testBookingNotification(@RequestBody TestBookingRequest request) {

        NotificationRequest notificationRequest = NotificationRequest.builder()
                .userId(request.getUserId())
                .type(NotificationType.BOOKING_CONFIRMATION)
                .title("✅ Booking Confirmed!")
                .content(String.format("Your booking with %s for %s has been confirmed. Amount: %.2f EGP",
                        request.getProviderName(), request.getServiceType(), request.getAmount()))
                .sendInApp(true)
                .sendPush(true)
                .sendEmail(true)
                .email(request.getEmail())
                .data(Map.of(
                        "bookingId", String.valueOf(request.getBookingId()),
                        "providerName", request.getProviderName(),
                        "serviceType", request.getServiceType(),
                        "scheduledTime", request.getScheduledTime(),
                        "amount", String.valueOf(request.getAmount()),
                        "action", "view_booking"
                ))
                .build();

        notificationService.sendNotification(notificationRequest);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Booking notification sent",
                "bookingId", request.getBookingId()
        ));
    }


    /**
     * Test emergency alert scenario
     * POST /api/test/notifications/test-emergency
     */
    @PostMapping("/test-emergency")
    public ResponseEntity<Map<String, Object>> testEmergencyNotification(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody TestEmergencyRequest request) {

        NotificationRequest notificationRequest = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.EMERGENCY_ALERT)
                .title("🚨 EMERGENCY ALERT")
                .content(String.format("Emergency services have been notified. Type: %s, Location: %s",
                        request.getEmergencyType(), request.getLocation()))
                .sendInApp(true)
                .sendPush(true)
                .sendEmail(true)
                .sendSms(true) // Always send SMS for emergencies
                .data(Map.of(
                        "emergencyType", request.getEmergencyType(),
                        "location", request.getLocation(),
                        "responders", String.valueOf(request.getResponders()),
                        "eta", request.getEta(),
                        "priority", "critical"
                ))
                .build();

        notificationService.sendNotification(notificationRequest);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Emergency notification sent"
        ));
    }

    /**
     * Test new message notification
     * POST /api/test/notifications/new-message
     */
    @PostMapping("/new-message")
    public ResponseEntity<Map<String, Object>> testNewMessage(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody TestMessageRequest request) {

        NotificationRequest notificationRequest = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.NEW_MESSAGE)
                .title("💬 New Message")
                .content(String.format("%s: %s", request.getSenderName(), request.getMessagePreview()))
                .sendInApp(true)
                .sendPush(true)
                .sendEmail(false)
                .sendSms(false)
                .data(Map.of(
                        "chatId", String.valueOf(request.getChatId()),
                        "senderName", request.getSenderName(),
                        "unreadCount", String.valueOf(request.getUnreadCount())
                ))
                .build();

        notificationService.sendNotification(notificationRequest);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Message notification sent"
        ));
    }

    /**
     * Get system status
     * GET /api/test/notifications/status/{userId}
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<Map<String, Object>> getSystemStatus(@PathVariable Long userId) {

        long unreadCount = notificationService.getUnreadCount(userId);

        Map<String, Object> status = new HashMap<>();
        status.put("userId", userId);
        status.put("unreadCount", unreadCount);
        status.put("websocketConnected", true); // You'd implement actual check
        status.put("pushEnabled", firebaseService.isFirebaseEnabled());
        status.put("emailEnabled", true);
//        status.put("smsEnabled", smsService.isTwilioEnabled());
        status.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(status);
    }
}

// ===================== REQUEST DTOs =====================

class TestEmailRequest {
    private String email;
    private String subject;
    private String message;
    // Getters and setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

class TestPushRequest {
    private String title;
    private String body;
    private String type;
    private Map<String, String> data;
    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, String> getData() { return data; }
    public void setData(Map<String, String> data) { this.data = data; }
}

class TestSmsRequest {
    private String phoneNumber;
    private String message;
    // Getters and setters
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

class TestInAppRequest {
    private String title;
    private String message;
    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

class TestAllChannelsRequest {
    private String title;
    private String message;
    private String email;
    private String phoneNumber;
    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}

class TestBookingRequest {
    private Long userId;
    private Long bookingId;
    private String providerName;
    private String serviceType;
    private String scheduledTime;
    private Double amount;
    private String email;
    // Getters and setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public String getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}

class TestPaymentRequest {
    private Double amount;
    private String currency;
    private String paymentMethod;
    private String transactionId;
    // Getters and setters
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
}

class TestEmergencyRequest {
    private String emergencyType;
    private String location;
    private Integer responders;
    private String eta;
    // Getters and setters
    public String getEmergencyType() { return emergencyType; }
    public void setEmergencyType(String emergencyType) { this.emergencyType = emergencyType; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getResponders() { return responders; }
    public void setResponders(Integer responders) { this.responders = responders; }
    public String getEta() { return eta; }
    public void setEta(String eta) { this.eta = eta; }
}

class TestMessageRequest {
    private String senderName;
    private String messagePreview;
    private Long chatId;
    private Integer unreadCount;
    // Getters and setters
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getMessagePreview() { return messagePreview; }
    public void setMessagePreview(String messagePreview) { this.messagePreview = messagePreview; }
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public Integer getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }
}