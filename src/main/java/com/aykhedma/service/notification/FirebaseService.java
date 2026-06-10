package com.aykhedma.service.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.aykhedma.model.notification.DeviceToken;
import com.aykhedma.model.notification.NotificationStatus;
import com.aykhedma.repository.DeviceTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FirebaseService {

    @Value("${firebase.config.file:classpath:firebase-service-account.json}")
    private String firebaseConfigPath;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    private FirebaseMessaging firebaseMessaging;

    @PostConstruct
    public void initialize() {
        try {
            ClassPathResource resource = new ClassPathResource("firebase-service-account.json");

            if (!resource.exists()) {
                log.warn("Firebase config file not found. Push notifications disabled.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(resource.getInputStream()))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully");
            }

            this.firebaseMessaging = FirebaseMessaging.getInstance();

        } catch (IOException e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
        }
    }

    @Transactional
    public NotificationStatus sendPushNotification(Long userId, String title,
            String body, Map<String, Object> data) {
        if (firebaseMessaging == null) {
            log.warn("Firebase not configured. Push notification not sent.");
            return NotificationStatus.FAILED;
        }

        try {
            // Get all active devices for user
            List<DeviceToken> deviceTokens = deviceTokenRepository.findByUserIdAndActiveTrue(userId);

            if (deviceTokens.isEmpty()) {
                log.warn("No active devices for user: {}", userId);
                return NotificationStatus.FAILED;
            }

            // Build notification
            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            // Send individually
            int successCount = 0;
            int failureCount = 0;

            for (int i = 0; i < deviceTokens.size(); i++) {
                DeviceToken deviceToken = deviceTokens.get(i);
                Message.Builder builder = Message.builder()
                        .setToken(deviceToken.getFcmToken())
                        .setNotification(notification)
                        .setAndroidConfig(getAndroidConfig())
                        .setApnsConfig(getIosConfig());

                if (data != null) {
                    data.forEach((key, value) -> builder.putData(key, String.valueOf(value)));
                }

                try {
                    firebaseMessaging.send(builder.build());
                    successCount++;
                } catch (FirebaseMessagingException ex) {
                    failureCount++;
                    log.warn("Failed to send to device {}: {}", deviceToken.getDeviceId(), ex.getMessage());
                    if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                        deviceToken.setActive(false);
                        deviceTokenRepository.save(deviceToken);
                        log.info("Deactivated invalid token for device: {}", deviceToken.getDeviceId());
                    }
                }
            }

            log.info("Push notifications sent: {} success, {} failures", successCount, failureCount);

            if (successCount > 0) {
                return NotificationStatus.DELIVERED;
            } else {
                return NotificationStatus.FAILED;
            }

        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage());
            return NotificationStatus.FAILED;
        }
    }

    private AndroidConfig getAndroidConfig() {
        return AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setChannelId("high_importance_channel")
                        .setPriority(AndroidNotification.Priority.HIGH)
                        .build())
                .build();
    }

    private ApnsConfig getIosConfig() {
        return ApnsConfig.builder()
                .setAps(Aps.builder()
                        .setSound("default")
                        .setContentAvailable(true)
                        .build())
                .build();
    }


    public boolean isFirebaseEnabled() {
        return firebaseMessaging != null;
    }
}
